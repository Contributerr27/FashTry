package com.fashtry.backend.controller.business;

import com.fashtry.backend.bean.vo.GeminiImageEditHistoryItemVO;
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

import java.util.Map;

/**
 * 单图局部改款（异步）：对外路径不含供应商名称；底层仍写入 {@code gemini_image_tasks}，由既有调度执行。
 * <p>
 * 计费：提交时校验钱包可用点数（见 {@code app.gemini.local-edit-consume-credits}，默认 1）；成功出图后扣点，失败不扣。
 * 响应 {@link com.fashtry.backend.bean.vo.GeminiTaskSubmitVO#getConsumeCredits()} 供前端展示说明。
 * GET {@code /history} 返回当前用户局部改款分页列表（原图 URL、成图 URL、状态等），供前端历史卡片与下载。
 * </p>
 */
@RestController
@RequestMapping("/api/image-edit")
public class ImageEditController {

    private final GeminiImageTaskService geminiImageTaskService;
    private final GeminiApiProperties geminiApiProperties;

    public ImageEditController(
            GeminiImageTaskService geminiImageTaskService, GeminiApiProperties geminiApiProperties) {
        this.geminiImageTaskService = geminiImageTaskService;
        this.geminiApiProperties = geminiApiProperties;
    }

    /**
     * 供前端展示「每次成功生成扣点」说明（与 {@link GeminiApiProperties#getLocalEditConsumeCredits()} 一致，需登录）。
     */
    @GetMapping("/pricing")
    public Result<Map<String, Integer>> localEditPricing() {
        int c = Math.max(0, geminiApiProperties.getLocalEditConsumeCredits());
        return Result.success(Map.of("consumeCredits", c));
    }

    /**
     * 当前账号局部改款历史（分页），卡片可配合 GET /tasks/{taskId} 拉详情。
     */
    @GetMapping("/history")
    public Result<PageResult<GeminiImageEditHistoryItemVO>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(geminiImageTaskService.listMyLocalEditHistory(userId, page, size));
    }

    /**
     * 提交：{@code file} 原图，{@code instruction} 修改说明，可选 {@code mask} 与源图同尺寸的黑白蒙版（白=可改区域），可选
     * {@code reference} 参考图（辅助颜色/质感/风格）。
     * 成功生成后按配置扣点，详见类注释。
     */
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GeminiTaskSubmitVO> submit(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mask", required = false) MultipartFile mask,
            @RequestParam(value = "reference", required = false) MultipartFile reference,
            @RequestParam("instruction") String instruction,
            @RequestParam(value = "modelId", required = false) String modelId,
            @RequestParam(value = "aspectRatio", required = false) String aspectRatio,
            @RequestParam(value = "imageSize", required = false) String imageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(
                geminiImageTaskService.submitLocalEdit(
                        file, mask, reference, instruction, modelId, aspectRatio, imageSize, userId));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<GeminiTaskQueryVO> getTask(@PathVariable("taskId") Long taskId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(geminiImageTaskService.getTaskForLocalEdit(taskId, userId));
    }
}
