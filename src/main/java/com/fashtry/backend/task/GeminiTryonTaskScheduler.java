package com.fashtry.backend.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashtry.backend.bean.db.TryonTask;
import com.fashtry.backend.bean.dto.gemini.GeminiTryOnTaskPayload;
import com.fashtry.backend.bean.vo.GeminiImageResultVO;
import com.fashtry.backend.common.config.GeminiApiProperties;
import com.fashtry.backend.common.constants.ClientSafeMessages;
import com.fashtry.backend.common.constants.TryonTaskProvider;
import com.fashtry.backend.mapper.TryonTaskMapper;
import com.fashtry.backend.service.ai.GeminiImageService;
import com.fashtry.backend.service.business.TryonService;
import com.fashtry.backend.service.storage.CosService;
import com.fashtry.backend.utils.ImageAspectRatioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 消费 {@code tryon_tasks} 中 provider=GEMINI、status=0 的试穿任务，调用 Gemini、上传 COS、回写结果与完成时间。
 */
@Component
public class GeminiTryonTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(GeminiTryonTaskScheduler.class);

    private static final int STATUS_QUEUED = 0;
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;

    /** 每轮从 DB 拉取的排队任务数（与线程池并行度配合；配额紧时在 yml 侧调小 http 超时或本常量） */
    private static final int BATCH = 50;
    private static final int ERROR_MAX_LEN = 4000;

    private final TryonTaskMapper tryonTaskMapper;
    private final GeminiImageService geminiImageService;
    private final CosService cosService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final TryonService tryonService;
    private final Executor aiTaskExecutor;
    private final GeminiApiProperties geminiApiProperties;

    public GeminiTryonTaskScheduler(
            TryonTaskMapper tryonTaskMapper,
            GeminiImageService geminiImageService,
            CosService cosService,
            ObjectMapper objectMapper,
            RestTemplate restTemplate,
            TryonService tryonService,
            @Qualifier("aiTaskExecutor") Executor aiTaskExecutor,
            GeminiApiProperties geminiApiProperties) {
        this.tryonTaskMapper = tryonTaskMapper;
        this.geminiImageService = geminiImageService;
        this.cosService = cosService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.tryonService = tryonService;
        this.aiTaskExecutor = aiTaskExecutor;
        this.geminiApiProperties = geminiApiProperties;
    }

    @Scheduled(fixedDelay = 2000)
    public void run() {
        if (!geminiImageService.isReady()) {
            return;
        }
        LambdaQueryWrapper<TryonTask> q =
                new LambdaQueryWrapper<TryonTask>()
                        .eq(TryonTask::getProvider, TryonTaskProvider.GEMINI)
                        .eq(TryonTask::getStatus, STATUS_QUEUED)
                        .orderByAsc(TryonTask::getCreatedAt)
                        .last("LIMIT " + BATCH);
        List<TryonTask> list = tryonTaskMapper.selectList(q);
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        long perTaskTimeoutMs = geminiApiProperties.effectiveTryonSchedulerTaskTimeoutMs();
        log.info(
                "Try-on scheduler batch={}, taskIds={}, timeoutMs={}",
                list.size(),
                list.stream().map(TryonTask::getId).toList(),
                perTaskTimeoutMs);
        // 不在此 join 整批：若 join，fixedDelay 要等本批全部跑完才进入下一轮，后续任务会长期停在 status=0「排队中」。
        // claim 已把任务标为生成中，交给线程池并行即可；下一轮 tick 继续拉新的排队任务。
        for (TryonTask task : list) {
            if (!claim(task.getId())) {
                log.warn("Try-on claim skipped taskId={}", task.getId());
                continue;
            }
            TryonTask t = task;
            CompletableFuture.runAsync(
                            () -> {
                                try {
                                    processOne(t);
                                } catch (Exception e) {
                                    log.warn(
                                            "Try-on task failed taskId={}: {}",
                                            t.getId(),
                                            e.getMessage() != null ? e.getMessage() : e.toString(),
                                            e);
                                    fail(t.getId(), e);
                                }
                            },
                            aiTaskExecutor)
                    .orTimeout(perTaskTimeoutMs, TimeUnit.MILLISECONDS)
                    .handle(
                            (r, ex) -> {
                                if (ex != null) {
                                    Throwable c = unwrapThrowable(ex);
                                    if (c instanceof TimeoutException) {
                                        log.warn(
                                                "Try-on scheduler wall-clock timeout taskId={} perTaskTimeoutMs={} "
                                                        + "(含下载素材+Gemini HTTP+解码+上传 COS；或线程池排队导致起算早)",
                                                t.getId(),
                                                perTaskTimeoutMs);
                                        fail(t.getId(), (Throwable) null);
                                    }
                                }
                                return null;
                            });
        }
    }

    private static Throwable unwrapThrowable(Throwable ex) {
        if (ex instanceof CompletionException && ex.getCause() != null) {
            return unwrapThrowable(ex.getCause());
        }
        return ex;
    }

    private boolean claim(Long id) {
        LocalDateTime started = LocalDateTime.now();
        LambdaUpdateWrapper<TryonTask> uw =
                new LambdaUpdateWrapper<TryonTask>()
                        .eq(TryonTask::getId, id)
                        .eq(TryonTask::getStatus, STATUS_QUEUED)
                        .eq(TryonTask::getProvider, TryonTaskProvider.GEMINI)
                        .set(TryonTask::getStatus, STATUS_PROCESSING)
                        .set(TryonTask::getProcessingStartedAt, started);
        return tryonTaskMapper.update(null, uw) == 1;
    }

    private void processOne(TryonTask task) throws Exception {
        final long wall0 = System.currentTimeMillis();
        TryonTask fresh = tryonTaskMapper.selectById(task.getId());
        Long queueWaitMs = null;
        if (fresh != null && fresh.getProcessingStartedAt() != null) {
            queueWaitMs = Duration.between(fresh.getProcessingStartedAt(), LocalDateTime.now()).toMillis();
        }
        if (aiTaskExecutor instanceof ThreadPoolTaskExecutor tpe) {
            log.info(
                    "Try-on pipeline start taskId={} userId={} thread={} queueWaitSinceClaimMs={} "
                            + "poolActive={} poolSize={} queueSize={} schedulerTimeoutMs={} httpTimeoutMs={}",
                    task.getId(),
                    fresh != null ? fresh.getUserId() : task.getUserId(),
                    Thread.currentThread().getName(),
                    queueWaitMs,
                    tpe.getActiveCount(),
                    tpe.getPoolSize(),
                    tpe.getQueueSize(),
                    geminiApiProperties.effectiveTryonSchedulerTaskTimeoutMs(),
                    geminiApiProperties.getHttpTimeoutMs());
        } else {
            log.info(
                    "Try-on pipeline start taskId={} userId={} thread={} queueWaitSinceClaimMs={} "
                            + "schedulerTimeoutMs={} httpTimeoutMs={}",
                    task.getId(),
                    fresh != null ? fresh.getUserId() : task.getUserId(),
                    Thread.currentThread().getName(),
                    queueWaitMs,
                    geminiApiProperties.effectiveTryonSchedulerTaskTimeoutMs(),
                    geminiApiProperties.getHttpTimeoutMs());
        }
        GeminiTryOnTaskPayload p = new GeminiTryOnTaskPayload();
        if (StringUtils.hasText(task.getGeminiPayloadJson())) {
            p = objectMapper.readValue(task.getGeminiPayloadJson(), GeminiTryOnTaskPayload.class);
        }
        p.setModelCosUrl(task.getModelUrl());
        p.setGarmentCosUrl(task.getGarmentUrl());
        final String modelUrl = p.getModelCosUrl();
        final String garmentUrl = p.getGarmentCosUrl();
        String imageSizePreview =
                StringUtils.hasText(p.getImageSize()) ? p.getImageSize().trim() : "1K";
        log.info(
                "Try-on inputs taskId={} modelUrl={} garmentUrl={} imageSize={} modelIdParam={} aspectRatio={}",
                task.getId(),
                safeUrlForLog(modelUrl),
                safeUrlForLog(garmentUrl),
                imageSizePreview,
                StringUtils.hasText(p.getModelId()) ? p.getModelId() : "(default)",
                StringUtils.hasText(p.getAspectRatio()) ? p.getAspectRatio() : "-");
        // 与 Gemini 调用相比下载占比不小；两张 COS 图并行拉取，避免串行加倍等待
        CompletableFuture<byte[]> modelFut =
                CompletableFuture.supplyAsync(
                        () -> downloadForTryon(task.getId(), "model", modelUrl), ForkJoinPool.commonPool());
        CompletableFuture<byte[]> garmentFut =
                CompletableFuture.supplyAsync(
                        () -> downloadForTryon(task.getId(), "garment", garmentUrl), ForkJoinPool.commonPool());
        byte[] modelBytes;
        byte[] garmentBytes;
        try {
            CompletableFuture.allOf(modelFut, garmentFut).join();
            modelBytes = modelFut.join();
            garmentBytes = garmentFut.join();
        } catch (CompletionException e) {
            Throwable c = unwrapThrowable(e);
            if (c instanceof RuntimeException re) {
                throw re;
            }
            if (c instanceof Error err) {
                throw err;
            }
            throw new Exception(c);
        }
        long downloadMs = System.currentTimeMillis() - wall0;
        String modelMime = guessImageMime(modelBytes);
        String garmentMime = guessImageMime(garmentBytes);
        String imageSize = StringUtils.hasText(p.getImageSize()) ? p.getImageSize().trim() : "1K";
        log.info(
                "Try-on downloads done taskId={} parallelDownloadMs={} modelBytes={} garmentBytes={}",
                task.getId(),
                downloadMs,
                modelBytes.length,
                garmentBytes.length);
        String aspectRatio =
                StringUtils.hasText(p.getAspectRatio()) ? p.getAspectRatio().trim() : null;
        if (!StringUtils.hasText(aspectRatio)) {
            aspectRatio = ImageAspectRatioUtils.detectClosestGeminiAspectRatio(modelBytes);
            if (StringUtils.hasText(aspectRatio)) {
                log.info(
                        "Try-on aspectRatio inferred from model image taskId={} aspectRatio={}",
                        task.getId(),
                        aspectRatio);
            }
        }
        String garmentCategory = StringUtils.hasText(task.getCategory()) ? task.getCategory().trim() : "one-pieces";
        GeminiImageResultVO vo =
                geminiImageService.tryOnVirtualDressing(
                        modelBytes,
                        modelMime,
                        garmentBytes,
                        garmentMime,
                        p.getInstruction(),
                        p.getModelId(),
                        aspectRatio,
                        imageSize,
                        garmentCategory,
                        task.getId());
        long afterGeminiMs = System.currentTimeMillis() - wall0;
        log.info(
                "Try-on after Gemini taskId={} wallSinceStartMs={} downloadMs={} geminiOnlyWallMs={}",
                task.getId(),
                afterGeminiMs,
                downloadMs,
                afterGeminiMs - downloadMs);
        if (!hasAnyImage(vo)) {
            log.warn("Try-on no image in response taskId={}", task.getId());
            fail(task.getId(), (Throwable) null);
            return;
        }
        String b64 = vo.getImagesBase64().stream().filter(StringUtils::hasText).findFirst().orElse(null);
        if (!StringUtils.hasText(b64)) {
            log.warn("Try-on empty image payload taskId={}", task.getId());
            fail(task.getId(), (Throwable) null);
            return;
        }
        int b64chars = b64.length();
        byte[] raw = Base64.getDecoder().decode(b64);
        log.info(
                "Try-on result decode taskId={} base64Chars={} rawBytes={}",
                task.getId(),
                b64chars,
                raw.length);
        long cos0 = System.currentTimeMillis();
        String resultCosUrl = cosService.uploadGeminiGeneratedImage(raw, guessImageMime(raw));
        long cosMainMs = System.currentTimeMillis() - cos0;
        String thumbUrl = null;
        long thumbMs = 0L;
        long tThumb0 = System.currentTimeMillis();
        try {
            thumbUrl = cosService.uploadTryonThumbnailFromUrl(resultCosUrl);
            thumbMs = System.currentTimeMillis() - tThumb0;
        } catch (Exception ex) {
            thumbMs = System.currentTimeMillis() - tThumb0;
            log.warn(
                    "Try-on thumbnail upload failed taskId={} thumbMs={} err={}",
                    task.getId(),
                    thumbMs,
                    ex.getMessage());
        }
        long totalMs = System.currentTimeMillis() - wall0;
        log.info(
                "Try-on persist-ready taskId={} totalMs={} cosMainMs={} thumbMs={} resultUrl={}",
                task.getId(),
                totalMs,
                cosMainMs,
                thumbMs,
                safeUrlForLog(resultCosUrl));
        complete(task.getId(), resultCosUrl, thumbUrl);
    }

    private static boolean hasAnyImage(GeminiImageResultVO vo) {
        if (vo.getImagesBase64() == null) {
            return false;
        }
        for (String b64 : vo.getImagesBase64()) {
            if (StringUtils.hasText(b64)) {
                return true;
            }
        }
        return false;
    }

    private byte[] downloadForTryon(Long taskId, String role, String url) {
        long t0 = System.currentTimeMillis();
        try {
            byte[] bytes = downloadBytes(url);
            log.info(
                    "Try-on asset download ok taskId={} role={} path={} bytes={} ms={}",
                    taskId,
                    role,
                    safeUrlForLog(url),
                    bytes.length,
                    System.currentTimeMillis() - t0);
            return bytes;
        } catch (Exception e) {
            log.warn(
                    "Try-on asset download fail taskId={} role={} path={} ms={} err={}",
                    taskId,
                    role,
                    safeUrlForLog(url),
                    System.currentTimeMillis() - t0,
                    e.getMessage() != null ? e.getMessage() : e.toString());
            throw e;
        }
    }

    private byte[] downloadBytes(String url) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("图片 URL 为空");
        }
        byte[] bytes = restTemplate.getForObject(url, byte[].class);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("下载图片失败: " + safeUrlForLog(url));
        }
        return bytes;
    }

    /** 日志用：仅保留 scheme/host/path 前几字符，避免刷长 query；排查时足以定位 COS 路径与资源 */
    private static String safeUrlForLog(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        try {
            URI u = URI.create(url.trim());
            String host = u.getHost() != null ? u.getHost() : "";
            String path = u.getPath() != null ? u.getPath() : "";
            if (path.length() > 96) {
                path = path.substring(0, 44) + "…" + path.substring(path.length() - 40);
            }
            String scheme = u.getScheme() != null ? u.getScheme() : "http";
            return scheme + "://" + host + path;
        } catch (Exception e) {
            return url.length() > 120 ? url.substring(0, 120) + "…" : url;
        }
    }

    private void complete(Long id, String resultCosUrl, String thumbUrl) {
        LocalDateTime now = LocalDateTime.now();
        TryonTask row = tryonTaskMapper.selectById(id);
        Long durationMs = computeDurationMs(row, now);
        LambdaUpdateWrapper<TryonTask> uw =
                new LambdaUpdateWrapper<TryonTask>()
                        .eq(TryonTask::getId, id)
                        .eq(TryonTask::getStatus, STATUS_PROCESSING)
                        .set(TryonTask::getStatus, STATUS_COMPLETED)
                        .set(TryonTask::getResultCosUrl, resultCosUrl)
                        .set(TryonTask::getThumbUrl, thumbUrl)
                        .set(TryonTask::getResultUrl, null)
                        .set(TryonTask::getErrorMsg, null)
                        .set(TryonTask::getCompletedAt, now)
                        .set(TryonTask::getGenerationDurationMs, durationMs);
        int updated = tryonTaskMapper.update(null, uw);
        if (updated != 1) {
            TryonTask cur = tryonTaskMapper.selectById(id);
            if (cur != null && cur.getStatus() != null && cur.getStatus() == STATUS_COMPLETED) {
                tryonService.updateRedis(id, cur);
                tryonService.deductTryonCreditsOnSuccess(cur);
            } else if (cur != null && cur.getStatus() != null && cur.getStatus() == STATUS_FAILED) {
                log.warn(
                        "Try-on complete lost race (likely scheduler orTimeout before COS/DB finished) taskId={}",
                        id);
            } else {
                log.warn(
                        "Try-on complete update skipped taskId={} currentStatus={}",
                        id,
                        cur != null ? cur.getStatus() : null);
            }
            return;
        }
        TryonTask full = tryonTaskMapper.selectById(id);
        if (full != null) {
            tryonService.updateRedis(id, full);
            tryonService.deductTryonCreditsOnSuccess(full);
        }
        log.info(
                "Try-on completed taskId={}, durationMs={}, resultCosUrl={}",
                id,
                durationMs,
                safeUrlForLog(resultCosUrl));
    }

    private void fail(Long id, Throwable detail) {
        if (detail != null) {
            log.warn(
                    "Try-on marked failed taskId={}: {}",
                    id,
                    detail.getMessage() != null ? detail.getMessage() : detail.toString(),
                    detail);
        } else {
            log.warn(
                    "Try-on marked failed taskId={} reason=timeout_or_empty_response (无异常对象，多为调度超时或无图)",
                    id);
        }
        String msg = ClientSafeMessages.TRYON_GENERATION_FAILED;
        if (msg.length() > ERROR_MAX_LEN) {
            msg = msg.substring(0, ERROR_MAX_LEN);
        }
        LocalDateTime now = LocalDateTime.now();
        TryonTask row = tryonTaskMapper.selectById(id);
        Long durationMs = computeDurationMs(row, now);
        LambdaUpdateWrapper<TryonTask> uw =
                new LambdaUpdateWrapper<TryonTask>()
                        .eq(TryonTask::getId, id)
                        .eq(TryonTask::getStatus, STATUS_PROCESSING)
                        .set(TryonTask::getStatus, STATUS_FAILED)
                        .set(TryonTask::getErrorMsg, msg)
                        .set(TryonTask::getCompletedAt, now)
                        .set(TryonTask::getGenerationDurationMs, durationMs);
        tryonTaskMapper.update(null, uw);
        TryonTask full = tryonTaskMapper.selectById(id);
        if (full != null) {
            tryonService.updateRedis(id, full);
            tryonService.releaseTryonCreditsOnFailure(full);
        }
        log.info("Try-on failure persisted taskId={}, durationMs={}", id, durationMs);
    }

    private static Long computeDurationMs(TryonTask row, LocalDateTime end) {
        if (row == null || row.getProcessingStartedAt() == null) {
            return null;
        }
        return Duration.between(row.getProcessingStartedAt(), end).toMillis();
    }

    private static String guessImageMime(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return "image/png";
        }
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
            return "image/jpeg";
        }
        if (bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F') {
            return "image/webp";
        }
        return "image/png";
    }
}
