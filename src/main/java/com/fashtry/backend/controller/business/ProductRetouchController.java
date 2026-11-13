package com.fashtry.backend.controller.business;

import com.fashtry.backend.bean.vo.GeminiPackshotHistoryItemVO;
import com.fashtry.backend.bean.vo.GeminiTaskQueryVO;
import com.fashtry.backend.bean.vo.GeminiTaskSubmitVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.common.config.GeminiApiProperties;
import com.fashtry.backend.security.SecurityUtils;
import com.fashtry.backend.service.ai.GeminiImageTaskService;
import com.fashtry.backend.service.ai.ProductRetouchPrompts;
import com.fashtry.backend.service.business.ProductRetouchService;
import java.util.List;
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
 * 服装提取：多图批量异步生图，结果与模特出图一致为 COS 图片 URL；不向前端返回模型名。
 */
@RestController
@RequestMapping("/api/product-retouch")
public class ProductRetouchController {

    private final ProductRetouchService productRetouchService;
    private final GeminiImageTaskService geminiImageTaskService;
    private final GeminiApiProperties geminiApiProperties;

    public ProductRetouchController(
            ProductRetouchService productRetouchService,
            GeminiImageTaskService geminiImageTaskService,
            GeminiApiProperties geminiApiProperties) {
        this.productRetouchService = productRetouchService;
        this.geminiImageTaskService = geminiImageTaskService;
        this.geminiApiProperties = geminiApiProperties;
    }

    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", productRetouchService.isEnabled());
        m.put("maxFiles", GeminiApiProperties.PRODUCT_RETOUCH_MAX_FILES);
        m.put(
                "garmentScopes",
                List.of(
                        ProductRetouchPrompts.SCOPE_FULL,
                        ProductRetouchPrompts.SCOPE_UPPER,
                        ProductRetouchPrompts.SCOPE_LOWER));
        return Result.success(m);
    }

    /** 扣点：每张出图成功扣 {@code creditsPerImage}，与批量张数相乘 */
    @GetMapping("/pricing")
    public Result<Map<String, Object>> pricing(
            @RequestParam(value = "fileCount", required = false) Integer fileCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("creditsPerImage", Math.max(0, geminiApiProperties.getProductRetouchCreditsPerImage()));
        m.put("maxFiles", GeminiApiProperties.PRODUCT_RETOUCH_MAX_FILES);
        if (fileCount != null) {
            m.put("consumeCredits", geminiApiProperties.computeProductRetouchConsumeCredits(fileCount));
        }
        m.put("slotRegenerateCredits", geminiApiProperties.computeProductRetouchConsumeCredits(1));
        return Result.success(m);
    }

    @GetMapping("/history")
    public Result<PageResult<GeminiPackshotHistoryItemVO>> history(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "12") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(geminiImageTaskService.listMyProductRetouchHistory(userId, page, size));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<GeminiTaskQueryVO> getTask(@PathVariable("taskId") Long taskId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(geminiImageTaskService.getTaskForProductRetouch(taskId, userId));
    }

    /**
     * @param files 至少 1 张，最多 {@link GeminiApiProperties#PRODUCT_RETOUCH_MAX_FILES} 张
     */
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GeminiTaskSubmitVO> submit(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "backgroundColor", required = false) String backgroundColor,
            @RequestParam(value = "aspectRatio", required = false) String aspectRatio,
            @RequestParam(value = "garmentScope", defaultValue = "FULL") String garmentScope,
            @RequestParam(value = "shootHint", required = false) String shootHint,
            @RequestParam(value = "cropPreference", required = false) String cropPreference,
            @RequestParam(value = "imageSize", required = false) String imageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(
                geminiImageTaskService.submitProductRetouch(
                        files,
                        backgroundColor,
                        aspectRatio,
                        garmentScope,
                        shootHint,
                        cropPreference,
                        imageSize,
                        userId));
    }

    /**
     * 对已完成的一套服装提取中的某一格单张重生成（同源图与参数）；扣点见 {@code /pricing} 的 {@code
     * slotRegenerateCredits}。
     */
    @PostMapping(value = "/regenerate-slot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GeminiTaskSubmitVO> regenerateSlot(
            @RequestParam("parentTaskId") Long parentTaskId, @RequestParam("slotIndex") int slotIndex) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(
                geminiImageTaskService.submitProductRetouchSlotRegenerate(parentTaskId, slotIndex, userId));
    }
}
