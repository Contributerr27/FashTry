package com.fashtry.backend.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fashtry.backend.bean.db.ModelVideoTask;
import com.fashtry.backend.common.config.GeminiApiProperties;
import com.fashtry.backend.common.constants.ClientSafeMessages;
import com.fashtry.backend.mapper.ModelVideoTaskMapper;
import com.fashtry.backend.service.ai.GeminiVeoVideoService;
import com.fashtry.backend.service.business.ModelVideoService;
import com.fashtry.backend.service.storage.CosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 消费 {@code model_video_tasks} 排队任务：拉取试穿结果图 → Veo → COS → 扣点。
 */
@Component
public class ModelVideoTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(ModelVideoTaskScheduler.class);

    private static final int STATUS_QUEUED = 0;
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;
    private static final int BATCH = 20;
    private static final int ERROR_MAX_LEN = 4000;

    private final ModelVideoTaskMapper modelVideoTaskMapper;
    private final GeminiVeoVideoService veoVideoService;
    private final CosService cosService;
    private final RestTemplate restTemplate;
    private final ModelVideoService modelVideoService;
    private final Executor aiTaskExecutor;
    private final GeminiApiProperties geminiApiProperties;

    public ModelVideoTaskScheduler(
            ModelVideoTaskMapper modelVideoTaskMapper,
            GeminiVeoVideoService veoVideoService,
            CosService cosService,
            RestTemplate restTemplate,
            ModelVideoService modelVideoService,
            @Qualifier("aiTaskExecutor") Executor aiTaskExecutor,
            GeminiApiProperties geminiApiProperties) {
        this.modelVideoTaskMapper = modelVideoTaskMapper;
        this.veoVideoService = veoVideoService;
        this.cosService = cosService;
        this.restTemplate = restTemplate;
        this.modelVideoService = modelVideoService;
        this.aiTaskExecutor = aiTaskExecutor;
        this.geminiApiProperties = geminiApiProperties;
    }

    @Scheduled(fixedDelay = 3000)
    public void run() {
        if (!geminiApiProperties.isModelVideoEnabled()) {
            return;
        }
        if (!veoVideoService.isReady()) {
            return;
        }
        LambdaQueryWrapper<ModelVideoTask> q =
                new LambdaQueryWrapper<ModelVideoTask>()
                        .eq(ModelVideoTask::getStatus, STATUS_QUEUED)
                        .orderByAsc(ModelVideoTask::getCreatedAt)
                        .last("LIMIT " + BATCH);
        List<ModelVideoTask> list = modelVideoTaskMapper.selectList(q);
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        long timeoutMs = geminiApiProperties.effectiveModelVideoSchedulerTaskTimeoutMs();
        log.info("Model-video scheduler batch={}, taskIds={}, timeoutMs={}", list.size(), list.stream()
                .map(ModelVideoTask::getId)
                .toList(), timeoutMs);
        for (ModelVideoTask task : list) {
            if (!claim(task.getId())) {
                continue;
            }
            ModelVideoTask t = task;
            CompletableFuture.runAsync(
                            () -> {
                                try {
                                    processOne(t);
                                } catch (Exception e) {
                                    log.warn(
                                            "Model-video failed taskId={}: {}",
                                            t.getId(),
                                            e.getMessage() != null ? e.getMessage() : e.toString(),
                                            e);
                                    fail(t.getId());
                                }
                            },
                            aiTaskExecutor)
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .handle(
                            (r, ex) -> {
                                if (ex != null) {
                                    Throwable c = unwrap(ex);
                                    if (c instanceof TimeoutException) {
                                        log.warn("Model-video timeout taskId={} ({}ms)", t.getId(), timeoutMs);
                                        fail(t.getId());
                                    }
                                }
                                return null;
                            });
        }
    }

    private static Throwable unwrap(Throwable ex) {
        if (ex instanceof CompletionException && ex.getCause() != null) {
            return unwrap(ex.getCause());
        }
        return ex;
    }

    private boolean claim(Long id) {
        LocalDateTime started = LocalDateTime.now();
        LambdaUpdateWrapper<ModelVideoTask> uw =
                new LambdaUpdateWrapper<ModelVideoTask>()
                        .eq(ModelVideoTask::getId, id)
                        .eq(ModelVideoTask::getStatus, STATUS_QUEUED)
                        .set(ModelVideoTask::getStatus, STATUS_PROCESSING)
                        .set(ModelVideoTask::getProcessingStartedAt, started);
        return modelVideoTaskMapper.update(null, uw) == 1;
    }

    private void processOne(ModelVideoTask task) {
        String url = task.getSourceImageUrl();
        byte[] imgBytes = restTemplate.getForObject(url, byte[].class);
        if (imgBytes == null || imgBytes.length == 0) {
            fail(task.getId());
            return;
        }
        String mime = guessImageMime(imgBytes);
        int dur =
                task.getDurationSeconds() != null && task.getDurationSeconds() > 0
                        ? task.getDurationSeconds()
                        : geminiApiProperties.getModelVideoDefaultDurationSeconds();
        String resNorm =
                StringUtils.hasText(task.getResolution())
                        ? geminiApiProperties.normalizeResolutionToken(task.getResolution())
                        : geminiApiProperties.normalizeResolutionToken(
                                geminiApiProperties.getModelVideoDefaultResolution());
        String aspectNorm =
                StringUtils.hasText(task.getAspectRatio())
                        ? geminiApiProperties.normalizeAspectRatioToken(task.getAspectRatio())
                        : geminiApiProperties.normalizeAspectRatioToken(
                                geminiApiProperties.getModelVideoDefaultAspectRatio());
        byte[] mp4 =
                veoVideoService.generateVideoFromImageBytes(
                        task.getPrompt(), imgBytes, mime, dur, resNorm, aspectNorm);
        if (mp4 == null || mp4.length == 0) {
            fail(task.getId());
            return;
        }
        String videoUrl = cosService.uploadModelVideoMp4(mp4);
        complete(task.getId(), videoUrl);
    }

    private void complete(Long id, String videoUrl) {
        LocalDateTime now = LocalDateTime.now();
        ModelVideoTask row = modelVideoTaskMapper.selectById(id);
        Long durationMs = computeDurationMs(row, now);
        LambdaUpdateWrapper<ModelVideoTask> uw =
                new LambdaUpdateWrapper<ModelVideoTask>()
                        .eq(ModelVideoTask::getId, id)
                        .eq(ModelVideoTask::getStatus, STATUS_PROCESSING)
                        .set(ModelVideoTask::getStatus, STATUS_COMPLETED)
                        .set(ModelVideoTask::getResultVideoUrl, videoUrl)
                        .set(ModelVideoTask::getErrorMsg, null)
                        .set(ModelVideoTask::getCompletedAt, now)
                        .set(ModelVideoTask::getGenerationDurationMs, durationMs);
        int n = modelVideoTaskMapper.update(null, uw);
        if (n != 1) {
            ModelVideoTask cur = modelVideoTaskMapper.selectById(id);
            if (cur != null && cur.getStatus() != null && cur.getStatus() == STATUS_COMPLETED) {
                modelVideoService.deductCreditsOnSuccess(cur);
            } else {
                log.warn("Model-video complete skipped taskId={}", id);
            }
            return;
        }
        ModelVideoTask full = modelVideoTaskMapper.selectById(id);
        if (full != null) {
            modelVideoService.deductCreditsOnSuccess(full);
        }
        log.info("Model-video completed taskId={}, durationMs={}", id, durationMs);
    }

    private void fail(Long id) {
        String msg = ClientSafeMessages.MODEL_VIDEO_GENERATION_FAILED;
        if (msg.length() > ERROR_MAX_LEN) {
            msg = msg.substring(0, ERROR_MAX_LEN);
        }
        LocalDateTime now = LocalDateTime.now();
        ModelVideoTask row = modelVideoTaskMapper.selectById(id);
        Long durationMs = computeDurationMs(row, now);
        LambdaUpdateWrapper<ModelVideoTask> uw =
                new LambdaUpdateWrapper<ModelVideoTask>()
                        .eq(ModelVideoTask::getId, id)
                        .eq(ModelVideoTask::getStatus, STATUS_PROCESSING)
                        .set(ModelVideoTask::getStatus, STATUS_FAILED)
                        .set(ModelVideoTask::getErrorMsg, msg)
                        .set(ModelVideoTask::getCompletedAt, now)
                        .set(ModelVideoTask::getGenerationDurationMs, durationMs);
        modelVideoTaskMapper.update(null, uw);
        log.info("Model-video failed persisted taskId={}", id);
    }

    private static Long computeDurationMs(ModelVideoTask row, LocalDateTime end) {
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
