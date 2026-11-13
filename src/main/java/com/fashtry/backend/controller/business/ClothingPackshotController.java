package com.fashtry.backend.controller.business;

import com.fashtry.backend.bean.vo.GeminiPackshotHistoryItemVO;
import com.fashtry.backend.bean.vo.GeminiTaskQueryVO;
import com.fashtry.backend.bean.vo.GeminiTaskSubmitVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.common.config.GeminiApiProperties;
import com.fashtry.backend.security.SecurityUtils;
import com.fashtry.backend.service.ai.GeminiImageTaskService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 模特出图（异步）：平铺图 → 模特上身 → 多场景（场景+姿势均变化）→ 白底主图；结果写入 COS 与 {@code gemini_image_tasks}。
 */
@RestController
@RequestMapping("/api/clothing-packshot")
public class ClothingPackshotController {

    private final GeminiImageTaskService geminiImageTaskService;
    private final GeminiApiProperties geminiApiProperties;

    public ClothingPackshotController(
            GeminiImageTaskService geminiImageTaskService, GeminiApiProperties geminiApiProperties) {
        this.geminiImageTaskService = geminiImageTaskService;
        this.geminiApiProperties = geminiApiProperties;
    }

    /**
     * @param sceneVariantCount 可选；若传入则额外返回该场景数下的 {@code consumeCredits}（与提交时扣点一致）
     */
    @GetMapping("/pricing")
    public Result<Map<String, Integer>> pricing(
            @RequestParam(value = "sceneVariantCount", required = false) Integer sceneVariantCount) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put(
                "creditsPerStep",
                Math.max(0, geminiApiProperties.getPackshotCreditsPerStep()));
        if (sceneVariantCount != null) {
            m.put("consumeCredits", geminiApiProperties.computePackshotConsumeCredits(sceneVariantCount));
        }
        m.put("slotRegenerateCredits", geminiApiProperties.computeClothingSuiteSlotRegenerateCredits());
        return Result.success(m);
    }

    @GetMapping("/history")
    public Result<PageResult<GeminiPackshotHistoryItemVO>> history(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "12") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(geminiImageTaskService.listMyPackshotHistory(userId, page, size));
    }

    /**
     * @param file 平铺服装图
     * @param artDirection indoor_leisure | urban_street | minimal_studio | dynamic_candid（兼容旧值 minimal / luxury /
     *     street 与中英文关键词）
     * @param modelGender female | male | neutral
     * @param ageGroup 可选：CHILD（儿童）、ADULT（成人，默认）、SENIOR（老人）
     * @param bodyType 可选：SLIM（偏瘦）、AVERAGE（标准，默认）、FULL（偏胖/丰满）
     * @param sceneVariantCount 场景变体张数 2～8，默认 4；总出图 = 1 + sceneVariantCount + 1（最多 10 张）
     * @param framingMode 可选：FULL_LENGTH（默认）、HALF_LENGTH、AUTO
     * @param faceMode PRESET（默认）或 UPLOAD；传 faceReference 文件时强制按上传参考脸处理
     * @param facePreset 预设面孔：ASIAN_ECOM, WESTERN_FASHION, MIXED_LUXE, EDITORIAL_SUPERMODEL（仅 PRESET 时有效）
     * @param faceReference 可选参考脸图（正脸清晰为宜）；与平铺图一并用于第一步生成
     */
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GeminiTaskSubmitVO> submit(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "artDirection", required = false) String artDirection,
            @RequestParam(value = "modelGender", required = false) String modelGender,
            @RequestParam(value = "ageGroup", required = false) String ageGroup,
            @RequestParam(value = "bodyType", required = false) String bodyType,
            @RequestParam(value = "modelStyleNote", required = false) String modelStyleNote,
            @RequestParam(value = "faceMode", required = false) String faceMode,
            @RequestParam(value = "facePreset", required = false) String facePreset,
            @RequestParam(value = "faceReference", required = false) MultipartFile faceReference,
            @RequestParam(value = "sceneVariantCount", required = false) Integer sceneVariantCount,
            @RequestParam(value = "framingMode", required = false) String framingMode,
            @RequestParam(value = "modelId", required = false) String modelId,
            @RequestParam(value = "aspectRatio", required = false) String aspectRatio,
            @RequestParam(value = "imageSize", required = false) String imageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(
                geminiImageTaskService.submitClothingPackshot(
                        file,
                        artDirection,
                        modelGender,
                        ageGroup,
                        bodyType,
                        modelStyleNote,
                        faceMode,
                        facePreset,
                        faceReference,
                        sceneVariantCount,
                        framingMode,
                        modelId,
                        aspectRatio,
                        imageSize,
                        userId));
    }

    /**
     * 对已完成整套模特出图任务中的某一格单张重生成（同源参数）；成功扣 {@code slotRegenerateCredits} 点。
     *
     * @param parentTaskId 整套模特出图任务 ID（须已有全量 result_json）
     * @param slotIndex 与成片顺序一致：0=上身，1…N=场景，N+1=白底（N 为场景张数）
     */
    @PostMapping(value = "/regenerate-slot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GeminiTaskSubmitVO> regenerateSlot(
            @RequestParam("parentTaskId") Long parentTaskId, @RequestParam("slotIndex") int slotIndex) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(
                geminiImageTaskService.submitPackshotSlotRegenerate(parentTaskId, slotIndex, userId));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<GeminiTaskQueryVO> getTask(@PathVariable("taskId") Long taskId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(geminiImageTaskService.getTaskForPackshot(taskId, userId));
    }
}
