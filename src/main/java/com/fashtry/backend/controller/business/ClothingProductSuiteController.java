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
 * 服装电商套图（异步）：单张服装图 → 固定 10 张同源编辑；结果写入 COS 与 {@code gemini_image_tasks}。
 */
@RestController
@RequestMapping("/api/clothing-product-suite")
public class ClothingProductSuiteController {

    private final GeminiImageTaskService geminiImageTaskService;
    private final GeminiApiProperties geminiApiProperties;

    public ClothingProductSuiteController(
            GeminiImageTaskService geminiImageTaskService, GeminiApiProperties geminiApiProperties) {
        this.geminiImageTaskService = geminiImageTaskService;
        this.geminiApiProperties = geminiApiProperties;
    }

    @GetMapping("/pricing")
    public Result<Map<String, Integer>> pricing() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("creditsPerStep", Math.max(0, geminiApiProperties.getPackshotCreditsPerStep()));
        m.put("consumeCredits", geminiApiProperties.computeClothingSuiteConsumeCredits());
        m.put("stepCount", 10);
        m.put("slotRegenerateCredits", geminiApiProperties.computeClothingSuiteSlotRegenerateCredits());
        return Result.success(m);
    }

    @GetMapping("/history")
    public Result<PageResult<GeminiPackshotHistoryItemVO>> history(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "12") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(geminiImageTaskService.listMyClothingSuiteHistory(userId, page, size));
    }

    /**
     * @param file 服装图（平铺/挂拍/白底等）
     * @param modelId 可选，覆盖默认生图模型
     * @param outputLanguage 可选；卖点图与详情长图上的文案语言（zh, en, ja, ko 等），默认 zh
     * @param modelGender 可选；成片含人物时的呈现倾向 female / male / neutral，默认由服务端归一
     */
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GeminiTaskSubmitVO> submit(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "modelId", required = false) String modelId,
            @RequestParam(value = "aspectRatio", required = false) String aspectRatio,
            @RequestParam(value = "imageSize", required = false) String imageSize,
            @RequestParam(value = "outputLanguage", required = false) String outputLanguage,
            @RequestParam(value = "modelGender", required = false) String modelGender,
            @RequestParam(value = "productBrief", required = false) String productBrief,
            @RequestParam(value = "marketplace", required = false) String marketplace) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(
                geminiImageTaskService.submitClothingProductSuite(
                        file,
                        modelId,
                        aspectRatio,
                        imageSize,
                        outputLanguage,
                        modelGender,
                        productBrief,
                        marketplace,
                        userId));
    }

    /**
     * 对已完成整套套图任务中的某一格单张重生成（同源图、同参数）；成功扣 {@code slotRegenerateCredits} 点。
     *
     * @param parentTaskId 整套套图任务 ID（须已完成且为 10 张全量）
     * @param slotIndex 步骤索引 0～9，与成片顺序一致
     */
    @PostMapping(value = "/regenerate-slot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GeminiTaskSubmitVO> regenerateSlot(
            @RequestParam("parentTaskId") Long parentTaskId, @RequestParam("slotIndex") int slotIndex) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(geminiImageTaskService.submitClothingSuiteSlotRegenerate(parentTaskId, slotIndex, userId));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<GeminiTaskQueryVO> getTask(@PathVariable("taskId") Long taskId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(geminiImageTaskService.getTaskForClothingSuite(taskId, userId));
    }

    /**
     * 看服装图写卖点草稿（Gemini 文本模型），填入前端「商品卖点」；不扣点。
     */
    @PostMapping(value = "/ai-brief", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, String>> aiBrief(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "outputLanguage", required = false) String outputLanguage,
            @RequestParam(value = "marketplace", required = false) String marketplace,
            @RequestParam(value = "modelGender", required = false) String modelGender) {
        Long userId = SecurityUtils.getCurrentUserId();
        String brief =
                geminiImageTaskService.writeClothingSuiteAiBrief(
                        file, outputLanguage, marketplace, modelGender, userId);
        Map<String, String> m = new LinkedHashMap<>();
        m.put("brief", brief);
        return Result.success(m);
    }
}
