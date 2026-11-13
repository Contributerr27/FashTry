package com.fashtry.backend.controller.business;

import com.fashtry.backend.bean.vo.GeminiPackshotHistoryItemVO;
import com.fashtry.backend.bean.vo.GeminiTaskQueryVO;
import com.fashtry.backend.bean.vo.GeminiTaskSubmitVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.common.config.GeminiApiProperties;
import com.fashtry.backend.security.SecurityUtils;
import com.fashtry.backend.service.ai.GeminiImageTaskService;
import com.fashtry.backend.service.ai.PortraitStudioPrompts;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 写真：1～3 张参考自拍 + 风格 + 妆容 + 多输出张数，异步 Gemini 出图。
 */
@RestController
@RequestMapping("/api/portrait-studio")
public class PortraitStudioController {

    private final GeminiImageTaskService geminiImageTaskService;
    private final GeminiApiProperties geminiApiProperties;

    public PortraitStudioController(
            GeminiImageTaskService geminiImageTaskService, GeminiApiProperties geminiApiProperties) {
        this.geminiImageTaskService = geminiImageTaskService;
        this.geminiApiProperties = geminiApiProperties;
    }

    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("maxOutputs", GeminiApiProperties.PORTRAIT_STUDIO_MAX_OUTPUTS);
        m.put("maxSourceImages", GeminiApiProperties.PORTRAIT_STUDIO_MAX_SOURCE_IMAGES);
        m.put("stylePresets", PortraitStudioPrompts.listPresetKeys());
        m.put("makeupPresets", PortraitStudioPrompts.listMakeupKeys());
        m.put("outfitModes", PortraitStudioPrompts.listOutfitModeKeys());
        return Result.success(m);
    }

    @GetMapping("/pricing")
    public Result<Map<String, Object>> pricing(
            @RequestParam(value = "outputCount", required = false) Integer outputCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("creditsPerImage", Math.max(0, geminiApiProperties.getPortraitStudioCreditsPerImage()));
        m.put("maxOutputs", GeminiApiProperties.PORTRAIT_STUDIO_MAX_OUTPUTS);
        m.put("maxSourceImages", GeminiApiProperties.PORTRAIT_STUDIO_MAX_SOURCE_IMAGES);
        m.put(
                "slotRegenerateCredits",
                geminiApiProperties.computePortraitStudioConsumeCredits(1));
        if (outputCount != null) {
            m.put("consumeCredits", geminiApiProperties.computePortraitStudioConsumeCredits(outputCount));
        }
        return Result.success(m);
    }

    @GetMapping("/history")
    public Result<PageResult<GeminiPackshotHistoryItemVO>> history(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "12") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(geminiImageTaskService.listMyPortraitStudioHistory(userId, page, size));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<GeminiTaskQueryVO> getTask(@PathVariable("taskId") Long taskId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(geminiImageTaskService.getTaskForPortraitStudio(taskId, userId));
    }

    /**
     * @param files 至少 1 张、最多 {@link GeminiApiProperties#PORTRAIT_STUDIO_MAX_SOURCE_IMAGES} 张
     */
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GeminiTaskSubmitVO> submit(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("stylePreset") String stylePreset,
            @RequestParam(value = "makeupPreset", defaultValue = "ORIGINAL") String makeupPreset,
            @RequestParam(value = "outfitMode", defaultValue = "STYLE") String outfitMode,
            @RequestParam(value = "outputCount", required = false) Integer outputCount,
            @RequestParam(value = "aspectRatio", required = false) String aspectRatio,
            @RequestParam(value = "imageSize", required = false) String imageSize,
            @RequestParam(value = "modelId", required = false) String modelId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(
                geminiImageTaskService.submitPortraitStudio(
                        files,
                        stylePreset,
                        makeupPreset,
                        outfitMode,
                        outputCount,
                        aspectRatio,
                        imageSize,
                        modelId,
                        userId));
    }

    /**
     * 对已完成的一套 AI 写真中的某一格单张重生成（同源参考与参数）；成功扣 {@code slotRegenerateCredits} 点（见
     * {@code /pricing}）。
     */
    @PostMapping(value = "/regenerate-slot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GeminiTaskSubmitVO> regenerateSlot(
            @RequestParam("parentTaskId") Long parentTaskId, @RequestParam("slotIndex") int slotIndex) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(
                geminiImageTaskService.submitPortraitStudioSlotRegenerate(parentTaskId, slotIndex, userId));
    }
}
