package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.vo.AdminTaskDetailWrapper;
import com.fashtry.backend.bean.vo.AdminUnifiedTaskListItemVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.admin.AdminConsoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台任务列表：试穿（tryon_tasks）+ Gemini 异步图（gemini_image_tasks）+ 模特视频（model_video_tasks）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminTaskController {

    private final AdminConsoleService adminConsoleService;

    @GetMapping("/tasks")
    public Result<PageResult<AdminUnifiedTaskListItemVO>> pageTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long userId) {
        return Result.success(adminConsoleService.pageUnifiedTasks(page, pageSize, userId));
    }

    @GetMapping("/tasks/detail")
    public Result<AdminTaskDetailWrapper> taskDetail(
            @RequestParam String recordSource, @RequestParam Long id) {
        return Result.success(adminConsoleService.getAdminTaskDetail(recordSource, id));
    }
}
