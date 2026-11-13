package com.fashtry.backend.controller.business;

import com.fashtry.backend.bean.dto.XhsCardGenerateRequest;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.bean.vo.XhsCardGenerateVO;
import com.fashtry.backend.bean.vo.XhsCardHistoryItemVO;
import com.fashtry.backend.bean.vo.XhsCardTaskDetailVO;
import com.fashtry.backend.security.SecurityUtils;
import com.fashtry.backend.service.business.XhsCardService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 小红书图文卡片：AI 生成结构化文案；图片由前端 HTML 模板导出（不走生图模型）。
 */
@RestController
@RequestMapping("/api/xhs-card")
public class XhsCardController {

    private final XhsCardService xhsCardService;

    public XhsCardController(XhsCardService xhsCardService) {
        this.xhsCardService = xhsCardService;
    }

    @GetMapping("/pricing")
    public Result<Map<String, Object>> pricing() {
        return Result.success(
                Map.of(
                        "consumeCredits",
                        xhsCardService.consumeCredits(),
                        "styles",
                        xhsCardService.availableStyles()));
    }

    @GetMapping("/styles")
    public Result<List<String>> styles() {
        return Result.success(xhsCardService.availableStyles());
    }

    @PostMapping("/generate")
    public Result<XhsCardGenerateVO> generate(@Valid @RequestBody XhsCardGenerateRequest body) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(xhsCardService.generate(userId, body));
    }

    @GetMapping("/history")
    public Result<PageResult<XhsCardHistoryItemVO>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(xhsCardService.listHistory(userId, page, size));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<XhsCardTaskDetailVO> detail(@PathVariable Long taskId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(xhsCardService.getDetail(userId, taskId));
    }
}
