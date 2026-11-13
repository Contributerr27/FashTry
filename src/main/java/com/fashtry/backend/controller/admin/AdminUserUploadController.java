package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.vo.AdminUserUploadVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.admin.AdminConsoleService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/user-uploads")
@Validated
public class AdminUserUploadController {

    private final AdminConsoleService adminConsoleService;

    public AdminUserUploadController(AdminConsoleService adminConsoleService) {
        this.adminConsoleService = adminConsoleService;
    }

    /**
     * 用户上传图片：含直传 COS、试穿模特/服装、Gemini 任务输入图等；打开列表时会自动补录历史任务中的缺失记录。
     */
    @GetMapping
    public Result<PageResult<AdminUserUploadVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String category) {
        return Result.success(adminConsoleService.pageUserUploads(page, pageSize, userId, category));
    }
}
