package com.fashtry.backend.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.fashtry.backend.bean.vo.GeminiImageEditHistoryItemVO;
import com.fashtry.backend.bean.vo.GeminiPackshotHistoryItemVO;
import com.fashtry.backend.bean.vo.GeminiImageResultJson;
import com.fashtry.backend.bean.vo.GeminiImageResultVO;
import com.fashtry.backend.bean.vo.GeminiTaskQueryVO;
import com.fashtry.backend.bean.vo.GeminiTaskSubmitVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.common.constants.ClientSafeMessages;
import com.fashtry.backend.common.constants.GeminiImageTaskStatus;
import com.fashtry.backend.common.constants.GeminiImageTaskType;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.config.GeminiApiProperties;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.bean.db.UserWallet;
import com.fashtry.backend.mapper.GeminiImageTaskMapper;
import com.fashtry.backend.mapper.UserWalletMapper;
import com.fashtry.backend.service.storage.CosService;
import com.fashtry.backend.service.storage.UserUploadRecordService;
import com.fashtry.backend.utils.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gemini 异步任务：提交时仅入库；执行与 COS 上传由 {@link com.fashtry.backend.task.GeminiImageTaskScheduler} 完成。
 */
@Service
public class GeminiImageTaskService {

    private final GeminiImageTaskMapper taskMapper;
    private final CosService cosService;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final GeminiImageService geminiImageService;
    private final UserWalletMapper userWalletMapper;
    private final GeminiApiProperties geminiApiProperties;
    private final UserUploadRecordService userUploadRecordService;

    public GeminiImageTaskService(
            GeminiImageTaskMapper taskMapper,
            CosService cosService,
            ObjectMapper objectMapper,
            SnowflakeIdGenerator idGenerator,
            GeminiImageService geminiImageService,
            UserWalletMapper userWalletMapper,
            GeminiApiProperties geminiApiProperties,
            UserUploadRecordService userUploadRecordService) {
        this.taskMapper = taskMapper;
        this.cosService = cosService;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
        this.geminiImageService = geminiImageService;
        this.userWalletMapper = userWalletMapper;
        this.geminiApiProperties = geminiApiProperties;
        this.userUploadRecordService = userUploadRecordService;
    }

    public GeminiTaskSubmitVO submitGenerate(GeminiGenerateRequest req) {
        assertGeminiReady();
        GeminiTextToImageTaskPayload p = new GeminiTextToImageTaskPayload();
        p.setPrompt(req.getPrompt());
        p.setModelId(req.getModelId());
        p.setAspectRatio(req.getAspectRatio());
        p.setImageSize(req.getImageSize());
        return insertTask(GeminiImageTaskType.TEXT_TO_IMAGE, p, null, 0);
    }

