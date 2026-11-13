package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.vo.AdminFeedbackListVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.business.FeedbackService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/feedback")
@Validated
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    public AdminFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public Result<PageResult<AdminFeedbackListVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId) {
        return Result.success(feedbackService.adminPage(page, pageSize, keyword, userId));
    }
}
