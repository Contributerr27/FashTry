package com.fashtry.backend.controller.business;

import com.fashtry.backend.bean.vo.MobileUnifiedHistoryItemVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.security.SecurityUtils;
import com.fashtry.backend.service.business.MobileTaskHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 移动端专用：统一查询历史任务（试穿 / 局部改款 / 模特出图 / 套图），减少并行 HTTP。
 */
@RestController
@RequestMapping("/api/mobile/task-history")
public class MobileTaskHistoryController {

    private final MobileTaskHistoryService mobileTaskHistoryService;

    public MobileTaskHistoryController(MobileTaskHistoryService mobileTaskHistoryService) {
        this.mobileTaskHistoryService = mobileTaskHistoryService;
    }

    /**
     * @param kind     all（每类最多 50 条合并后按时间倒序）| tryon | image_edit | packshot | suite | retouch | portrait（走原有分页语义）
     * @param page     从 1 开始，默认 1；kind=all 时忽略
     * @param pageSize 默认 20，最大 50；kind=all 时忽略
     */
    @GetMapping
    public Result<PageResult<MobileUnifiedHistoryItemVO>> list(
            @RequestParam(defaultValue = "all") String kind,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(mobileTaskHistoryService.listUnified(userId, kind, page, pageSize));
    }
}