    /**
     * PC「局部改款」：同源图 + 文案，可选蒙版；任务归属 {@code userId}。
     */
    public GeminiTaskSubmitVO submitLocalEdit(
            MultipartFile file,
            MultipartFile mask,
            MultipartFile reference,
            String instruction,
            String modelId,
            String aspectRatio,
            String imageSize,
            Long userId) {
        assertGeminiReady();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请上传图片文件");
        }
        if (!StringUtils.hasText(instruction)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "instruction 不能为空");
        }
        int cost = Math.max(0, geminiApiProperties.getLocalEditConsumeCredits());
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请先登录");
        }
        if (cost > 0) {
            UserWallet wallet = userWalletMapper.selectById(userId);
            int total = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
            int frozen = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
            if (total - frozen < cost) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }
        String sourceUrl = cosService.uploadBusiness(file);
        String maskUrl = null;
        if (mask != null && !mask.isEmpty()) {
            maskUrl = cosService.uploadBusiness(mask);
        }
        String referenceUrl = null;
        if (reference != null && !reference.isEmpty()) {
            referenceUrl = cosService.uploadBusiness(reference);
        }
        GeminiEditTaskPayload p = new GeminiEditTaskPayload();
        p.setSourceCosUrl(sourceUrl);
        p.setMaskCosUrl(maskUrl);
        p.setReferenceCosUrl(referenceUrl);
        p.setInstruction(instruction);
        p.setModelId(modelId);
        p.setAspectRatio(aspectRatio);
        p.setImageSize(imageSize);
        return insertTask(GeminiImageTaskType.EDIT, p, userId, cost);
    }

    /**
     * 模特出图：平铺图 → 模特 → 多场景 → 白底；成功完成后按步数 × {@link
     * GeminiApiProperties#getPackshotCreditsPerStep()} 扣点（见 {@link
     * GeminiApiProperties#computePackshotConsumeCredits(int)}）。
     */
    public GeminiTaskSubmitVO submitClothingPackshot(
            MultipartFile file,
            String artDirection,
            String modelGender,
            String ageGroup,
            String bodyType,
            String modelStyleNote,
            String faceMode,
            String facePreset,
            MultipartFile faceReference,
            Integer sceneVariantCount,
            String framingMode,
            String modelId,
            String aspectRatio,
            String imageSize,
            Long userId) {
        assertGeminiReady();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请上传服装平铺图");
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请先登录");
        }
        int scenes = sceneVariantCount != null ? sceneVariantCount : 4;
        if (scenes < 2 || scenes > 8) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "场景数量需在 2～8 之间");
        }
        String fm = ClothingPackshotPrompts.normalizeFaceMode(faceMode);
        if (faceReference != null && !faceReference.isEmpty()) {
            fm = ClothingPackshotPrompts.FACE_MODE_UPLOAD;
        }
        if (ClothingPackshotPrompts.FACE_MODE_UPLOAD.equals(fm)
                && (faceReference == null || faceReference.isEmpty())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请上传参考脸图，或改用预设面孔");
        }
        int cost = geminiApiProperties.computePackshotConsumeCredits(scenes);
        if (cost > 0) {
            UserWallet wallet = userWalletMapper.selectById(userId);
            int total = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
            int frozen = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
            if (total - frozen < cost) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }
        String sourceUrl = cosService.uploadBusiness(file);
        String faceRefUrl = null;
        if (faceReference != null && !faceReference.isEmpty()) {
            faceRefUrl = cosService.uploadBusiness(faceReference);
        }
        String art = ClothingPackshotPrompts.normalizeArtDirection(artDirection);
        String gender = ClothingPackshotPrompts.normalizeModelGender(modelGender);
        // 与 Gemini 3.x 文档一致：显式竖版比例 + 1K，利于全身电商主图；未传时使用默认（仍可在接口覆盖）
        String ar = StringUtils.hasText(aspectRatio) ? aspectRatio.trim() : "3:4";
        String sz = StringUtils.hasText(imageSize) ? imageSize.trim() : "1K";
        GeminiPackshotTaskPayload p = new GeminiPackshotTaskPayload();
        p.setSourceCosUrl(sourceUrl);
        p.setArtDirection(art);
        p.setModelGender(gender);
        p.setAgeGroup(ClothingPackshotPrompts.normalizeAgeGroup(ageGroup));
        p.setBodyType(ClothingPackshotPrompts.normalizeBodyType(bodyType));
        p.setModelStyleNote(modelStyleNote);
        p.setFaceMode(fm);
        p.setFacePreset(ClothingPackshotPrompts.normalizeFacePreset(facePreset));
        p.setFaceReferenceCosUrl(faceRefUrl);
        p.setFramingMode(ClothingPackshotPrompts.normalizeFramingMode(framingMode));
        p.setSceneVariantCount(scenes);
        p.setModelId(modelId);
        p.setAspectRatio(ar);
        p.setImageSize(sz);
        return insertTask(GeminiImageTaskType.CLOTHING_PACKSHOT, p, userId, cost);
    }

    /**
     * 服装电商套图：固定 10 张同源编辑；扣点见 {@link GeminiApiProperties#computeClothingSuiteConsumeCredits()}。
     */
    public GeminiTaskSubmitVO submitClothingProductSuite(
            MultipartFile file,
            String modelId,
            String aspectRatio,
            String imageSize,
            String outputLanguage,
            String modelGender,
            Long userId) {
        return submitClothingProductSuite(
                file, modelId, aspectRatio, imageSize, outputLanguage, modelGender, null, null, userId);
    }

    public GeminiTaskSubmitVO submitClothingProductSuite(
            MultipartFile file,
            String modelId,
            String aspectRatio,
            String imageSize,
            String outputLanguage,
            String modelGender,
            String productBrief,
            String marketplace,
            Long userId) {
        assertGeminiReady();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请上传服装图");
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请先登录");
        }
        int cost = geminiApiProperties.computeClothingSuiteConsumeCredits();
        if (cost > 0) {
            UserWallet wallet = userWalletMapper.selectById(userId);
            int total = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
            int frozen = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
            if (total - frozen < cost) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }
        String sourceUrl = cosService.uploadBusiness(file);
        String ar = StringUtils.hasText(aspectRatio) ? aspectRatio.trim() : "3:4";
        String sz = StringUtils.hasText(imageSize) ? imageSize.trim() : "1K";
        GeminiClothingSuiteTaskPayload p = new GeminiClothingSuiteTaskPayload();
        p.setSourceCosUrl(sourceUrl);
        p.setModelId(modelId);
        p.setAspectRatio(ar);
        p.setImageSize(sz);
        p.setOutputLanguage(ClothingProductSuitePrompts.normalizeOutputLanguage(outputLanguage));
        p.setModelGender(ClothingPackshotPrompts.normalizeModelGender(modelGender));
        p.setProductBrief(ClothingProductSuitePrompts.normalizeProductBrief(productBrief));
        p.setMarketplace(ClothingProductSuitePrompts.normalizeMarketplace(marketplace));
        return insertTask(GeminiImageTaskType.CLOTHING_PRODUCT_SUITE, p, userId, cost);
    }

    /**
     * 服装套图「AI 帮写」：看服装图写卖点草稿，同步返回，不扣点、不落任务。
     */
    public String writeClothingSuiteAiBrief(
            MultipartFile file,
            String outputLanguage,
            String marketplace,
            String modelGender,
            Long userId) {
        assertGeminiReady();
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请先登录");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请先上传服装图");
        }
        if (file.getSize() > 12L * 1024 * 1024) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "单张图片请小于 12MB");
        }
        String ct = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (StringUtils.hasText(ct) && !ct.startsWith("image/")) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "仅支持图片文件");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "读取图片失败");
        }
        String mime = StringUtils.hasText(ct) ? ct : "image/jpeg";
        String mid =
                StringUtils.hasText(geminiApiProperties.getSuiteBriefModelId())
                        ? geminiApiProperties.getSuiteBriefModelId().trim()
                        : "gemini-3.8-flash";
        String instruction =
                ClothingProductSuitePrompts.aiBriefInstruction(outputLanguage, marketplace, modelGender);
        GeminiImageResultVO raw = geminiImageService.generateTextFromImage(bytes, mime, instruction, mid);
        if (raw == null || raw.getTextParts() == null || raw.getTextParts().isEmpty()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "AI 帮写未返回文案，请稍后重试");
        }
        String brief = ClothingProductSuitePrompts.sanitizeAiBriefForForm(String.join("", raw.getTextParts()));
        if (!StringUtils.hasText(brief)) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "AI 帮写未返回文案，请稍后重试");
        }
        return brief;
    }

    /**
     * 服装提取：多图批量，每图出一张提取图；扣点 = 张数 ×
     * {@link GeminiApiProperties#computeProductRetouchConsumeCredits(int)}。
     */
    public GeminiTaskSubmitVO submitProductRetouch(
            MultipartFile[] files,
            String backgroundColor,
            String aspectRatio,
            String garmentScope,
            String shootHint,
            String cropPreference,
            String imageSize,
            Long userId) {
        assertGeminiReady();
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请先登录");
        }
        if (files == null || files.length == 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请至少上传一张图片");
        }
        if (files.length > GeminiApiProperties.PRODUCT_RETOUCH_MAX_FILES) {
            throw new BusinessException(
                    ResultCode.PARAM_INVALID, "单次最多 " + GeminiApiProperties.PRODUCT_RETOUCH_MAX_FILES + " 张");
        }
        long maxBytes = 12L * 1024 * 1024;
        List<String> cosUrls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (file.getSize() > maxBytes) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "单张图片请小于 12MB");
            }
            String ct = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
            if (StringUtils.hasText(ct) && !ct.startsWith("image/")) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "仅支持图片文件");
            }
            cosUrls.add(cosService.uploadBusiness(file));
        }
        if (cosUrls.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请至少上传一张有效图片");
        }
        int cost = geminiApiProperties.computeProductRetouchConsumeCredits(cosUrls.size());
        if (cost > 0) {
            UserWallet wallet = userWalletMapper.selectById(userId);
            int total = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
            int frozen = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
            if (total - frozen < cost) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }
        String ar = StringUtils.hasText(aspectRatio) ? aspectRatio.trim() : "3:4";
        String sz = StringUtils.hasText(imageSize) ? imageSize.trim() : "1K";
        String sh = ProductRetouchPrompts.normalizeShootHint(shootHint);
        String gs = ProductRetouchPrompts.normalizeGarmentScope(garmentScope);
        GeminiProductRetouchTaskPayload p = new GeminiProductRetouchTaskPayload();
        p.setSourceCosUrls(cosUrls);
        p.setBackgroundColor(backgroundColor);
        p.setAspectRatio(ar);
        p.setGarmentScope(gs);
        p.setShootHint(sh);
        p.setCropPreference(cropPreference);
        p.setImageSize(sz);
        return insertTask(GeminiImageTaskType.PRODUCT_RETOUCH, p, userId, cost);
    }

    /**
     * 对已完成「整套」服装提取中的某一格单张重生成：新建子任务（历史列表不展示）；成功后将新图合并进父任务
     * {@code result_json}。扣点 = {@link GeminiApiProperties#computeProductRetouchConsumeCredits(int)}({@code 1})。
     */
    public GeminiTaskSubmitVO submitProductRetouchSlotRegenerate(Long parentTaskId, int slotIndex, Long userId) {
        assertGeminiReady();
        if (parentTaskId == null || userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "参数无效");
        }
        GeminiImageTask parent = taskMapper.selectById(parentTaskId);
        if (parent == null || parent.getUserId() == null || !parent.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        if (!GeminiImageTaskType.PRODUCT_RETOUCH.equals(parent.getTaskType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        Integer st = parent.getStatus();
        if (st != null && (st == GeminiImageTaskStatus.PENDING || st == GeminiImageTaskStatus.PROCESSING)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "父任务进行中，请稍后再试");
        }
        if (st == null || (st != GeminiImageTaskStatus.COMPLETED && st != GeminiImageTaskStatus.FAILED)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请待提取任务完成后再重试单张");
        }
        GeminiProductRetouchTaskPayload parentPay;
        try {
            parentPay = objectMapper.readValue(parent.getPayloadJson(), GeminiProductRetouchTaskPayload.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "任务参数无效");
        }
        if (parentPay.getSourceCosUrls() == null || parentPay.getSourceCosUrls().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "父任务缺少原图列表");
        }
        int n = parentPay.getSourceCosUrls().size();
        if (slotIndex < 0 || slotIndex >= n) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "图片索引无效");
        }
        if (!StringUtils.hasText(parent.getResultJson())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "整套提取未完成，无法重试单张");
        }
        try {
            GeminiImageResultJson rj = objectMapper.readValue(parent.getResultJson(), GeminiImageResultJson.class);
            if (rj.getUrls() == null || rj.getUrls().size() < n) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "整套提取未完成，无法重试单张");
            }
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "整套提取未完成，无法重试单张");
        }
        if (hasPendingProductRetouchSlotChild(userId, parentTaskId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "该任务尚有单张重生成进行中，请稍后再试");
        }
        int cost = geminiApiProperties.computeProductRetouchConsumeCredits(1);
        if (cost > 0) {
            UserWallet wallet = userWalletMapper.selectById(userId);
            int total = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
            int frozen = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
            if (total - frozen < cost) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }
        GeminiProductRetouchTaskPayload pNew = new GeminiProductRetouchTaskPayload();
        pNew.setSourceCosUrls(new ArrayList<>(parentPay.getSourceCosUrls()));
        pNew.setBackgroundColor(parentPay.getBackgroundColor());
        pNew.setAspectRatio(parentPay.getAspectRatio());
        pNew.setGarmentScope(ProductRetouchPrompts.normalizeGarmentScope(parentPay.getGarmentScope()));
        pNew.setShootHint(parentPay.getShootHint());
        pNew.setCropPreference(parentPay.getCropPreference());
        pNew.setImageSize(parentPay.getImageSize());
        pNew.setRegenerateSlotIndex(slotIndex);
        pNew.setParentTaskId(parentTaskId);
        GeminiTaskSubmitVO sub = insertTask(GeminiImageTaskType.PRODUCT_RETOUCH, pNew, userId, cost);
        return new GeminiTaskSubmitVO(sub.getTaskId(), sub.getConsumeCredits(), parentTaskId);
    }

    /**
     * AI 写真：1～3 张参考自拍 + 风格 + 妆容 + 生成张数；扣点 = 张数 ×
     * {@link GeminiApiProperties#computePortraitStudioConsumeCredits(int)}。
     */
    public GeminiTaskSubmitVO submitPortraitStudio(
            MultipartFile[] files,
            String stylePreset,
            String makeupPreset,
            String outfitMode,
            Integer outputCount,
            String aspectRatio,
            String imageSize,
            String modelId,
            Long userId) {
        assertGeminiReady();
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请先登录");
        }
        if (files == null || files.length == 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请上传至少一张参考照片");
        }
        if (files.length > GeminiApiProperties.PORTRAIT_STUDIO_MAX_SOURCE_IMAGES) {
            throw new BusinessException(
                    ResultCode.PARAM_INVALID,
                    "参考照片最多 " + GeminiApiProperties.PORTRAIT_STUDIO_MAX_SOURCE_IMAGES + " 张");
        }
        List<String> uploaded = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (file.getSize() > 12L * 1024 * 1024) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "单张图片请小于 12MB");
            }
            String ct = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
            if (StringUtils.hasText(ct) && !ct.startsWith("image/")) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "仅支持图片文件");
            }
            uploaded.add(cosService.uploadBusiness(file));
        }
        if (uploaded.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请上传至少一张参考照片");
        }
        int n = outputCount != null ? outputCount : 1;
        if (n < 1 || n > GeminiApiProperties.PORTRAIT_STUDIO_MAX_OUTPUTS) {
            throw new BusinessException(
                    ResultCode.PARAM_INVALID,
                    "生成张数需在 1～" + GeminiApiProperties.PORTRAIT_STUDIO_MAX_OUTPUTS + " 之间");
        }
        int cost = geminiApiProperties.computePortraitStudioConsumeCredits(n);
        if (cost > 0) {
            UserWallet wallet = userWalletMapper.selectById(userId);
            int total = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
            int frozen = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
            if (total - frozen < cost) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }
        String ar = StringUtils.hasText(aspectRatio) ? aspectRatio.trim() : "3:4";
        String sz = StringUtils.hasText(imageSize) ? imageSize.trim() : "1K";
        String style = PortraitStudioPrompts.normalizeStylePreset(stylePreset);
        String mk = PortraitStudioPrompts.normalizeMakeupPreset(makeupPreset);
        String om = PortraitStudioPrompts.normalizeOutfitMode(outfitMode);
        GeminiPortraitStudioTaskPayload p = new GeminiPortraitStudioTaskPayload();
        p.setSourceCosUrls(uploaded);
        p.setStylePreset(style);
        p.setMakeupPreset(mk);
        p.setOutfitMode(om);
        p.setOutputCount(n);
        p.setAspectRatio(ar);
        p.setImageSize(sz);
        p.setModelId(StringUtils.hasText(modelId) ? modelId.trim() : null);
        return insertTask(GeminiImageTaskType.PORTRAIT_STUDIO, p, userId, cost);
    }

    /**
     * 模特出图：与 {@link #submitClothingPackshot(MultipartFile, String, String, String, String, String, String, String, MultipartFile, Integer, String, String, String, String, Long)} 相同，
     * 平铺图与参考脸使用已可访问的 http(s) URL（任务执行时由调度器下载）。
     */
    public GeminiTaskSubmitVO submitClothingPackshotFromImageUrls(
            String flatImageUrl,
            String faceReferenceUrl,
            String artDirection,
            String modelGender,
            String ageGroup,
            String bodyType,
            String modelStyleNote,
            String faceMode,
            String facePreset,
            Integer sceneVariantCount,
            String framingMode,
            String modelId,
            String aspectRatio,
            String imageSize,
            Long userId) {
        assertGeminiReady();
        assertHttpSourceImageUrl(flatImageUrl);
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请先登录");
        }
        int scenes = sceneVariantCount != null ? sceneVariantCount : 4;
        if (scenes < 2 || scenes > 8) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "场景数量需在 2～8 之间");
        }
        String fm = ClothingPackshotPrompts.normalizeFaceMode(faceMode);
        String faceRef = StringUtils.hasText(faceReferenceUrl) ? faceReferenceUrl.trim() : null;
        if (StringUtils.hasText(faceRef)) {
            assertHttpSourceImageUrl(faceRef);
            fm = ClothingPackshotPrompts.FACE_MODE_UPLOAD;
        }
        if (ClothingPackshotPrompts.FACE_MODE_UPLOAD.equals(fm) && !StringUtils.hasText(faceRef)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请提供参考脸图 URL，或改用预设面孔");
        }
        int cost = geminiApiProperties.computePackshotConsumeCredits(scenes);
        if (cost > 0) {
            UserWallet wallet = userWalletMapper.selectById(userId);
            int total = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
            int frozen = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
            if (total - frozen < cost) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }
        String art = ClothingPackshotPrompts.normalizeArtDirection(artDirection);
        String gender = ClothingPackshotPrompts.normalizeModelGender(modelGender);
        String ar = StringUtils.hasText(aspectRatio) ? aspectRatio.trim() : "3:4";
        String sz = StringUtils.hasText(imageSize) ? imageSize.trim() : "1K";
        GeminiPackshotTaskPayload p = new GeminiPackshotTaskPayload();
        p.setSourceCosUrl(flatImageUrl.trim());
        p.setArtDirection(art);
        p.setModelGender(gender);
        p.setAgeGroup(ClothingPackshotPrompts.normalizeAgeGroup(ageGroup));
        p.setBodyType(ClothingPackshotPrompts.normalizeBodyType(bodyType));
        p.setModelStyleNote(modelStyleNote);
        p.setFaceMode(fm);
        p.setFacePreset(ClothingPackshotPrompts.normalizeFacePreset(facePreset));
        p.setFaceReferenceCosUrl(faceRef);
        p.setFramingMode(ClothingPackshotPrompts.normalizeFramingMode(framingMode));
        p.setSceneVariantCount(scenes);
        p.setModelId(modelId);
        p.setAspectRatio(ar);
        p.setImageSize(sz);
        return insertTask(GeminiImageTaskType.CLOTHING_PACKSHOT, p, userId, cost);
    }

    /**
     * 服装电商套图：与 {@link #submitClothingProductSuite(MultipartFile, String, String, String, String, String, Long)} 相同，源图为 http(s) URL。
     */
    public GeminiTaskSubmitVO submitClothingProductSuiteFromImageUrl(
            String garmentImageUrl,
            String modelId,
            String aspectRatio,
            String imageSize,
            String outputLanguage,
            String modelGender,
            Long userId) {
        assertGeminiReady();
        assertHttpSourceImageUrl(garmentImageUrl);
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请先登录");
        }
        int cost = geminiApiProperties.computeClothingSuiteConsumeCredits();
        if (cost > 0) {
            UserWallet wallet = userWalletMapper.selectById(userId);
            int total = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
            int frozen = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
            if (total - frozen < cost) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }
        String ar = StringUtils.hasText(aspectRatio) ? aspectRatio.trim() : "3:4";
        String sz = StringUtils.hasText(imageSize) ? imageSize.trim() : "1K";
        GeminiClothingSuiteTaskPayload p = new GeminiClothingSuiteTaskPayload();
        p.setSourceCosUrl(garmentImageUrl.trim());
        p.setModelId(modelId);
        p.setAspectRatio(ar);
        p.setImageSize(sz);
        p.setOutputLanguage(ClothingProductSuitePrompts.normalizeOutputLanguage(outputLanguage));
        p.setModelGender(ClothingPackshotPrompts.normalizeModelGender(modelGender));
        p.setProductBrief("");
        p.setMarketplace(ClothingProductSuitePrompts.normalizeMarketplace(null));
        return insertTask(GeminiImageTaskType.CLOTHING_PRODUCT_SUITE, p, userId, cost);
    }

    /**
     * 服装提取：与 {@link #submitProductRetouch(MultipartFile[], String, String, String, String, String, String, Long)} 相同，源图为 http(s) URL 列表。
     */
    public GeminiTaskSubmitVO submitProductRetouchFromImageUrls(
            List<String> imageUrls,
            String backgroundColor,
            String aspectRatio,
            String garmentScope,
            String shootHint,
            String cropPreference,
            String imageSize,
            Long userId) {
        assertGeminiReady();
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请先登录");
        }
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请至少提供一张图片 URL");
        }
        if (imageUrls.size() > GeminiApiProperties.PRODUCT_RETOUCH_MAX_FILES) {
            throw new BusinessException(
                    ResultCode.PARAM_INVALID, "单次最多 " + GeminiApiProperties.PRODUCT_RETOUCH_MAX_FILES + " 张");
        }
        List<String> cosUrls = new ArrayList<>();
        for (String u : imageUrls) {
            if (!StringUtils.hasText(u)) {
                continue;
            }
            assertHttpSourceImageUrl(u);
            cosUrls.add(u.trim());
        }
        if (cosUrls.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请至少提供一张有效图片 URL");
        }
        int cost = geminiApiProperties.computeProductRetouchConsumeCredits(cosUrls.size());
        if (cost > 0) {
            UserWallet wallet = userWalletMapper.selectById(userId);
            int total = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
            int frozen = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
            if (total - frozen < cost) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }
        String ar = StringUtils.hasText(aspectRatio) ? aspectRatio.trim() : "3:4";
        String sz = StringUtils.hasText(imageSize) ? imageSize.trim() : "1K";
        String sh = ProductRetouchPrompts.normalizeShootHint(shootHint);
        String gs = ProductRetouchPrompts.normalizeGarmentScope(garmentScope);
        GeminiProductRetouchTaskPayload p = new GeminiProductRetouchTaskPayload();
        p.setSourceCosUrls(cosUrls);
        p.setBackgroundColor(backgroundColor);
        p.setAspectRatio(ar);
        p.setGarmentScope(gs);
        p.setShootHint(sh);
        p.setCropPreference(cropPreference);
        p.setImageSize(sz);
        return insertTask(GeminiImageTaskType.PRODUCT_RETOUCH, p, userId, cost);
    }

    /**
     * AI 写真：与 {@link #submitPortraitStudio(MultipartFile[], String, String, String, Integer, String, String, String, Long)} 相同，参考图为 http(s) URL 列表。
     */
    public GeminiTaskSubmitVO submitPortraitStudioFromImageUrls(
            List<String> imageUrls,
            String stylePreset,
            String makeupPreset,
            String outfitMode,
            Integer outputCount,
            String aspectRatio,
            String imageSize,
            String modelId,
            Long userId) {
        assertGeminiReady();
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请先登录");
        }
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请至少提供一张参考图 URL");
        }
        if (imageUrls.size() > GeminiApiProperties.PORTRAIT_STUDIO_MAX_SOURCE_IMAGES) {
            throw new BusinessException(
                    ResultCode.PARAM_INVALID,
                    "参考照片最多 " + GeminiApiProperties.PORTRAIT_STUDIO_MAX_SOURCE_IMAGES + " 张");
        }
        List<String> uploaded = new ArrayList<>();
        for (String u : imageUrls) {
            if (!StringUtils.hasText(u)) {
                continue;
            }
            assertHttpSourceImageUrl(u);
            uploaded.add(u.trim());
        }
        if (uploaded.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请至少提供一张有效参考图 URL");
        }
        int n = outputCount != null ? outputCount : 1;
        if (n < 1 || n > GeminiApiProperties.PORTRAIT_STUDIO_MAX_OUTPUTS) {
            throw new BusinessException(
                    ResultCode.PARAM_INVALID,
                    "生成张数需在 1～" + GeminiApiProperties.PORTRAIT_STUDIO_MAX_OUTPUTS + " 之间");
        }
        int cost = geminiApiProperties.computePortraitStudioConsumeCredits(n);
        if (cost > 0) {
            UserWallet wallet = userWalletMapper.selectById(userId);
            int total = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
            int frozen = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
            if (total - frozen < cost) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }
        String ar = StringUtils.hasText(aspectRatio) ? aspectRatio.trim() : "3:4";
        String sz = StringUtils.hasText(imageSize) ? imageSize.trim() : "1K";
        String style = PortraitStudioPrompts.normalizeStylePreset(stylePreset);
        String mk = PortraitStudioPrompts.normalizeMakeupPreset(makeupPreset);
        String om = PortraitStudioPrompts.normalizeOutfitMode(outfitMode);
        GeminiPortraitStudioTaskPayload p = new GeminiPortraitStudioTaskPayload();
        p.setSourceCosUrls(uploaded);
        p.setStylePreset(style);
        p.setMakeupPreset(mk);
        p.setOutfitMode(om);
        p.setOutputCount(n);
        p.setAspectRatio(ar);
        p.setImageSize(sz);
        p.setModelId(StringUtils.hasText(modelId) ? modelId.trim() : null);
        return insertTask(GeminiImageTaskType.PORTRAIT_STUDIO, p, userId, cost);
    }

    private static void assertHttpSourceImageUrl(String url) {
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "图片 URL 不能为空");
        }
        String u = url.trim().toLowerCase();
        if (!(u.startsWith("http://") || u.startsWith("https://"))) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "图片 URL 须为 http(s) 地址");
        }
    }

    /**
     * 对已完成「整套」AI 写真任务中的某一格单张重生成：新建子任务（历史列表不展示）；成功后将新图合并进父任务
     * {@code result_json}。扣点 = {@link GeminiApiProperties#computePortraitStudioConsumeCredits(int)}({@code 1})。
     */
    public GeminiTaskSubmitVO submitPortraitStudioSlotRegenerate(Long parentTaskId, int slotIndex, Long userId) {
        assertGeminiReady();
        if (parentTaskId == null || userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "参数无效");
        }
        GeminiImageTask parent = taskMapper.selectById(parentTaskId);
        if (parent == null || parent.getUserId() == null || !parent.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        if (!GeminiImageTaskType.PORTRAIT_STUDIO.equals(parent.getTaskType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        Integer st = parent.getStatus();
        if (st != null && (st == GeminiImageTaskStatus.PENDING || st == GeminiImageTaskStatus.PROCESSING)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "父任务进行中，请稍后再试");
        }
        if (st == null || (st != GeminiImageTaskStatus.COMPLETED && st != GeminiImageTaskStatus.FAILED)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请待写真任务完成后再重试单张");
        }
        GeminiPortraitStudioTaskPayload parentPay;
        try {
            parentPay = objectMapper.readValue(parent.getPayloadJson(), GeminiPortraitStudioTaskPayload.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "任务参数无效");
        }
        int n = parentPay.getOutputCount() != null ? parentPay.getOutputCount() : 1;
        n = Math.max(1, Math.min(GeminiApiProperties.PORTRAIT_STUDIO_MAX_OUTPUTS, n));
        if (slotIndex < 0 || slotIndex >= n) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "成片索引无效");
        }
        if (!StringUtils.hasText(parent.getResultJson())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "整套写真未完成，无法重试单张");
        }
        try {
            GeminiImageResultJson rj = objectMapper.readValue(parent.getResultJson(), GeminiImageResultJson.class);
            if (rj.getUrls() == null || rj.getUrls().size() < n) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "整套写真未完成，无法重试单张");
            }
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "整套写真未完成，无法重试单张");
        }
        if (hasPendingPortraitSlotChild(userId, parentTaskId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "该任务尚有单张重生成进行中，请稍后再试");
        }
        int cost = geminiApiProperties.computePortraitStudioConsumeCredits(1);
        if (cost > 0) {
            UserWallet wallet = userWalletMapper.selectById(userId);
            int total = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
            int frozen = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
            if (total - frozen < cost) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }
        boolean hasSources =
                (parentPay.getSourceCosUrls() != null && !parentPay.getSourceCosUrls().isEmpty())
                        || StringUtils.hasText(parentPay.getSourceCosUrl());
        if (!hasSources) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "父任务缺少参考图");
        }
        GeminiPortraitStudioTaskPayload pNew = new GeminiPortraitStudioTaskPayload();
        if (parentPay.getSourceCosUrls() != null && !parentPay.getSourceCosUrls().isEmpty()) {
            pNew.setSourceCosUrls(new ArrayList<>(parentPay.getSourceCosUrls()));
        } else if (StringUtils.hasText(parentPay.getSourceCosUrl())) {
            pNew.setSourceCosUrl(parentPay.getSourceCosUrl());
        }
        pNew.setStylePreset(parentPay.getStylePreset());
        pNew.setMakeupPreset(parentPay.getMakeupPreset());
        pNew.setOutfitMode(PortraitStudioPrompts.normalizeOutfitMode(parentPay.getOutfitMode()));
        pNew.setOutputCount(n);
        pNew.setAspectRatio(parentPay.getAspectRatio());
        pNew.setImageSize(parentPay.getImageSize());
        pNew.setModelId(parentPay.getModelId());
        pNew.setRegenerateSlotIndex(slotIndex);
        pNew.setParentTaskId(parentTaskId);
        GeminiTaskSubmitVO sub = insertTask(GeminiImageTaskType.PORTRAIT_STUDIO, pNew, userId, cost);
        return new GeminiTaskSubmitVO(sub.getTaskId(), sub.getConsumeCredits(), parentTaskId);
    }

    /**
     * 对已完成「整套」服装套图任务中的某一格单张重生成：新建子任务（历史列表不展示）；成功后将新图合并进父任务
     * {@code result_json}。扣点见 {@link GeminiApiProperties#computeClothingSuiteSlotRegenerateCredits()}。
     */
    public GeminiTaskSubmitVO submitClothingSuiteSlotRegenerate(Long parentTaskId, int slotIndex, Long userId) {
        assertGeminiReady();
        if (parentTaskId == null || userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "参数无效");
        }
        if (slotIndex < 0 || slotIndex >= ClothingProductSuitePrompts.STEP_COUNT) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "步骤索引无效");
        }
        GeminiImageTask parent = taskMapper.selectById(parentTaskId);
        if (parent == null || parent.getUserId() == null || !parent.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        if (!GeminiImageTaskType.CLOTHING_PRODUCT_SUITE.equals(parent.getTaskType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        Integer st = parent.getStatus();
        if (st != null && (st == GeminiImageTaskStatus.PENDING || st == GeminiImageTaskStatus.PROCESSING)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "父任务进行中，请稍后再试");
        }
        if (st == null
                || (st != GeminiImageTaskStatus.COMPLETED && st != GeminiImageTaskStatus.FAILED)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请待整套套图完成后再重试单张");
        }
        GeminiClothingSuiteTaskPayload parentPay;
        try {
            parentPay = objectMapper.readValue(parent.getPayloadJson(), GeminiClothingSuiteTaskPayload.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "任务参数无效");
        }
        if (StringUtils.hasText(parent.getResultJson())) {
            try {
                GeminiImageResultJson rj = objectMapper.readValue(parent.getResultJson(), GeminiImageResultJson.class);
                if (rj.getUrls() == null || rj.getUrls().size() < ClothingProductSuitePrompts.STEP_COUNT) {
                    throw new BusinessException(ResultCode.PARAM_INVALID, "整套套图未完成，无法重试单张");
                }
            } catch (JsonProcessingException e) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "整套套图未完成，无法重试单张");
            }
        } else {
            throw new BusinessException(ResultCode.PARAM_INVALID, "整套套图未完成，无法重试单张");
        }
        if (hasPendingSuiteSlotChild(userId, parentTaskId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "该套图尚有单张重生成进行中，请稍后再试");
        }
        int cost = geminiApiProperties.computeClothingSuiteSlotRegenerateCredits();
        if (cost > 0) {
            UserWallet wallet = userWalletMapper.selectById(userId);
            int total = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
            int frozen = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
            if (total - frozen < cost) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }
        GeminiClothingSuiteTaskPayload pNew = new GeminiClothingSuiteTaskPayload();
        pNew.setSourceCosUrl(parentPay.getSourceCosUrl());
        pNew.setModelId(parentPay.getModelId());
        pNew.setAspectRatio(parentPay.getAspectRatio());
        pNew.setImageSize(parentPay.getImageSize());
        pNew.setOutputLanguage(parentPay.getOutputLanguage());
        pNew.setModelGender(ClothingPackshotPrompts.normalizeModelGender(parentPay.getModelGender()));
        pNew.setRegenerateSlotIndex(slotIndex);
        pNew.setParentTaskId(parentTaskId);
        GeminiTaskSubmitVO sub = insertTask(GeminiImageTaskType.CLOTHING_PRODUCT_SUITE, pNew, userId, cost);
        return new GeminiTaskSubmitVO(sub.getTaskId(), sub.getConsumeCredits(), parentTaskId);
    }

    /**
     * 对已完成「整套」模特出图任务中的某一格单张重生成：新建子任务（历史列表不展示）；成功后将新图合并进父任务
     * {@code result_json}。扣点与服装套图单张一致（{@link GeminiApiProperties#computeClothingSuiteSlotRegenerateCredits()}）。
     */
    public GeminiTaskSubmitVO submitPackshotSlotRegenerate(Long parentTaskId, int slotIndex, Long userId) {
        assertGeminiReady();
        if (parentTaskId == null || userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "参数无效");
        }
        GeminiImageTask parent = taskMapper.selectById(parentTaskId);
        if (parent == null || parent.getUserId() == null || !parent.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        if (!GeminiImageTaskType.CLOTHING_PACKSHOT.equals(parent.getTaskType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        Integer st = parent.getStatus();
        if (st != null && (st == GeminiImageTaskStatus.PENDING || st == GeminiImageTaskStatus.PROCESSING)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "父任务进行中，请稍后再试");
        }
        if (st == null
                || (st != GeminiImageTaskStatus.COMPLETED && st != GeminiImageTaskStatus.FAILED)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请待整套模特出图完成后再重试单张");
        }
        GeminiPackshotTaskPayload parentPay;
        try {
            parentPay = objectMapper.readValue(parent.getPayloadJson(), GeminiPackshotTaskPayload.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "任务参数无效");
        }
        int sceneN = parentPay.getSceneVariantCount() != null ? parentPay.getSceneVariantCount() : 4;
        int totalSlots = sceneN + 2;
        if (slotIndex < 0 || slotIndex >= totalSlots) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "步骤索引无效");
        }
        if (StringUtils.hasText(parent.getResultJson())) {
            try {
                GeminiImageResultJson rj = objectMapper.readValue(parent.getResultJson(), GeminiImageResultJson.class);
                if (rj.getUrls() == null || rj.getUrls().size() < totalSlots) {
                    throw new BusinessException(ResultCode.PARAM_INVALID, "整套出图未完成，无法重试单张");
                }
            } catch (JsonProcessingException e) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "整套出图未完成，无法重试单张");
            }
        } else {
            throw new BusinessException(ResultCode.PARAM_INVALID, "整套出图未完成，无法重试单张");
        }
        if (hasPendingPackshotSlotChild(userId, parentTaskId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "该任务尚有单张重生成进行中，请稍后再试");
        }
        int cost = geminiApiProperties.computeClothingSuiteSlotRegenerateCredits();
        if (cost > 0) {
            UserWallet wallet = userWalletMapper.selectById(userId);
            int total = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
            int frozen = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
            if (total - frozen < cost) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }
        GeminiPackshotTaskPayload pNew = new GeminiPackshotTaskPayload();
        pNew.setSourceCosUrl(parentPay.getSourceCosUrl());
        pNew.setArtDirection(parentPay.getArtDirection());
        pNew.setEcomScene(parentPay.getEcomScene());
        pNew.setModelGender(parentPay.getModelGender());
        pNew.setAgeGroup(parentPay.getAgeGroup());
        pNew.setBodyType(parentPay.getBodyType());
        pNew.setModelStyleNote(parentPay.getModelStyleNote());
        pNew.setFaceMode(parentPay.getFaceMode());
        pNew.setFacePreset(parentPay.getFacePreset());
        pNew.setFaceReferenceCosUrl(parentPay.getFaceReferenceCosUrl());
        pNew.setFramingMode(parentPay.getFramingMode());
        pNew.setSceneVariantCount(parentPay.getSceneVariantCount());
        pNew.setModelId(parentPay.getModelId());
        pNew.setAspectRatio(parentPay.getAspectRatio());
        pNew.setImageSize(parentPay.getImageSize());
        pNew.setRegenerateSlotIndex(slotIndex);
        pNew.setParentTaskId(parentTaskId);
        GeminiTaskSubmitVO sub = insertTask(GeminiImageTaskType.CLOTHING_PACKSHOT, pNew, userId, cost);
        return new GeminiTaskSubmitVO(sub.getTaskId(), sub.getConsumeCredits(), parentTaskId);
    }

    /** 是否已有指向该父任务的套图单张子任务处于 PENDING/PROCESSING */
    private boolean hasPendingSuiteSlotChild(Long userId, Long parentTaskId) {
        List<GeminiImageTask> list =
                taskMapper.selectList(
                        new LambdaQueryWrapper<GeminiImageTask>()
                                .eq(GeminiImageTask::getUserId, userId)
                                .eq(GeminiImageTask::getTaskType, GeminiImageTaskType.CLOTHING_PRODUCT_SUITE)
                                .in(
                                        GeminiImageTask::getStatus,
                                        GeminiImageTaskStatus.PENDING,
                                        GeminiImageTaskStatus.PROCESSING)
                                .last("LIMIT 200"));
        String want = String.valueOf(parentTaskId);
        for (GeminiImageTask t : list) {
            if (!StringUtils.hasText(t.getPayloadJson())) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(t.getPayloadJson());
                JsonNode p = root.get("parentTaskId");
                if (p != null && !p.isNull()) {
                    String ps = p.asText();
                    if (want.equals(ps)) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
                // skip
            }
        }
        return false;
    }

    /** 是否已有指向该父任务的模特出图单张子任务处于 PENDING/PROCESSING */
    private boolean hasPendingPackshotSlotChild(Long userId, Long parentTaskId) {
        List<GeminiImageTask> list =
                taskMapper.selectList(
                        new LambdaQueryWrapper<GeminiImageTask>()
                                .eq(GeminiImageTask::getUserId, userId)
                                .eq(GeminiImageTask::getTaskType, GeminiImageTaskType.CLOTHING_PACKSHOT)
                                .in(
                                        GeminiImageTask::getStatus,
                                        GeminiImageTaskStatus.PENDING,
                                        GeminiImageTaskStatus.PROCESSING)
                                .last("LIMIT 200"));
        String want = String.valueOf(parentTaskId);
        for (GeminiImageTask t : list) {
            if (!StringUtils.hasText(t.getPayloadJson())) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(t.getPayloadJson());
                JsonNode par = root.get("parentTaskId");
                if (par != null && !par.isNull()) {
                    if (want.equals(par.asText())) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
                // skip
            }
        }
        return false;
    }

    /** 是否已有指向该父任务的 AI 写真单张子任务处于 PENDING/PROCESSING */
    private boolean hasPendingPortraitSlotChild(Long userId, Long parentTaskId) {
        List<GeminiImageTask> list =
                taskMapper.selectList(
                        new LambdaQueryWrapper<GeminiImageTask>()
                                .eq(GeminiImageTask::getUserId, userId)
                                .eq(GeminiImageTask::getTaskType, GeminiImageTaskType.PORTRAIT_STUDIO)
                                .in(
                                        GeminiImageTask::getStatus,
                                        GeminiImageTaskStatus.PENDING,
                                        GeminiImageTaskStatus.PROCESSING)
                                .last("LIMIT 200"));
        String want = String.valueOf(parentTaskId);
        for (GeminiImageTask t : list) {
            if (!StringUtils.hasText(t.getPayloadJson())) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(t.getPayloadJson());
                JsonNode par = root.get("parentTaskId");
                if (par != null && !par.isNull() && want.equals(par.asText())) {
                    return true;
                }
            } catch (Exception ignored) {
                // skip
            }
        }
        return false;
    }

    /** 是否已有指向该父任务的服装提取单张子任务处于 PENDING/PROCESSING */
    private boolean hasPendingProductRetouchSlotChild(Long userId, Long parentTaskId) {
        List<GeminiImageTask> list =
                taskMapper.selectList(
                        new LambdaQueryWrapper<GeminiImageTask>()
                                .eq(GeminiImageTask::getUserId, userId)
                                .eq(GeminiImageTask::getTaskType, GeminiImageTaskType.PRODUCT_RETOUCH)
                                .in(
                                        GeminiImageTask::getStatus,
                                        GeminiImageTaskStatus.PENDING,
                                        GeminiImageTaskStatus.PROCESSING)
                                .last("LIMIT 200"));
        String want = String.valueOf(parentTaskId);
        for (GeminiImageTask t : list) {
            if (!StringUtils.hasText(t.getPayloadJson())) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(t.getPayloadJson());
                JsonNode par = root.get("parentTaskId");
                if (par != null && !par.isNull() && want.equals(par.asText())) {
                    return true;
                }
            } catch (Exception ignored) {
                // skip
            }
        }
        return false;
    }

    public GeminiTaskSubmitVO submitInterleavedJson(GeminiInterleavedRequest req) {
        assertGeminiReady();
        GeminiInterleavedTaskPayload p = new GeminiInterleavedTaskPayload();
        p.setPrompt(req.getPrompt());
        p.setSecondPrompt(req.getSecondPrompt());
        p.setModelId(req.getModelId());
        p.setAspectRatio(req.getAspectRatio());
        p.setImageSize(req.getImageSize());
        return insertTask(GeminiImageTaskType.INTERLEAVED, p, null, 0);
    }

    public GeminiTaskSubmitVO submitInterleavedMultipart(
            String prompt,
            String secondPrompt,
            MultipartFile[] files,
            String modelId,
            String aspectRatio,
            String imageSize) {
        assertGeminiReady();
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "prompt 不能为空");
        }
        GeminiInterleavedTaskPayload p = new GeminiInterleavedTaskPayload();
        p.setPrompt(prompt);
        p.setSecondPrompt(secondPrompt);
        p.setModelId(modelId);
        p.setAspectRatio(aspectRatio);
        p.setImageSize(imageSize);
        if (files != null && files.length > 0) {
            List<String> urls = new ArrayList<>();
            for (MultipartFile f : files) {
                if (f == null || f.isEmpty()) {
                    continue;
                }
                urls.add(cosService.uploadBusiness(f));
            }
            p.setImageCosUrls(urls);
        }
        return insertTask(GeminiImageTaskType.INTERLEAVED, p, null, 0);
    }

    public GeminiTaskQueryVO getTask(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "taskId 不能为空");
        }
        GeminiImageTask row = taskMapper.selectById(taskId);
        if (row == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        return toQueryVO(row);
    }

    /**
     * 当前用户的局部改款任务分页列表（仅 {@link GeminiImageTaskType#EDIT}），按创建时间倒序。
     */
    public PageResult<GeminiImageEditHistoryItemVO> listMyLocalEditHistory(Long userId, int page, int size) {
        if (userId == null || userId <= 0) {
            return PageResult.of(List.of(), 0, 1, 0);
        }
        int p = Math.max(1, page);
        int s = Math.min(50, Math.max(1, size));
        Page<GeminiImageTask> pg = new Page<>(p, s);
        LambdaQueryWrapper<GeminiImageTask> w =
                new LambdaQueryWrapper<GeminiImageTask>()
                        .eq(GeminiImageTask::getUserId, userId)
                        .eq(GeminiImageTask::getTaskType, GeminiImageTaskType.EDIT)
                        .orderByDesc(GeminiImageTask::getCreatedAt);
        IPage<GeminiImageTask> out = taskMapper.selectPage(pg, w);
        List<GeminiImageEditHistoryItemVO> rows =
                out.getRecords().stream().map(this::toHistoryItem).collect(Collectors.toList());
        return PageResult.of(rows, out.getTotal(), p, s);
    }

    /** 服装电商套图历史（仅 {@link GeminiImageTaskType#CLOTHING_PRODUCT_SUITE}） */
    public PageResult<GeminiPackshotHistoryItemVO> listMyClothingSuiteHistory(Long userId, int page, int size) {
        if (userId == null || userId <= 0) {
            return PageResult.of(List.of(), 0, 1, 0);
        }
        int p = Math.max(1, page);
        int s = Math.min(50, Math.max(1, size));
        // 子任务（payload.parentTaskId 非空）不在列表展示。不用 MySQL JSON 函数过滤：不同版本/类型下易误判导致父任务整页缺失；
        // 与 hasPendingSuiteSlotChild 一致在 Java 中解析 JSON。
        final int fetchCap = 5000;
        LambdaQueryWrapper<GeminiImageTask> w =
                new LambdaQueryWrapper<GeminiImageTask>()
                        .eq(GeminiImageTask::getUserId, userId)
                        .eq(GeminiImageTask::getTaskType, GeminiImageTaskType.CLOTHING_PRODUCT_SUITE)
                        .orderByDesc(GeminiImageTask::getUpdatedAt)
                        .orderByDesc(GeminiImageTask::getCreatedAt)
                        .last("LIMIT " + fetchCap);
        List<GeminiImageTask> raw = taskMapper.selectList(w);
        List<GeminiImageTask> parents =
                raw.stream().filter(this::isClothingSuiteHistoryParentRow).collect(Collectors.toList());
        long total = parents.size();
        int from = (p - 1) * s;
        if (from >= parents.size()) {
            return PageResult.of(List.of(), total, p, s);
        }
        int to = Math.min(from + s, parents.size());
        List<GeminiPackshotHistoryItemVO> rows =
                parents.subList(from, to).stream()
                        .map(this::toClothingSuiteHistoryItem)
                        .collect(Collectors.toList());
        return PageResult.of(rows, total, p, s);
    }

    /** 列表只展示「整套」父任务；单格重生成子任务带非空 parentTaskId，需排除 */
    private boolean isClothingSuiteHistoryParentRow(GeminiImageTask row) {
        if (!StringUtils.hasText(row.getPayloadJson())) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(row.getPayloadJson());
            JsonNode pid = root.get("parentTaskId");
            if (pid == null || pid.isNull()) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /** 模特出图历史（仅 {@link GeminiImageTaskType#CLOTHING_PACKSHOT}） */
    public PageResult<GeminiPackshotHistoryItemVO> listMyPackshotHistory(Long userId, int page, int size) {
        if (userId == null || userId <= 0) {
            return PageResult.of(List.of(), 0, 1, 0);
        }
        int p = Math.max(1, page);
        int s = Math.min(50, Math.max(1, size));
        final int fetchCap = 5000;
        LambdaQueryWrapper<GeminiImageTask> w =
                new LambdaQueryWrapper<GeminiImageTask>()
                        .eq(GeminiImageTask::getUserId, userId)
                        .eq(GeminiImageTask::getTaskType, GeminiImageTaskType.CLOTHING_PACKSHOT)
                        .orderByDesc(GeminiImageTask::getUpdatedAt)
                        .orderByDesc(GeminiImageTask::getCreatedAt)
                        .last("LIMIT " + fetchCap);
        List<GeminiImageTask> raw = taskMapper.selectList(w);
        List<GeminiImageTask> parents =
                raw.stream().filter(this::isPackshotHistoryParentRow).collect(Collectors.toList());
        long total = parents.size();
        int from = (p - 1) * s;
        if (from >= parents.size()) {
            return PageResult.of(List.of(), total, p, s);
        }
        int to = Math.min(from + s, parents.size());
        List<GeminiPackshotHistoryItemVO> rows =
                parents.subList(from, to).stream().map(this::toPackshotHistoryItem).collect(Collectors.toList());
        return PageResult.of(rows, total, p, s);
    }

    /** 服装提取任务历史（仅 {@link GeminiImageTaskType#PRODUCT_RETOUCH}） */
    public PageResult<GeminiPackshotHistoryItemVO> listMyProductRetouchHistory(Long userId, int page, int size) {
        if (userId == null || userId <= 0) {
            return PageResult.of(List.of(), 0, 1, 0);
        }
        int p = Math.max(1, page);
        int s = Math.min(50, Math.max(1, size));
        final int fetchCap = 5000;
        LambdaQueryWrapper<GeminiImageTask> w =
                new LambdaQueryWrapper<GeminiImageTask>()
                        .eq(GeminiImageTask::getUserId, userId)
                        .eq(GeminiImageTask::getTaskType, GeminiImageTaskType.PRODUCT_RETOUCH)
                        .orderByDesc(GeminiImageTask::getUpdatedAt)
                        .orderByDesc(GeminiImageTask::getCreatedAt)
                        .last("LIMIT " + fetchCap);
        List<GeminiImageTask> raw = taskMapper.selectList(w);
        List<GeminiImageTask> parents =
                raw.stream().filter(this::isPackshotHistoryParentRow).collect(Collectors.toList());
        long total = parents.size();
        int from = (p - 1) * s;
        if (from >= parents.size()) {
            return PageResult.of(List.of(), total, p, s);
        }
        int to = Math.min(from + s, parents.size());
        List<GeminiPackshotHistoryItemVO> rows =
                parents.subList(from, to).stream()
                        .map(this::toProductRetouchHistoryItem)
                        .collect(Collectors.toList());
        return PageResult.of(rows, total, p, s);
    }

    /** AI 写真历史（仅 {@link GeminiImageTaskType#PORTRAIT_STUDIO}） */
    public PageResult<GeminiPackshotHistoryItemVO> listMyPortraitStudioHistory(Long userId, int page, int size) {
        if (userId == null || userId <= 0) {
            return PageResult.of(List.of(), 0, 1, 0);
        }
        int p = Math.max(1, page);
        int s = Math.min(50, Math.max(1, size));
        final int fetchCap = 5000;
        LambdaQueryWrapper<GeminiImageTask> w =
                new LambdaQueryWrapper<GeminiImageTask>()
                        .eq(GeminiImageTask::getUserId, userId)
                        .eq(GeminiImageTask::getTaskType, GeminiImageTaskType.PORTRAIT_STUDIO)
                        .orderByDesc(GeminiImageTask::getUpdatedAt)
                        .orderByDesc(GeminiImageTask::getCreatedAt)
                        .last("LIMIT " + fetchCap);
        List<GeminiImageTask> raw = taskMapper.selectList(w);
        List<GeminiImageTask> parents =
                raw.stream().filter(this::isPackshotHistoryParentRow).collect(Collectors.toList());
        long total = parents.size();
        int from = (p - 1) * s;
        if (from >= parents.size()) {
            return PageResult.of(List.of(), total, p, s);
        }
        int to = Math.min(from + s, parents.size());
        List<GeminiPackshotHistoryItemVO> rowsPortrait =
                parents.subList(from, to).stream()
                        .map(this::toPortraitStudioHistoryItem)
                        .collect(Collectors.toList());
        return PageResult.of(rowsPortrait, total, p, s);
    }

    /** 列表只展示「整套」父任务；单格重生成子任务带非空 parentTaskId，需排除 */
    private boolean isPackshotHistoryParentRow(GeminiImageTask row) {
        if (!StringUtils.hasText(row.getPayloadJson())) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(row.getPayloadJson());
            JsonNode pid = root.get("parentTaskId");
            if (pid == null || pid.isNull()) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private GeminiPackshotHistoryItemVO toClothingSuiteHistoryItem(GeminiImageTask row) {
        String sourceUrl = null;
        Long parentTaskId = null;
        Integer regSlot = null;
        if (StringUtils.hasText(row.getPayloadJson())) {
            try {
                GeminiClothingSuiteTaskPayload pay =
                        objectMapper.readValue(row.getPayloadJson(), GeminiClothingSuiteTaskPayload.class);
                sourceUrl = pay.getSourceCosUrl();
                parentTaskId = pay.getParentTaskId();
                regSlot = pay.getRegenerateSlotIndex();
            } catch (JsonProcessingException ignored) {
                // keep defaults
            }
        }
        // 单张重生成排队中状态为 PENDING/PROCESSING，但 result_json 仍保留上一版成片，列表需继续展示缩略图
        List<String> outputs = Collections.emptyList();
        List<String> labels = Collections.emptyList();
        if (StringUtils.hasText(row.getResultJson())) {
            try {
                GeminiImageResultJson rj = objectMapper.readValue(row.getResultJson(), GeminiImageResultJson.class);
                if (rj.getUrls() != null && !rj.getUrls().isEmpty()) {
                    outputs = rj.getUrls();
                }
                if (rj.getImageLabels() != null && !rj.getImageLabels().isEmpty()) {
                    labels = rj.getImageLabels();
                }
            } catch (JsonProcessingException ignored) {
                outputs = Collections.emptyList();
            }
        }
        return GeminiPackshotHistoryItemVO.builder()
                .taskId(row.getId())
                .status(statusLabel(row.getStatus()))
                .createdAt(row.getCreatedAt())
                .completedAt(row.getCompletedAt())
                .sourceImageUrl(sourceUrl)
                .artDirection("")
                .outputUrls(outputs)
                .imageLabels(labels)
                .parentTaskId(parentTaskId)
                .regenerateSlotIndex(regSlot)
                .build();
    }

    private GeminiPackshotHistoryItemVO toPackshotHistoryItem(GeminiImageTask row) {
        String sourceUrl = null;
        String artDir = "";
        Long parentTaskId = null;
        Integer regSlot = null;
        if (StringUtils.hasText(row.getPayloadJson())) {
            try {
                GeminiPackshotTaskPayload pay =
                        objectMapper.readValue(row.getPayloadJson(), GeminiPackshotTaskPayload.class);
                sourceUrl = pay.getSourceCosUrl();
                artDir = pay.getArtDirection() != null ? pay.getArtDirection() : "";
                parentTaskId = pay.getParentTaskId();
                regSlot = pay.getRegenerateSlotIndex();
            } catch (JsonProcessingException ignored) {
                // keep defaults
            }
        }
        List<String> outputs = Collections.emptyList();
        List<String> labels = Collections.emptyList();
        if (StringUtils.hasText(row.getResultJson())) {
            try {
                GeminiImageResultJson rj = objectMapper.readValue(row.getResultJson(), GeminiImageResultJson.class);
                if (rj.getUrls() != null && !rj.getUrls().isEmpty()) {
                    outputs = rj.getUrls();
                }
                if (rj.getImageLabels() != null && !rj.getImageLabels().isEmpty()) {
                    labels = rj.getImageLabels();
                }
            } catch (JsonProcessingException ignored) {
                outputs = Collections.emptyList();
            }
        }
        return GeminiPackshotHistoryItemVO.builder()
                .taskId(row.getId())
                .status(statusLabel(row.getStatus()))
                .createdAt(row.getCreatedAt())
                .completedAt(row.getCompletedAt())
                .sourceImageUrl(sourceUrl)
                .artDirection(artDir)
                .outputUrls(outputs)
                .imageLabels(labels)
                .parentTaskId(parentTaskId)
                .regenerateSlotIndex(regSlot)
                .build();
    }

    private GeminiPackshotHistoryItemVO toProductRetouchHistoryItem(GeminiImageTask row) {
        String sourceUrl = null;
        String garmentScopeLabel = null;
        if (StringUtils.hasText(row.getPayloadJson())) {
            try {
                GeminiProductRetouchTaskPayload pay =
                        objectMapper.readValue(row.getPayloadJson(), GeminiProductRetouchTaskPayload.class);
                if (pay.getSourceCosUrls() != null && !pay.getSourceCosUrls().isEmpty()) {
                    sourceUrl = pay.getSourceCosUrls().get(0);
                }
                garmentScopeLabel = ProductRetouchPrompts.normalizeGarmentScope(pay.getGarmentScope());
            } catch (JsonProcessingException ignored) {
            }
        }
        List<String> outputs = Collections.emptyList();
        List<String> labels = Collections.emptyList();
        if (StringUtils.hasText(row.getResultJson())) {
            try {
                GeminiImageResultJson rj = objectMapper.readValue(row.getResultJson(), GeminiImageResultJson.class);
                if (rj.getUrls() != null && !rj.getUrls().isEmpty()) {
                    outputs = rj.getUrls();
                }
                if (rj.getImageLabels() != null && !rj.getImageLabels().isEmpty()) {
                    labels = rj.getImageLabels();
                }
            } catch (JsonProcessingException ignored) {
                outputs = Collections.emptyList();
            }
        }
        return GeminiPackshotHistoryItemVO.builder()
                .taskId(row.getId())
                .status(statusLabel(row.getStatus()))
                .createdAt(row.getCreatedAt())
                .completedAt(row.getCompletedAt())
                .sourceImageUrl(sourceUrl)
                .artDirection("")
                .garmentScope(garmentScopeLabel)
                .outputUrls(outputs)
                .imageLabels(labels)
                .parentTaskId(null)
                .regenerateSlotIndex(null)
                .build();
    }

    private GeminiPackshotHistoryItemVO toPortraitStudioHistoryItem(GeminiImageTask row) {
        List<String> sourceList = Collections.emptyList();
        String sourceUrl = null;
        String styleKey = "";
        String makeupKey = "";
        String outfitForVo = null;
        Long parentTaskId = null;
        Integer regSlot = null;
        if (StringUtils.hasText(row.getPayloadJson())) {
            try {
                GeminiPortraitStudioTaskPayload pay =
                        objectMapper.readValue(row.getPayloadJson(), GeminiPortraitStudioTaskPayload.class);
                if (pay.getSourceCosUrls() != null && !pay.getSourceCosUrls().isEmpty()) {
                    sourceList =
                            pay.getSourceCosUrls().stream()
                                    .filter(StringUtils::hasText)
                                    .collect(Collectors.toList());
                } else if (StringUtils.hasText(pay.getSourceCosUrl())) {
                    sourceList = List.of(pay.getSourceCosUrl());
                }
                if (!sourceList.isEmpty()) {
                    sourceUrl = sourceList.get(0);
                }
                if (StringUtils.hasText(pay.getStylePreset())) {
                    styleKey = pay.getStylePreset();
                }
                if (StringUtils.hasText(pay.getMakeupPreset())) {
                    makeupKey = pay.getMakeupPreset();
                }
                outfitForVo = PortraitStudioPrompts.normalizeOutfitMode(pay.getOutfitMode());
                parentTaskId = pay.getParentTaskId();
                regSlot = pay.getRegenerateSlotIndex();
            } catch (JsonProcessingException ignored) {
            }
        }
        List<String> outputs = Collections.emptyList();
        List<String> labels = Collections.emptyList();
        if (StringUtils.hasText(row.getResultJson())) {
            try {
                GeminiImageResultJson rj = objectMapper.readValue(row.getResultJson(), GeminiImageResultJson.class);
                if (rj.getUrls() != null && !rj.getUrls().isEmpty()) {
                    outputs = rj.getUrls();
                }
                if (rj.getImageLabels() != null && !rj.getImageLabels().isEmpty()) {
                    labels = rj.getImageLabels();
                }
            } catch (JsonProcessingException ignored) {
                outputs = Collections.emptyList();
            }
        }
        return GeminiPackshotHistoryItemVO.builder()
                .taskId(row.getId())
                .status(statusLabel(row.getStatus()))
                .createdAt(row.getCreatedAt())
                .completedAt(row.getCompletedAt())
                .sourceImageUrl(sourceUrl)
                .sourceImageUrls(sourceList.isEmpty() ? null : sourceList)
                .artDirection(styleKey)
                .makeupPreset(StringUtils.hasText(makeupKey) ? makeupKey : null)
                .outfitMode(outfitForVo)
                .outputUrls(outputs)
                .imageLabels(labels)
                .parentTaskId(parentTaskId)
                .regenerateSlotIndex(regSlot)
                .build();
    }

    private GeminiImageEditHistoryItemVO toHistoryItem(GeminiImageTask row) {
        String instruction = "";
        String sourceUrl = null;
        if (StringUtils.hasText(row.getPayloadJson())) {
            try {
                GeminiEditTaskPayload pay =
                        objectMapper.readValue(row.getPayloadJson(), GeminiEditTaskPayload.class);
                if (pay.getInstruction() != null) {
                    instruction = pay.getInstruction();
                }
                sourceUrl = pay.getSourceCosUrl();
            } catch (JsonProcessingException ignored) {
                // keep defaults
            }
        }
        String preview = instruction;
        if (preview.length() > 96) {
            preview = preview.substring(0, 96) + "…";
        }
        List<String> outputs = Collections.emptyList();
        if (row.getStatus() != null
                && row.getStatus() == GeminiImageTaskStatus.COMPLETED
                && StringUtils.hasText(row.getResultJson())) {
            try {
                GeminiImageResultJson rj = objectMapper.readValue(row.getResultJson(), GeminiImageResultJson.class);
                if (rj.getUrls() != null && !rj.getUrls().isEmpty()) {
                    outputs = rj.getUrls();
                }
            } catch (JsonProcessingException ignored) {
                outputs = Collections.emptyList();
            }
        }
        return GeminiImageEditHistoryItemVO.builder()
                .taskId(row.getId())
                .status(statusLabel(row.getStatus()))
                .createdAt(row.getCreatedAt())
                .completedAt(row.getCompletedAt())
                .instructionPreview(preview)
                .sourceImageUrl(sourceUrl)
                .outputUrls(outputs)
                .build();
    }

    /** 仅允许查询 {@link GeminiImageTaskType#EDIT} 且归属当前用户的任务 */
    public GeminiTaskQueryVO getTaskForLocalEdit(Long taskId, Long userId) {
        if (taskId == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "taskId 不能为空");
        }
        GeminiImageTask row = taskMapper.selectById(taskId);
        if (row == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        if (!GeminiImageTaskType.EDIT.equals(row.getTaskType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        if (row.getUserId() == null || !row.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        return toQueryVO(row);
    }

    public GeminiTaskQueryVO getTaskForPackshot(Long taskId, Long userId) {
        if (taskId == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "taskId 不能为空");
        }
        GeminiImageTask row = taskMapper.selectById(taskId);
        if (row == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        if (!GeminiImageTaskType.CLOTHING_PACKSHOT.equals(row.getTaskType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        if (row.getUserId() == null || !row.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        return toQueryVO(row);
    }

    public GeminiTaskQueryVO getTaskForClothingSuite(Long taskId, Long userId) {
        if (taskId == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "taskId 不能为空");
        }
        GeminiImageTask row = taskMapper.selectById(taskId);
        if (row == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        if (!GeminiImageTaskType.CLOTHING_PRODUCT_SUITE.equals(row.getTaskType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        if (row.getUserId() == null || !row.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        return toQueryVO(row);
    }

    public GeminiTaskQueryVO getTaskForProductRetouch(Long taskId, Long userId) {
        if (taskId == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "taskId 不能为空");
        }
        GeminiImageTask row = taskMapper.selectById(taskId);
        if (row == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        if (!GeminiImageTaskType.PRODUCT_RETOUCH.equals(row.getTaskType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        if (row.getUserId() == null || !row.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        return toQueryVO(row);
    }

    public GeminiTaskQueryVO getTaskForPortraitStudio(Long taskId, Long userId) {
        if (taskId == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "taskId 不能为空");
        }
        GeminiImageTask row = taskMapper.selectById(taskId);
        if (row == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        if (!GeminiImageTaskType.PORTRAIT_STUDIO.equals(row.getTaskType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        if (row.getUserId() == null || !row.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        return toQueryVO(row);
    }

    private GeminiTaskQueryVO toQueryVO(GeminiImageTask row) {
        String safeErr =
                row.getStatus() != null && row.getStatus() == GeminiImageTaskStatus.FAILED
                        ? ClientSafeMessages.IMAGE_ASYNC_TASK_FAILED
                        : null;
        GeminiTaskQueryVO.GeminiTaskQueryVOBuilder b =
                GeminiTaskQueryVO.builder()
                        .taskId(row.getId())
                        .status(statusLabel(row.getStatus()))
                        .errorMsg(safeErr)
                        .createdAt(row.getCreatedAt())
                        .processingStartedAt(row.getProcessingStartedAt())
                        .completedAt(row.getCompletedAt())
                        .generationDurationMs(row.getGenerationDurationMs());
        if (row.getStatus() != null
                && row.getStatus() == GeminiImageTaskStatus.PROCESSING
                && row.getProcessingStartedAt() != null
                && row.getGenerationDurationMs() == null) {
            long elapsedMs = Duration.between(row.getProcessingStartedAt(), LocalDateTime.now()).toMillis();
            b.elapsedMsSinceProcessingStart(elapsedMs);
        }
        if (StringUtils.hasText(row.getResultJson())) {
            try {
                GeminiImageResultJson parsed = objectMapper.readValue(row.getResultJson(), GeminiImageResultJson.class);
                b.urls(parsed.getUrls() != null ? parsed.getUrls() : List.of());
                b.imageLabels(parsed.getImageLabels() != null ? parsed.getImageLabels() : List.of());
                b.textParts(parsed.getTextParts() != null ? parsed.getTextParts() : List.of());
            } catch (JsonProcessingException e) {
                b.urls(List.of());
                b.imageLabels(List.of());
                b.textParts(List.of());
            }
        } else {
            b.urls(List.of());
            b.imageLabels(List.of());
            b.textParts(List.of());
        }
        if (StringUtils.hasText(row.getPayloadJson())) {
            try {
                if (GeminiImageTaskType.CLOTHING_PRODUCT_SUITE.equals(row.getTaskType())) {
                    GeminiClothingSuiteTaskPayload pay =
                            objectMapper.readValue(row.getPayloadJson(), GeminiClothingSuiteTaskPayload.class);
                    b.parentTaskId(pay.getParentTaskId());
                    b.regenerateSlotIndex(pay.getRegenerateSlotIndex());
                } else if (GeminiImageTaskType.PORTRAIT_STUDIO.equals(row.getTaskType())) {
                    GeminiPortraitStudioTaskPayload pay =
                            objectMapper.readValue(row.getPayloadJson(), GeminiPortraitStudioTaskPayload.class);
                    b.parentTaskId(pay.getParentTaskId());
                    b.regenerateSlotIndex(pay.getRegenerateSlotIndex());
                }
            } catch (JsonProcessingException ignored) {
                // keep nulls
            }
        }
        return b.build();
    }

    /**
     * 穿搭视频：取当前用户已完成的局部改款（EDIT）任务首张成图 COS URL。
     */
    public String resolveFirstOutputCosUrlForLocalEdit(Long taskId, Long userId) {
        if (taskId == null || userId == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "改款任务 ID 无效");
        }
        GeminiImageTask row = taskMapper.selectById(taskId);
        if (row == null || row.getUserId() == null || !row.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限或任务不存在");
        }
        if (!GeminiImageTaskType.EDIT.equals(row.getTaskType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请选择局部改款任务");
        }
        if (row.getStatus() == null || row.getStatus() != GeminiImageTaskStatus.COMPLETED) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请选择已完成的改款任务");
        }
        if (!StringUtils.hasText(row.getResultJson())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "该改款任务暂无可用成片");
        }
        try {
            GeminiImageResultJson rj = objectMapper.readValue(row.getResultJson(), GeminiImageResultJson.class);
            if (rj.getUrls() != null && !rj.getUrls().isEmpty()) {
                String u = rj.getUrls().get(0);
                if (StringUtils.hasText(u)) {
                    return u.trim();
                }
            }
        } catch (JsonProcessingException ignored) {
            // fall through
        }
        throw new BusinessException(ResultCode.PARAM_INVALID, "该改款任务暂无可用成片");
    }

    private void assertGeminiReady() {
        if (!geminiImageService.isReady()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, ClientSafeMessages.AI_IMAGE_NOT_CONFIGURED);
        }
    }

    private GeminiTaskSubmitVO insertTask(String taskType, Object payload, Long userId, int consumeCredits) {
        long id = idGenerator.nextId();
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "任务参数序列化失败");
        }
        LocalDateTime now = LocalDateTime.now();
        GeminiImageTask row = new GeminiImageTask();
        row.setId(id);
        row.setTaskType(taskType);
        row.setUserId(userId);
        row.setConsumeCredits(Math.max(0, consumeCredits));
        row.setStatus(GeminiImageTaskStatus.PENDING);
        row.setPayloadJson(json);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        taskMapper.insert(row);
        userUploadRecordService.registerGeminiPayload(userId, json);
        Integer creditsForClient = consumeCredits > 0 ? consumeCredits : null;
        return new GeminiTaskSubmitVO(id, creditsForClient);
    }

    private static String statusLabel(Integer s) {
        if (s == null) {
            return "UNKNOWN";
        }
        return switch (s) {
            case GeminiImageTaskStatus.PENDING -> "PENDING";
            case GeminiImageTaskStatus.PROCESSING -> "PROCESSING";
            case GeminiImageTaskStatus.COMPLETED -> "COMPLETED";
            case GeminiImageTaskStatus.FAILED -> "FAILED";
            default -> "UNKNOWN";
        };
    }
}
