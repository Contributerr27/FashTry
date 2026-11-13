package com.fashtry.backend.controller.business;

import com.fashtry.backend.bean.dto.ModelVideoSubmitRequest;
import com.fashtry.backend.bean.vo.ModelVideoPricingVO;
import com.fashtry.backend.bean.vo.ModelVideoStatusVO;
import com.fashtry.backend.bean.vo.ModelVideoSubmitVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.business.ModelVideoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模特穿搭动态视频（Veo 图生视频）：上传图、试穿成片或局部改款成片均可作为首帧。
 */
@RestController
@RequestMapping("/api/model-video")
public class ModelVideoController {

    private final ModelVideoService modelVideoService;

    public ModelVideoController(ModelVideoService modelVideoService) {
        this.modelVideoService = modelVideoService;
    }

    @GetMapping("/pricing")
    public Result<ModelVideoPricingVO> pricing() {
        return Result.success(modelVideoService.getPricing());
    }

    @PostMapping("/submit")
    public Result<ModelVideoSubmitVO> submit(@Valid @RequestBody ModelVideoSubmitRequest request) {
        return Result.success(modelVideoService.submit(request));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<ModelVideoStatusVO> getStatus(@PathVariable Long taskId) {
        return Result.success(modelVideoService.getStatus(taskId));
    }

    @GetMapping("/tasks")
    public Result<PageResult<ModelVideoStatusVO>> list(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "12") int size) {
        return Result.success(modelVideoService.listMyTasks(page, size));
    }
}
