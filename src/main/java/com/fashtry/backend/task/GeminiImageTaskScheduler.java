package com.fashtry.backend.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashtry.backend.bean.db.GeminiImageTask;
import com.fashtry.backend.bean.dto.GeminiGenerateRequest;
import com.fashtry.backend.bean.dto.GeminiInterleavedRequest;
import com.fashtry.backend.bean.dto.gemini.GeminiEditTaskPayload;
import com.fashtry.backend.bean.dto.gemini.GeminiInterleavedTaskPayload;
import com.fashtry.backend.bean.dto.gemini.GeminiClothingSuiteTaskPayload;
import com.fashtry.backend.bean.dto.gemini.GeminiPackshotTaskPayload;
import com.fashtry.backend.bean.dto.gemini.GeminiPortraitStudioTaskPayload;
import com.fashtry.backend.bean.dto.gemini.GeminiProductRetouchTaskPayload;
import com.fashtry.backend.bean.dto.gemini.GeminiTextToImageTaskPayload;
import com.fashtry.backend.bean.vo.GeminiImageResultJson;
import com.fashtry.backend.bean.vo.GeminiImageResultVO;
import com.fashtry.backend.common.config.GeminiApiProperties;
import com.fashtry.backend.common.constants.ClientSafeMessages;
import com.fashtry.backend.common.constants.GeminiImageTaskStatus;
import com.fashtry.backend.common.constants.GeminiImageTaskType;
import com.fashtry.backend.mapper.GeminiImageTaskMapper;
import com.fashtry.backend.service.business.WalletDeductionService;
import com.fashtry.backend.service.ai.ClothingPackshotPrompts;
import com.fashtry.backend.service.ai.ClothingProductSuitePrompts;
import com.fashtry.backend.service.ai.GeminiImageService;
import com.fashtry.backend.service.ai.PortraitStudioPrompts;
import com.fashtry.backend.service.ai.ProductRetouchPrompts;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 拉取 {@code gemini_image_tasks} 中待处理任务，调用 Gemini、将生成图上传 COS、结果写入 {@code result_json}。
 */
@Component
public class GeminiImageTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(GeminiImageTaskScheduler.class);

    private static final int BATCH = 50;
    private static final int ERROR_MAX_LEN = 4000;

    private final GeminiImageTaskMapper taskMapper;
    private final GeminiImageService geminiImageService;
    private final CosService cosService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final Executor aiTaskExecutor;
    private final GeminiApiProperties geminiApiProperties;
    private final WalletDeductionService walletDeductionService;

    public GeminiImageTaskScheduler(
            GeminiImageTaskMapper taskMapper,
            GeminiImageService geminiImageService,
            CosService cosService,
            ObjectMapper objectMapper,
            RestTemplate restTemplate,
            @Qualifier("aiTaskExecutor") Executor aiTaskExecutor,
            GeminiApiProperties geminiApiProperties,
            WalletDeductionService walletDeductionService) {
        this.taskMapper = taskMapper;
        this.geminiImageService = geminiImageService;
        this.cosService = cosService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.aiTaskExecutor = aiTaskExecutor;
        this.geminiApiProperties = geminiApiProperties;
        this.walletDeductionService = walletDeductionService;
    }

    /** 轮询间隔略短可减少排队首包延迟；单任务实际耗时主要取决于上游推理与 COS。 */
    @Scheduled(fixedDelay = 1000)
    public void run() {
        if (!geminiImageService.isReady()) {
            return;
        }
        LambdaQueryWrapper<GeminiImageTask> q =
                new LambdaQueryWrapper<GeminiImageTask>()
                        .eq(GeminiImageTask::getStatus, GeminiImageTaskStatus.PENDING)
                        .orderByAsc(GeminiImageTask::getCreatedAt)
                        .last("LIMIT " + BATCH);
        List<GeminiImageTask> list = taskMapper.selectList(q);
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        // 不在此 join 整批：若 join，fixedDelay 要等本批全部跑完才进入下一轮，单任务极慢会拖住后续任务长期停在 PENDING（与 GeminiTryonTaskScheduler 一致）。
        for (GeminiImageTask task : list) {
            if (!claim(task.getId())) {
                continue;
            }
            long perTaskTimeoutMs =
                    geminiApiProperties.effectiveGeminiImageSchedulerTaskTimeoutMs(task.getTaskType());
            GeminiImageTask t = task;
            CompletableFuture.runAsync(
                            () -> {
                                try {
                                    processOne(t);
                                } catch (Exception e) {
                                    log.warn("Gemini 任务执行失败 taskId={}: {}", t.getId(), e.getMessage());
                                    fail(t.getId(), e.getMessage());
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
                                                "Gemini 生图任务超时 taskId={} ({}ms)",
                                                t.getId(),
                                                perTaskTimeoutMs);
                                        fail(t.getId(), ClientSafeMessages.IMAGE_ASYNC_TASK_FAILED);
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
        LambdaUpdateWrapper<GeminiImageTask> uw =
                new LambdaUpdateWrapper<GeminiImageTask>()
                        .eq(GeminiImageTask::getId, id)
                        .eq(GeminiImageTask::getStatus, GeminiImageTaskStatus.PENDING)
                        .set(GeminiImageTask::getStatus, GeminiImageTaskStatus.PROCESSING)
                        .set(GeminiImageTask::getProcessingStartedAt, started)
                        .set(GeminiImageTask::getUpdatedAt, started);
        return taskMapper.update(null, uw) == 1;
    }

    private void processOne(GeminiImageTask task) throws Exception {
        String type = task.getTaskType();
        if (GeminiImageTaskType.TEXT_TO_IMAGE.equals(type)) {
            GeminiTextToImageTaskPayload p = objectMapper.readValue(task.getPayloadJson(), GeminiTextToImageTaskPayload.class);
            GeminiGenerateRequest req = new GeminiGenerateRequest();
            req.setPrompt(p.getPrompt());
            req.setModelId(p.getModelId());
            req.setAspectRatio(p.getAspectRatio());
            req.setImageSize(p.getImageSize());
            GeminiImageResultVO vo = geminiImageService.generateFromText(req);
            finishWithResult(task.getId(), type, vo);
            return;
        }
        if (GeminiImageTaskType.EDIT.equals(type)) {
            GeminiEditTaskPayload p = objectMapper.readValue(task.getPayloadJson(), GeminiEditTaskPayload.class);
            byte[] bytes = download(p.getSourceCosUrl());
            String mime = guessImageMime(bytes);
            byte[] maskBytes = null;
            String maskMime = null;
            if (StringUtils.hasText(p.getMaskCosUrl())) {
                maskBytes = download(p.getMaskCosUrl());
                maskMime = guessImageMime(maskBytes);
            }
            byte[] refBytes = null;
            String refMime = null;
            if (StringUtils.hasText(p.getReferenceCosUrl())) {
                refBytes = download(p.getReferenceCosUrl());
                refMime = guessImageMime(refBytes);
            }
            GeminiImageResultVO vo =
                    geminiImageService.editImage(
                            bytes,
                            mime,
                            maskBytes,
                            maskMime,
                            refBytes,
                            refMime,
                            p.getInstruction(),
                            p.getModelId(),
                            p.getAspectRatio(),
                            p.getImageSize());
            finishWithResult(task.getId(), type, vo);
            return;
        }
        if (GeminiImageTaskType.INTERLEAVED.equals(type)) {
            GeminiInterleavedTaskPayload p = objectMapper.readValue(task.getPayloadJson(), GeminiInterleavedTaskPayload.class);
            GeminiImageResultVO vo;
            if (p.getImageCosUrls() != null && !p.getImageCosUrls().isEmpty()) {
                List<byte[]> imageBytes = new ArrayList<>();
                List<String> mimes = new ArrayList<>();
                for (String url : p.getImageCosUrls()) {
                    byte[] b = download(url);
                    imageBytes.add(b);
                    mimes.add(guessImageMime(b));
                }
                vo =
                        geminiImageService.generateInterleavedWithImages(
                                imageBytes,
                                mimes,
                                p.getPrompt(),
                                p.getSecondPrompt(),
                                p.getModelId(),
                                p.getAspectRatio(),
                                p.getImageSize());
            } else {
                GeminiInterleavedRequest req = new GeminiInterleavedRequest();
                req.setPrompt(p.getPrompt());
                if (StringUtils.hasText(p.getSecondPrompt())) {
                    req.setSecondPrompt(p.getSecondPrompt());
                }
                req.setModelId(p.getModelId());
                req.setAspectRatio(p.getAspectRatio());
                req.setImageSize(p.getImageSize());
                vo = geminiImageService.generateInterleaved(req);
            }
            finishWithResult(task.getId(), type, vo);
            return;
        }
        if (GeminiImageTaskType.PRODUCT_RETOUCH.equals(type)) {
            GeminiProductRetouchTaskPayload p =
                    objectMapper.readValue(task.getPayloadJson(), GeminiProductRetouchTaskPayload.class);
            Integer regSlot = p.getRegenerateSlotIndex();
            Long parentProductId = p.getParentTaskId();
            if (regSlot != null && parentProductId != null) {
                processProductRetouchSlotRegenerate(task.getId(), p, regSlot, parentProductId);
                return;
            }
            processProductRetouch(task);
            return;
        }
        if (GeminiImageTaskType.PORTRAIT_STUDIO.equals(type)) {
            GeminiPortraitStudioTaskPayload p =
                    objectMapper.readValue(task.getPayloadJson(), GeminiPortraitStudioTaskPayload.class);
            Integer regSlot = p.getRegenerateSlotIndex();
            Long parentPortraitId = p.getParentTaskId();
            if (regSlot != null && parentPortraitId != null) {
                processPortraitSlotRegenerate(task.getId(), p, regSlot, parentPortraitId);
                return;
            }
            processPortraitStudio(task);
            return;
        }
        if (GeminiImageTaskType.CLOTHING_PACKSHOT.equals(type)) {
            GeminiPackshotTaskPayload p =
                    objectMapper.readValue(task.getPayloadJson(), GeminiPackshotTaskPayload.class);
            if (!StringUtils.hasText(p.getSourceCosUrl())) {
                fail(task.getId(), "缺少服装平铺图地址");
                return;
            }
            Integer regSlot = p.getRegenerateSlotIndex();
            Long parentPackshotId = p.getParentTaskId();
            if (regSlot != null && parentPackshotId != null) {
                processPackshotSlotRegenerate(task.getId(), p, regSlot, parentPackshotId);
                return;
            }
            String art = ClothingPackshotPrompts.effectiveArtDirection(p);
            String gender = ClothingPackshotPrompts.normalizeModelGender(p.getModelGender());
            String faceMode = ClothingPackshotPrompts.normalizeFaceMode(p.getFaceMode());
            String facePreset = ClothingPackshotPrompts.normalizeFacePreset(p.getFacePreset());
            String framingMode = ClothingPackshotPrompts.normalizeFramingMode(p.getFramingMode());
            int sceneN = p.getSceneVariantCount() != null ? p.getSceneVariantCount() : 4;
            List<String> labelsFixed = buildPackshotResultLabels(sceneN);
            ArrayList<String> urls = new ArrayList<>(Collections.nCopies(sceneN + 2, ""));
            writeProcessingPartialResult(task.getId(), urls, labelsFixed);
            byte[] flatBytes = download(p.getSourceCosUrl());
            String flatMime = guessImageMime(flatBytes);
            boolean hasFaceRef = StringUtils.hasText(p.getFaceReferenceCosUrl());
            List<byte[]> step1Bytes = new ArrayList<>();
            List<String> step1Mimes = new ArrayList<>();
            step1Bytes.add(flatBytes);
            step1Mimes.add(flatMime);
            if (hasFaceRef) {
                byte[] faceBytes = download(p.getFaceReferenceCosUrl());
                step1Bytes.add(faceBytes);
                step1Mimes.add(guessImageMime(faceBytes));
            }
            String prompt =
                    ClothingPackshotPrompts.step1UserPrompt(
                            art,
                            gender,
                            p.getAgeGroup(),
                            p.getBodyType(),
                            faceMode,
                            facePreset,
                            p.getModelStyleNote(),
                            hasFaceRef,
                            framingMode);
            String second = ClothingPackshotPrompts.step1SecondPrompt(framingMode);
            GeminiImageResultVO step1 =
                    geminiImageService.generateInterleavedWithImages(
                            step1Bytes,
                            step1Mimes,
                            prompt,
                            second,
                            p.getModelId(),
                            p.getAspectRatio(),
                            p.getImageSize());
            String modelB64 = firstNonEmptyImageB64(step1);
            if (!StringUtils.hasText(modelB64)) {
                fail(
                        task.getId(),
                        "第一步未生成模特图（可能被安全策略拦截或模型无图输出），请更换素材后重试");
                return;
            }
            byte[] modelBytes = Base64.getDecoder().decode(modelB64);
            String modelMime = guessImageMime(modelBytes);
            String modelWearUrl = cosService.uploadGeminiGeneratedImage(modelBytes, modelMime);
            urls.set(0, modelWearUrl);
            writeProcessingPartialResult(task.getId(), urls, labelsFixed);
            List<String> variantPrompts =
                    ClothingPackshotPrompts.sceneAndPoseVariantInstructions(art, sceneN, framingMode);
            // 每张场景与白底均从「第一步上身图」编辑，避免链式以上一景为输入导致人脸/身份逐步漂移
            for (int i = 0; i < variantPrompts.size(); i++) {
                GeminiImageResultVO ev =
                        geminiImageService.editImage(
                                modelBytes,
                                modelMime,
                                null,
                                null,
                                null,
                                null,
                                variantPrompts.get(i),
                                p.getModelId(),
                                p.getAspectRatio(),
                                p.getImageSize());
                String b64 = firstNonEmptyImageB64(ev);
                if (!StringUtils.hasText(b64)) {
                    fail(task.getId(), "场景图生成失败（第 " + (i + 1) + " 张），请重试");
                    return;
                }
                byte[] sceneRaw = Base64.getDecoder().decode(b64);
                String sceneUrl = cosService.uploadGeminiGeneratedImage(sceneRaw, guessImageMime(sceneRaw));
                urls.set(i + 1, sceneUrl);
                writeProcessingPartialResult(task.getId(), urls, labelsFixed);
            }
            GeminiImageResultVO whiteVo =
                    geminiImageService.editImage(
                            modelBytes,
                            modelMime,
                            null,
                            null,
                            null,
                            null,
                            ClothingPackshotPrompts.whiteEcomInstruction(framingMode),
                            p.getModelId(),
                            p.getAspectRatio(),
                            p.getImageSize());
            String whiteB64 = firstNonEmptyImageB64(whiteVo);
            if (!StringUtils.hasText(whiteB64)) {
                fail(task.getId(), "白底主图生成失败，请重试");
                return;
            }
            byte[] whiteRaw = Base64.getDecoder().decode(whiteB64);
            String whiteUrl = cosService.uploadGeminiGeneratedImage(whiteRaw, guessImageMime(whiteRaw));
            urls.set(sceneN + 1, whiteUrl);
            writeProcessingPartialResult(task.getId(), urls, labelsFixed);
            GeminiImageResultJson done = new GeminiImageResultJson();
            done.setUrls(urls);
            done.setImageLabels(labelsFixed);
            done.setTextParts(List.of());
            complete(task.getId(), objectMapper.writeValueAsString(done));
            return;
        }
        if (GeminiImageTaskType.CLOTHING_PRODUCT_SUITE.equals(type)) {
            GeminiClothingSuiteTaskPayload p =
                    objectMapper.readValue(task.getPayloadJson(), GeminiClothingSuiteTaskPayload.class);
            if (!StringUtils.hasText(p.getSourceCosUrl())) {
                fail(task.getId(), "缺少服装图地址");
                return;
            }
            byte[] garmentBytes = download(p.getSourceCosUrl());
            String garmentMime = guessImageMime(garmentBytes);
            String lang = ClothingProductSuitePrompts.normalizeOutputLanguage(p.getOutputLanguage());
            String genderNorm = ClothingPackshotPrompts.normalizeModelGender(p.getModelGender());
            if (p.getRegenerateSlotIndex() != null) {
                int i = p.getRegenerateSlotIndex();
                if (i < 0 || i >= ClothingProductSuitePrompts.STEP_COUNT) {
                    fail(task.getId(), "无效的步骤索引");
                    return;
                }
                String ar = ClothingProductSuitePrompts.aspectRatioForStep(i, p.getAspectRatio());
                GeminiImageResultVO stepVo =
                        geminiImageService.editImage(
                                garmentBytes,
                                garmentMime,
                                null,
                                null,
                                null,
                                null,
                                ClothingProductSuitePrompts.instruction(
                                        i, lang, genderNorm, p.getProductBrief(), p.getMarketplace()),
                                p.getModelId(),
                                ar,
                                p.getImageSize());
                String b64 = firstNonEmptyImageB64(stepVo);
                if (!StringUtils.hasText(b64)) {
                    fail(task.getId(), "单张重生成失败，请重试");
                    return;
                }
                Long parentSuiteId = p.getParentTaskId();
                GeminiImageTask baseRow =
                        parentSuiteId != null
                                ? taskMapper.selectById(parentSuiteId)
                                : taskMapper.selectById(task.getId());
                if (baseRow == null || !StringUtils.hasText(baseRow.getResultJson())) {
                    fail(task.getId(), "缺少原有结果，无法合并单张");
                    return;
                }
                GeminiImageResultJson existing =
                        objectMapper.readValue(baseRow.getResultJson(), GeminiImageResultJson.class);
                List<String> urlList = existing.getUrls();
                if (urlList == null || urlList.size() < ClothingProductSuitePrompts.STEP_COUNT) {
                    fail(task.getId(), "原有结果不完整，无法合并");
                    return;
                }
                byte[] raw = Base64.getDecoder().decode(b64);
                String newUrl = cosService.uploadGeminiGeneratedImage(raw, guessImageMime(raw));
                ArrayList<String> mergedUrls = new ArrayList<>(urlList);
                mergedUrls.set(i, newUrl);
                existing.setUrls(mergedUrls);
                String mergedJson = objectMapper.writeValueAsString(existing);
                if (parentSuiteId != null) {
                    updateParentGeminiImageTaskResult(parentSuiteId, mergedJson);
                    complete(task.getId(), mergedJson);
                } else {
                    finishSuiteSlotMerge(task.getId(), mergedJson);
                }
                return;
            }
            // 整套套图：每步上传 COS 后写入 result_json（仍为 PROCESSING），便于前端轮询逐格展示；未出图槽位用空串占位（JSON 稳定）
            ArrayList<String> urls =
                    new ArrayList<>(Collections.nCopies(ClothingProductSuitePrompts.STEP_COUNT, ""));
            List<String> labelsFixed = new ArrayList<>(Arrays.asList(ClothingProductSuitePrompts.LABELS));
            writeProcessingPartialResult(task.getId(), urls, labelsFixed);
            for (int i = 0; i < ClothingProductSuitePrompts.STEP_COUNT; i++) {
                String ar = ClothingProductSuitePrompts.aspectRatioForStep(i, p.getAspectRatio());
                GeminiImageResultVO stepVo =
                        geminiImageService.editImage(
                                garmentBytes,
                                garmentMime,
                                null,
                                null,
                                null,
                                null,
                                ClothingProductSuitePrompts.instruction(
                                        i, lang, genderNorm, p.getProductBrief(), p.getMarketplace()),
                                p.getModelId(),
                                ar,
                                p.getImageSize());
                String b64 = firstNonEmptyImageB64(stepVo);
                if (!StringUtils.hasText(b64)) {
                    fail(task.getId(), "套图生成失败（第 " + (i + 1) + " 张），请重试");
                    return;
                }
                byte[] raw = Base64.getDecoder().decode(b64);
                String cosUrl = cosService.uploadGeminiGeneratedImage(raw, guessImageMime(raw));
                urls.set(i, cosUrl);
                writeProcessingPartialResult(task.getId(), urls, labelsFixed);
            }
            GeminiImageResultJson done = new GeminiImageResultJson();
            done.setUrls(urls);
            done.setImageLabels(labelsFixed);
            done.setTextParts(List.of());
            complete(task.getId(), objectMapper.writeValueAsString(done));
            return;
        }
        fail(task.getId(), "未知任务类型: " + type);
    }

    /**
     * 模特出图单格重生成：按 slot 调用与整套流程一致的子步骤，合并进父任务 result_json 并完成子任务。
     */
    private void processPackshotSlotRegenerate(
            Long childTaskId, GeminiPackshotTaskPayload p, int slot, Long parentId) throws Exception {
        GeminiImageTask parentRow = taskMapper.selectById(parentId);
        if (parentRow == null || !StringUtils.hasText(parentRow.getResultJson())) {
            fail(childTaskId, "缺少原有结果，无法合并单张");
            return;
        }
        GeminiImageResultJson existing = objectMapper.readValue(parentRow.getResultJson(), GeminiImageResultJson.class);
        List<String> parentUrls = existing.getUrls();
        int sceneN = p.getSceneVariantCount() != null ? p.getSceneVariantCount() : 4;
        int totalSlots = sceneN + 2;
        if (slot < 0 || slot >= totalSlots) {
            fail(childTaskId, "无效的步骤索引");
            return;
        }
        if (parentUrls == null || parentUrls.size() < totalSlots) {
            fail(childTaskId, "原有结果不完整，无法合并");
            return;
        }

        String art = ClothingPackshotPrompts.effectiveArtDirection(p);
        String gender = ClothingPackshotPrompts.normalizeModelGender(p.getModelGender());
        String faceMode = ClothingPackshotPrompts.normalizeFaceMode(p.getFaceMode());
        String facePreset = ClothingPackshotPrompts.normalizeFacePreset(p.getFacePreset());
        String framingMode = ClothingPackshotPrompts.normalizeFramingMode(p.getFramingMode());
        List<String> variantPrompts =
                ClothingPackshotPrompts.sceneAndPoseVariantInstructions(art, sceneN, framingMode);

        String newB64;
        if (slot == 0) {
            byte[] flatBytes = download(p.getSourceCosUrl());
            String flatMime = guessImageMime(flatBytes);
            boolean hasFaceRef = StringUtils.hasText(p.getFaceReferenceCosUrl());
            List<byte[]> step1Bytes = new ArrayList<>();
            List<String> step1Mimes = new ArrayList<>();
            step1Bytes.add(flatBytes);
            step1Mimes.add(flatMime);
            if (hasFaceRef) {
                byte[] faceBytes = download(p.getFaceReferenceCosUrl());
                step1Bytes.add(faceBytes);
                step1Mimes.add(guessImageMime(faceBytes));
            }
            String prompt =
                    ClothingPackshotPrompts.step1UserPrompt(
                            art,
                            gender,
                            p.getAgeGroup(),
                            p.getBodyType(),
                            faceMode,
                            facePreset,
                            p.getModelStyleNote(),
                            hasFaceRef,
                            framingMode);
            String second = ClothingPackshotPrompts.step1SecondPrompt(framingMode);
            GeminiImageResultVO step1 =
                    geminiImageService.generateInterleavedWithImages(
                            step1Bytes,
                            step1Mimes,
                            prompt,
                            second,
                            p.getModelId(),
                            p.getAspectRatio(),
                            p.getImageSize());
            newB64 = firstNonEmptyImageB64(step1);
            if (!StringUtils.hasText(newB64)) {
                fail(
                        childTaskId,
                        "第一步未生成模特图（可能被安全策略拦截或模型无图输出），请更换素材后重试");
                return;
            }
        } else if (slot <= sceneN) {
            byte[] modelBytes = download(parentUrls.get(0));
            String modelMime = guessImageMime(modelBytes);
            int sceneIdx = slot - 1;
            if (sceneIdx < 0 || sceneIdx >= variantPrompts.size()) {
                fail(childTaskId, "单张重生成失败：场景索引无效");
                return;
            }
            GeminiImageResultVO ev =
                    geminiImageService.editImage(
                            modelBytes,
                            modelMime,
                            null,
                            null,
                            null,
                            null,
                            variantPrompts.get(sceneIdx),
                            p.getModelId(),
                            p.getAspectRatio(),
                            p.getImageSize());
            newB64 = firstNonEmptyImageB64(ev);
            if (!StringUtils.hasText(newB64)) {
                fail(childTaskId, "场景图单张重生成失败，请重试");
                return;
            }
        } else {
            byte[] modelBytes = download(parentUrls.get(0));
            String modelMime = guessImageMime(modelBytes);
            GeminiImageResultVO whiteVo =
                    geminiImageService.editImage(
                            modelBytes,
                            modelMime,
                            null,
                            null,
                            null,
                            null,
                            ClothingPackshotPrompts.whiteEcomInstruction(framingMode),
                            p.getModelId(),
                            p.getAspectRatio(),
                            p.getImageSize());
            newB64 = firstNonEmptyImageB64(whiteVo);
            if (!StringUtils.hasText(newB64)) {
                fail(childTaskId, "白底主图单张重生成失败，请重试");
                return;
            }
        }

        byte[] raw = Base64.getDecoder().decode(newB64);
        String newUrl = cosService.uploadGeminiGeneratedImage(raw, guessImageMime(raw));
        ArrayList<String> mergedUrls = new ArrayList<>(parentUrls);
        mergedUrls.set(slot, newUrl);
        existing.setUrls(mergedUrls);

        List<String> mergedLabels;
        if (existing.getImageLabels() != null && existing.getImageLabels().size() == mergedUrls.size()) {
            mergedLabels = new ArrayList<>(existing.getImageLabels());
            mergedLabels.set(slot, packshotLabelForSlot(slot, sceneN));
        } else {
            mergedLabels = packshotDefaultLabels(sceneN);
        }
        existing.setImageLabels(mergedLabels);

        String mergedJson = objectMapper.writeValueAsString(existing);
        updateParentGeminiImageTaskResult(parentId, mergedJson);
        complete(childTaskId, mergedJson);
    }

    private static String packshotLabelForSlot(int slot, int sceneN) {
        if (slot == 0) {
            return "MODEL_WEAR";
        }
        if (slot == sceneN + 1) {
            return "ECOM_WHITE";
        }
        return "SCENE_" + slot;
    }

    private static List<String> packshotDefaultLabels(int sceneN) {
        List<String> labels = new ArrayList<>();
        labels.add("MODEL_WEAR");
        for (int i = 0; i < sceneN; i++) {
            labels.add("SCENE_" + (i + 1));
        }
        labels.add("ECOM_WHITE");
        return labels;
    }

    /**
     * TEXT_TO_IMAGE / EDIT 必须至少有一张图；否则记失败（避免 result_json 里 urls 为空仍 status=2）。
     * INTERLEAVED 允许纯文本结果。虚拟试穿已迁至 {@code tryon_tasks}（见 {@link GeminiTryonTaskScheduler}）。
     */
    private void finishWithResult(Long taskId, String taskType, GeminiImageResultVO vo) throws Exception {
        finishWithResult(taskId, taskType, vo, null);
    }

    private void finishWithResult(
            Long taskId, String taskType, GeminiImageResultVO vo, List<String> imageLabels)
            throws Exception {
        if (requiresImageOutput(taskType) && !hasAnyImageBase64(vo)) {
            fail(
                    taskId,
                    "模型未返回图片（可能仅返回文本、被安全策略拦截或响应异常），请重试或更换素材/模型");
            log.warn("Gemini 任务无图片输出 taskId={}, type={}", taskId, taskType);
            return;
        }
        complete(taskId, toResultJson(vo, imageLabels));
    }

    private static boolean requiresImageOutput(String taskType) {
        return GeminiImageTaskType.TEXT_TO_IMAGE.equals(taskType)
                || GeminiImageTaskType.EDIT.equals(taskType)
                || GeminiImageTaskType.CLOTHING_PACKSHOT.equals(taskType)
                || GeminiImageTaskType.CLOTHING_PRODUCT_SUITE.equals(taskType);
    }

    private static String firstNonEmptyImageB64(GeminiImageResultVO vo) {
        if (vo.getImagesBase64() == null) {
            return null;
        }
        for (String b64 : vo.getImagesBase64()) {
            if (StringUtils.hasText(b64)) {
                return b64;
            }
        }
        return null;
    }

    private static boolean hasAnyImageBase64(GeminiImageResultVO vo) {
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

    private void processProductRetouch(GeminiImageTask task) throws Exception {
        GeminiProductRetouchTaskPayload p =
                objectMapper.readValue(task.getPayloadJson(), GeminiProductRetouchTaskPayload.class);
        List<String> sources = p.getSourceCosUrls();
        if (sources == null || sources.isEmpty()) {
            fail(task.getId(), "缺少原图");
            return;
        }
        int n = sources.size();
        String ar = StringUtils.hasText(p.getAspectRatio()) ? p.getAspectRatio().trim() : "3:4";
        String isz = StringUtils.hasText(p.getImageSize()) ? p.getImageSize().trim() : "1K";
        String modelId = StringUtils.hasText(geminiApiProperties.getProductRetouchModelId())
                ? geminiApiProperties.getProductRetouchModelId()
                : "gemini-2.5-flash-image";
        List<String> urls = new ArrayList<>(Collections.nCopies(n, ""));
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            labels.add("RETOUCH_" + (i + 1));
        }
        writeProcessingPartialResult(task.getId(), urls, labels);
        for (int i = 0; i < n; i++) {
            byte[] bytes = download(sources.get(i));
            String mime = guessImageMime(bytes);
            String instruction =
                    ProductRetouchPrompts.buildImageEditInstruction(
                            p.getBackgroundColor(),
                            ar,
                            p.getGarmentScope(),
                            p.getShootHint(),
                            p.getCropPreference());
            GeminiImageResultVO vo =
                    geminiImageService.editImage(
                            bytes, mime, null, null, null, null, instruction, modelId, ar, isz);
            if (!hasAnyImageBase64(vo)) {
                fail(task.getId(), "第 " + (i + 1) + " 张未返回图片，请重试或换图");
                return;
            }
            List<String> up = uploadImageBytesFromResult(vo);
            if (up.isEmpty()) {
                fail(task.getId(), "第 " + (i + 1) + " 张出图处理失败");
                return;
            }
            urls.set(i, up.get(0));
            writeProcessingPartialResult(task.getId(), new ArrayList<>(urls), labels);
        }
        GeminiImageResultJson out = new GeminiImageResultJson();
        out.setUrls(new ArrayList<>(urls));
        out.setImageLabels(new ArrayList<>(labels));
        out.setTextParts(new ArrayList<>());
        complete(task.getId(), objectMapper.writeValueAsString(out));
    }

    /**
     * 服装提取单张重生成：仅重跑 {@code slot} 对应源图的一张，合并进父任务 {@code result_json} 并完成子任务。
     */
    private void processProductRetouchSlotRegenerate(
            Long childTaskId, GeminiProductRetouchTaskPayload p, int slot, Long parentId) throws Exception {
        GeminiImageTask parentRow = taskMapper.selectById(parentId);
        if (parentRow == null || !StringUtils.hasText(parentRow.getResultJson())) {
            fail(childTaskId, "缺少原有结果，无法合并单张");
            return;
        }
        GeminiImageResultJson existing = objectMapper.readValue(parentRow.getResultJson(), GeminiImageResultJson.class);
        List<String> parentUrls = existing.getUrls();
        List<String> sources = p.getSourceCosUrls();
        if (sources == null || sources.isEmpty()) {
            fail(childTaskId, "缺少原图");
            return;
        }
        int n = sources.size();
        if (slot < 0 || slot >= n) {
            fail(childTaskId, "无效的图片索引");
            return;
        }
        if (parentUrls == null || parentUrls.size() < n) {
            fail(childTaskId, "原有结果不完整，无法合并");
            return;
        }
        String ar = StringUtils.hasText(p.getAspectRatio()) ? p.getAspectRatio().trim() : "3:4";
        String isz = StringUtils.hasText(p.getImageSize()) ? p.getImageSize().trim() : "1K";
        String modelId = StringUtils.hasText(geminiApiProperties.getProductRetouchModelId())
                ? geminiApiProperties.getProductRetouchModelId()
                : "gemini-2.5-flash-image";
        byte[] bytes = download(sources.get(slot).trim());
        String mime = guessImageMime(bytes);
        String instruction =
                ProductRetouchPrompts.buildImageEditInstruction(
                        p.getBackgroundColor(),
                        ar,
                        p.getGarmentScope(),
                        p.getShootHint(),
                        p.getCropPreference());
        GeminiImageResultVO vo =
                geminiImageService.editImage(
                        bytes, mime, null, null, null, null, instruction, modelId, ar, isz);
        if (!hasAnyImageBase64(vo)) {
            fail(childTaskId, "单张重生成未返回图片，请重试或换图");
            return;
        }
        List<String> up = uploadImageBytesFromResult(vo);
        if (up.isEmpty()) {
            fail(childTaskId, "单张重生成出图处理失败");
            return;
        }
        ArrayList<String> mergedUrls = new ArrayList<>(parentUrls);
        mergedUrls.set(slot, up.get(0));
        existing.setUrls(mergedUrls);
        List<String> mergedLabels;
        if (existing.getImageLabels() != null && existing.getImageLabels().size() == mergedUrls.size()) {
            mergedLabels = new ArrayList<>(existing.getImageLabels());
            mergedLabels.set(slot, "RETOUCH_" + (slot + 1));
        } else {
            mergedLabels = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                mergedLabels.add("RETOUCH_" + (i + 1));
            }
        }
        existing.setImageLabels(mergedLabels);
        String mergedJson = objectMapper.writeValueAsString(existing);
        updateParentGeminiImageTaskResult(parentId, mergedJson);
        complete(childTaskId, mergedJson);
    }

    private void processPortraitStudio(GeminiImageTask task) throws Exception {
        GeminiPortraitStudioTaskPayload p =
                objectMapper.readValue(task.getPayloadJson(), GeminiPortraitStudioTaskPayload.class);
        List<String> cosUrls = new ArrayList<>();
        if (p.getSourceCosUrls() != null) {
            for (String u : p.getSourceCosUrls()) {
                if (StringUtils.hasText(u)) {
                    cosUrls.add(u.trim());
                }
            }
        }
        if (cosUrls.isEmpty() && StringUtils.hasText(p.getSourceCosUrl())) {
            cosUrls.add(p.getSourceCosUrl().trim());
        }
        if (cosUrls.isEmpty()) {
            fail(task.getId(), "缺少参考照片");
            return;
        }
        int n = p.getOutputCount() != null ? p.getOutputCount() : 1;
        n = Math.max(1, Math.min(GeminiApiProperties.PORTRAIT_STUDIO_MAX_OUTPUTS, n));
        String ar = StringUtils.hasText(p.getAspectRatio()) ? p.getAspectRatio().trim() : "3:4";
        String isz = StringUtils.hasText(p.getImageSize()) ? p.getImageSize().trim() : "1K";
        String modelId = StringUtils.hasText(p.getModelId()) ? p.getModelId().trim() : null;
        String style = PortraitStudioPrompts.normalizeStylePreset(p.getStylePreset());
        String mk = PortraitStudioPrompts.normalizeMakeupPreset(p.getMakeupPreset());
        String om = PortraitStudioPrompts.normalizeOutfitMode(p.getOutfitMode());
        List<byte[]> refBytes = new ArrayList<>();
        List<String> refMimes = new ArrayList<>();
        for (String u : cosUrls) {
            byte[] b = download(u);
            refBytes.add(b);
            refMimes.add(guessImageMime(b));
        }
        int refN = refBytes.size();
        List<String> urls = new ArrayList<>(Collections.nCopies(n, ""));
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            labels.add("PORTRAIT_" + (i + 1));
        }
        writeProcessingPartialResult(task.getId(), urls, labels);
        for (int i = 0; i < n; i++) {
            String instruction = PortraitStudioPrompts.buildInstruction(style, i + 1, n, mk, refN, om);
            GeminiImageResultVO vo;
            if (refN == 1) {
                vo =
                        geminiImageService.editImage(
                                refBytes.get(0),
                                refMimes.get(0),
                                null,
                                null,
                                null,
                                null,
                                instruction,
                                modelId,
                                ar,
                                isz);
            } else {
                vo =
                        geminiImageService.editImageWithOrderedReferenceImages(
                                refBytes, refMimes, instruction, modelId, ar, isz);
            }
            if (!hasAnyImageBase64(vo)) {
                fail(task.getId(), "第 " + (i + 1) + " 张未返回图片，请重试或换图");
                return;
            }
            List<String> up = uploadImageBytesFromResult(vo);
            if (up.isEmpty()) {
                fail(task.getId(), "第 " + (i + 1) + " 张出图处理失败");
                return;
            }
            urls.set(i, up.get(0));
            writeProcessingPartialResult(task.getId(), new ArrayList<>(urls), labels);
        }
        GeminiImageResultJson out = new GeminiImageResultJson();
        out.setUrls(new ArrayList<>(urls));
        out.setImageLabels(new ArrayList<>(labels));
        out.setTextParts(new ArrayList<>());
        complete(task.getId(), objectMapper.writeValueAsString(out));
    }

    /**
     * AI 写真单格重生成：仅生成 {@code slot} 对应的一张，合并进父任务 {@code result_json} 并完成子任务。
     */
    private void processPortraitSlotRegenerate(
            Long childTaskId, GeminiPortraitStudioTaskPayload p, int slot, Long parentId) throws Exception {
        GeminiImageTask parentRow = taskMapper.selectById(parentId);
        if (parentRow == null || !StringUtils.hasText(parentRow.getResultJson())) {
            fail(childTaskId, "缺少原有结果，无法合并单张");
            return;
        }
        GeminiImageResultJson existing = objectMapper.readValue(parentRow.getResultJson(), GeminiImageResultJson.class);
        List<String> parentUrls = existing.getUrls();
        int n = p.getOutputCount() != null ? p.getOutputCount() : 1;
        n = Math.max(1, Math.min(GeminiApiProperties.PORTRAIT_STUDIO_MAX_OUTPUTS, n));
        if (slot < 0 || slot >= n) {
            fail(childTaskId, "无效的成片索引");
            return;
        }
        if (parentUrls == null || parentUrls.size() < n) {
            fail(childTaskId, "原有结果不完整，无法合并");
            return;
        }
        List<String> cosUrls = new ArrayList<>();
        if (p.getSourceCosUrls() != null) {
            for (String u : p.getSourceCosUrls()) {
                if (StringUtils.hasText(u)) {
                    cosUrls.add(u.trim());
                }
            }
        }
        if (cosUrls.isEmpty() && StringUtils.hasText(p.getSourceCosUrl())) {
            cosUrls.add(p.getSourceCosUrl().trim());
        }
        if (cosUrls.isEmpty()) {
            fail(childTaskId, "缺少参考照片");
            return;
        }
        String ar = StringUtils.hasText(p.getAspectRatio()) ? p.getAspectRatio().trim() : "3:4";
        String isz = StringUtils.hasText(p.getImageSize()) ? p.getImageSize().trim() : "1K";
        String modelId = StringUtils.hasText(p.getModelId()) ? p.getModelId().trim() : null;
        String style = PortraitStudioPrompts.normalizeStylePreset(p.getStylePreset());
        String mk = PortraitStudioPrompts.normalizeMakeupPreset(p.getMakeupPreset());
        String om = PortraitStudioPrompts.normalizeOutfitMode(p.getOutfitMode());
        List<byte[]> refBytes = new ArrayList<>();
        List<String> refMimes = new ArrayList<>();
        for (String u : cosUrls) {
            byte[] b = download(u);
            refBytes.add(b);
            refMimes.add(guessImageMime(b));
        }
        int refN = refBytes.size();
        String instruction = PortraitStudioPrompts.buildInstruction(style, slot + 1, n, mk, refN, om);
        GeminiImageResultVO vo;
        if (refN == 1) {
            vo =
                    geminiImageService.editImage(
                            refBytes.get(0),
                            refMimes.get(0),
                            null,
                            null,
                            null,
                            null,
                            instruction,
                            modelId,
                            ar,
                            isz);
        } else {
            vo =
                    geminiImageService.editImageWithOrderedReferenceImages(
                            refBytes, refMimes, instruction, modelId, ar, isz);
        }
        if (!hasAnyImageBase64(vo)) {
            fail(childTaskId, "单张重生成未返回图片，请重试或换图");
            return;
        }
        List<String> up = uploadImageBytesFromResult(vo);
        if (up.isEmpty()) {
            fail(childTaskId, "单张重生成出图处理失败");
            return;
        }
        ArrayList<String> mergedUrls = new ArrayList<>(parentUrls);
        mergedUrls.set(slot, up.get(0));
        existing.setUrls(mergedUrls);
        List<String> mergedLabels;
        if (existing.getImageLabels() != null && existing.getImageLabels().size() == mergedUrls.size()) {
            mergedLabels = new ArrayList<>(existing.getImageLabels());
            mergedLabels.set(slot, "PORTRAIT_" + (slot + 1));
        } else {
            mergedLabels = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                mergedLabels.add("PORTRAIT_" + (i + 1));
            }
        }
        existing.setImageLabels(mergedLabels);
        String mergedJson = objectMapper.writeValueAsString(existing);
        updateParentGeminiImageTaskResult(parentId, mergedJson);
        complete(childTaskId, mergedJson);
    }

    private List<String> uploadImageBytesFromResult(GeminiImageResultVO vo) throws Exception {
        List<String> urls = new ArrayList<>();
        if (vo.getImagesBase64() == null) {
            return urls;
        }
        for (String b64 : vo.getImagesBase64()) {
            if (!StringUtils.hasText(b64)) {
                continue;
            }
            byte[] raw = Base64.getDecoder().decode(b64);
            urls.add(cosService.uploadGeminiGeneratedImage(raw, guessImageMime(raw)));
        }
        return urls;
    }

    private byte[] download(String url) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("图片 URL 为空");
        }
        byte[] bytes = restTemplate.getForObject(url, byte[].class);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("下载图片失败: " + url);
        }
        return bytes;
    }

    private String toResultJson(GeminiImageResultVO vo) throws Exception {
        return toResultJson(vo, null);
    }

    private String toResultJson(GeminiImageResultVO vo, List<String> imageLabels) throws Exception {
        GeminiImageResultJson out = new GeminiImageResultJson();
        out.setTextParts(vo.getTextParts() != null ? vo.getTextParts() : List.of());
        List<String> urls = new ArrayList<>();
        if (vo.getImagesBase64() != null) {
            for (String b64 : vo.getImagesBase64()) {
                if (!StringUtils.hasText(b64)) {
                    continue;
                }
                byte[] raw = Base64.getDecoder().decode(b64);
                urls.add(cosService.uploadGeminiGeneratedImage(raw, guessImageMime(raw)));
            }
        }
        out.setUrls(urls);
        if (imageLabels != null && !imageLabels.isEmpty()) {
            out.setImageLabels(imageLabels);
        } else {
            out.setImageLabels(new ArrayList<>());
        }
        return objectMapper.writeValueAsString(out);
    }

    /** 子任务单张成功：将合并后的整套 URL 写回父任务（历史列表只看父任务行） */
    private void updateParentGeminiImageTaskResult(Long parentTaskId, String mergedJson) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<GeminiImageTask> uw =
                new LambdaUpdateWrapper<GeminiImageTask>()
                        .eq(GeminiImageTask::getId, parentTaskId)
                        .set(GeminiImageTask::getResultJson, mergedJson)
                        .set(GeminiImageTask::getUpdatedAt, now);
        int n = taskMapper.update(null, uw);
        if (n != 1) {
            log.warn("套图父任务 result_json 更新未生效 parentTaskId={}", parentTaskId);
        }
    }

    /**
     * 服装套图 / 模特出图生成中：每完成一步即更新 {@code result_json}（未完成槽位为占位串），任务仍为
     * {@link GeminiImageTaskStatus#PROCESSING}。
     */
    private void writeProcessingPartialResult(Long taskId, List<String> urls, List<String> imageLabels)
            throws Exception {
        GeminiImageResultJson out = new GeminiImageResultJson();
        out.setUrls(new ArrayList<>(urls));
        out.setImageLabels(new ArrayList<>(imageLabels));
        out.setTextParts(List.of());
        updateProcessingResultJson(taskId, objectMapper.writeValueAsString(out));
    }

    /** 与调度器出图顺序一致：上身、SCENE_1…N、白底 */
    private static List<String> buildPackshotResultLabels(int sceneN) {
        List<String> labels = new ArrayList<>();
        labels.add("MODEL_WEAR");
        for (int i = 0; i < sceneN; i++) {
            labels.add("SCENE_" + (i + 1));
        }
        labels.add("ECOM_WHITE");
        return labels;
    }

    private void updateProcessingResultJson(Long taskId, String resultJson) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<GeminiImageTask> uw =
                new LambdaUpdateWrapper<GeminiImageTask>()
                        .eq(GeminiImageTask::getId, taskId)
                        .eq(GeminiImageTask::getStatus, GeminiImageTaskStatus.PROCESSING)
                        .set(GeminiImageTask::getResultJson, resultJson)
                        .set(GeminiImageTask::getUpdatedAt, now);
        int n = taskMapper.update(null, uw);
        if (n != 1) {
            log.warn("PROCESSING 任务 result_json 更新未生效 taskId={}", taskId);
        }
    }

    private void complete(Long id, String resultJson) {
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = computeGenerationDurationMs(id, now);
        LambdaUpdateWrapper<GeminiImageTask> uw =
                new LambdaUpdateWrapper<GeminiImageTask>()
                        .eq(GeminiImageTask::getId, id)
                        .eq(GeminiImageTask::getStatus, GeminiImageTaskStatus.PROCESSING)
                        .set(GeminiImageTask::getStatus, GeminiImageTaskStatus.COMPLETED)
                        .set(GeminiImageTask::getResultJson, resultJson)
                        .set(GeminiImageTask::getErrorMsg, null)
                        .set(GeminiImageTask::getGenerationDurationMs, durationMs)
                        .set(GeminiImageTask::getCompletedAt, now)
                        .set(GeminiImageTask::getUpdatedAt, now);
        taskMapper.update(null, uw);
        log.info("Gemini 任务完成 taskId={}, generationDurationMs={}", id, durationMs);
        deductLocalEditCreditsIfNeeded(id);
    }

    /**
     * 与试穿一致：仅成功出图后扣点；{@link GeminiImageTaskType#EDIT} 或 {@link
     * GeminiImageTaskType#CLOTHING_PACKSHOT} / {@link GeminiImageTaskType#CLOTHING_PRODUCT_SUITE} 且 {@code
     * consume_credits > 0}。
     */
    private void deductLocalEditCreditsIfNeeded(Long taskId) {
        GeminiImageTask row = taskMapper.selectById(taskId);
        if (row == null) {
            return;
        }
        String tt = row.getTaskType();
        if (!GeminiImageTaskType.EDIT.equals(tt)
                && !GeminiImageTaskType.CLOTHING_PACKSHOT.equals(tt)
                && !GeminiImageTaskType.CLOTHING_PRODUCT_SUITE.equals(tt)
                && !GeminiImageTaskType.PRODUCT_RETOUCH.equals(tt)
                && !GeminiImageTaskType.PORTRAIT_STUDIO.equals(tt)) {
            return;
        }
        int credits = row.getConsumeCredits() != null ? row.getConsumeCredits() : 0;
        Long uid = row.getUserId();
        applyWalletLocalEditCredits(uid, credits, taskId);
    }

    /** 按指定点数扣减（幂等标记 credits_deducted_at，原子扣钱包） */
    private void applyWalletLocalEditCredits(Long userId, int credits, Long taskId) {
        if (credits <= 0 || userId == null || userId <= 0 || taskId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<GeminiImageTask> mark =
                new LambdaUpdateWrapper<GeminiImageTask>()
                        .eq(GeminiImageTask::getId, taskId)
                        .eq(GeminiImageTask::getStatus, GeminiImageTaskStatus.COMPLETED)
                        .isNull(GeminiImageTask::getCreditsDeductedAt)
                        .set(GeminiImageTask::getCreditsDeductedAt, now)
                        .set(GeminiImageTask::getUpdatedAt, now);
        if (taskMapper.update(null, mark) != 1) {
            return;
        }
        if (!walletDeductionService.deductIfEnough(userId, credits, "gemini", taskId)) {
            LambdaUpdateWrapper<GeminiImageTask> rollback =
                    new LambdaUpdateWrapper<GeminiImageTask>()
                            .eq(GeminiImageTask::getId, taskId)
                            .eq(GeminiImageTask::getStatus, GeminiImageTaskStatus.COMPLETED)
                            .set(GeminiImageTask::getCreditsDeductedAt, null)
                            .set(GeminiImageTask::getUpdatedAt, LocalDateTime.now());
            taskMapper.update(null, rollback);
        }
    }

    private void fail(Long id, String message) {
        String msg = message == null ? "未知错误" : message;
        if (msg.length() > ERROR_MAX_LEN) {
            msg = msg.substring(0, ERROR_MAX_LEN);
        }
        GeminiImageTask row = taskMapper.selectById(id);
        boolean suiteSlotRegen = false;
        String preservedResult = null;
        String cleanPayload = null;
        if (row != null
                && GeminiImageTaskType.CLOTHING_PRODUCT_SUITE.equals(row.getTaskType())
                && StringUtils.hasText(row.getPayloadJson())) {
            try {
                GeminiClothingSuiteTaskPayload pay =
                        objectMapper.readValue(row.getPayloadJson(), GeminiClothingSuiteTaskPayload.class);
                if (pay.getRegenerateSlotIndex() != null && pay.getParentTaskId() == null) {
                    suiteSlotRegen = true;
                    preservedResult = row.getResultJson();
                    pay.setRegenerateSlotIndex(null);
                    pay.setParentTaskId(null);
                    // regenerateSlotQueue 保留，便于失败后继续排队或重试
                    cleanPayload = objectMapper.writeValueAsString(pay);
                }
            } catch (Exception e) {
                log.warn("fail 解析套图 payload 失败 taskId={}", id, e);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = computeGenerationDurationMs(id, now);
        LambdaUpdateWrapper<GeminiImageTask> uw =
                new LambdaUpdateWrapper<GeminiImageTask>()
                        .eq(GeminiImageTask::getId, id)
                        .eq(GeminiImageTask::getStatus, GeminiImageTaskStatus.PROCESSING)
                        .set(GeminiImageTask::getStatus, GeminiImageTaskStatus.FAILED)
                        .set(GeminiImageTask::getErrorMsg, msg)
                        .set(GeminiImageTask::getGenerationDurationMs, durationMs)
                        .set(GeminiImageTask::getCompletedAt, now)
                        .set(GeminiImageTask::getUpdatedAt, now);
        if (suiteSlotRegen && StringUtils.hasText(preservedResult)) {
            uw.set(GeminiImageTask::getResultJson, preservedResult);
            uw.set(GeminiImageTask::getPayloadJson, cleanPayload);
            uw.set(GeminiImageTask::getConsumeCredits, 0);
        } else {
            uw.set(GeminiImageTask::getResultJson, null);
        }
        int n = taskMapper.update(null, uw);
        if (n == 0) {
            log.warn("Gemini 任务标记失败未生效 taskId={}", id);
        } else {
            log.info("Gemini 任务失败 taskId={}, generationDurationMs={}, msg={}", id, durationMs, msg);
        }
    }

    /**
     * 单张合并写入 result_json 后：扣点；若 {@link GeminiClothingSuiteTaskPayload#getRegenerateSlotQueue()} 非空则立刻回到
     * PENDING 执行下一张，避免出现「先 COMPLETED 再排队」的短暂状态。
     */
    private void finishSuiteSlotMerge(Long taskId, String mergedJson) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        GeminiImageTask row = taskMapper.selectById(taskId);
        if (row == null || !StringUtils.hasText(row.getPayloadJson())) {
            return;
        }
        int creditsForThisSlot = row.getConsumeCredits() != null ? row.getConsumeCredits() : 0;
        Long userIdForDeduction = row.getUserId();
        Long durationMs = computeGenerationDurationMs(taskId, now);
        GeminiClothingSuiteTaskPayload p =
                objectMapper.readValue(row.getPayloadJson(), GeminiClothingSuiteTaskPayload.class);
        List<Integer> q = p.getRegenerateSlotQueue();
        if (q == null) {
            q = new ArrayList<>();
        }
        boolean hasMore = !q.isEmpty();
        Integer nextSlot = null;
        if (hasMore) {
            nextSlot = q.remove(0);
        }
        if (hasMore) {
            p.setRegenerateSlotIndex(nextSlot);
            p.setRegenerateSlotQueue(q.isEmpty() ? null : q);
        } else {
            p.setRegenerateSlotIndex(null);
            p.setParentTaskId(null);
            p.setRegenerateSlotQueue(null);
        }
        String payloadJson = objectMapper.writeValueAsString(p);
        int slotCost = Math.max(0, geminiApiProperties.computeClothingSuiteSlotRegenerateCredits());
        LambdaUpdateWrapper<GeminiImageTask> uw =
                new LambdaUpdateWrapper<GeminiImageTask>()
                        .eq(GeminiImageTask::getId, taskId)
                        .eq(GeminiImageTask::getStatus, GeminiImageTaskStatus.PROCESSING)
                        .set(GeminiImageTask::getResultJson, mergedJson)
                        .set(GeminiImageTask::getGenerationDurationMs, durationMs)
                        .set(GeminiImageTask::getUpdatedAt, now);
        if (hasMore) {
            uw.set(GeminiImageTask::getStatus, GeminiImageTaskStatus.PENDING)
                    .set(GeminiImageTask::getPayloadJson, payloadJson)
                    .set(GeminiImageTask::getConsumeCredits, slotCost)
                    .set(GeminiImageTask::getErrorMsg, null)
                    .set(GeminiImageTask::getCompletedAt, null)
                    .set(GeminiImageTask::getProcessingStartedAt, null);
        } else {
            uw.set(GeminiImageTask::getStatus, GeminiImageTaskStatus.COMPLETED)
                    .set(GeminiImageTask::getPayloadJson, payloadJson)
                    .set(GeminiImageTask::getErrorMsg, null)
                    .set(GeminiImageTask::getCompletedAt, now);
        }
        int n = taskMapper.update(null, uw);
        if (n != 1) {
            log.warn("finishSuiteSlotMerge 更新未生效 taskId={}", taskId);
            return;
        }
        applyWalletLocalEditCredits(userIdForDeduction, creditsForThisSlot, taskId);
        log.info(
                "服装套图单张完成 taskId={}, hasMoreQueued={}, generationDurationMs={}",
                taskId,
                hasMore,
                durationMs);
    }

    /** 从 processing_started_at 到结束时刻的毫秒数，供统计与前端进度分母参考 */
    private Long computeGenerationDurationMs(Long id, LocalDateTime end) {
        GeminiImageTask row = taskMapper.selectById(id);
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
