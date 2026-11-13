package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.dto.AdminUserSetCreditsRequest;
import com.fashtry.backend.bean.vo.AdminUserCreditsVO;
import com.fashtry.backend.bean.vo.AdminUserListVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.admin.AdminConsoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@Validated
public class AdminUserController {

    private final AdminConsoleService adminConsoleService;

    public AdminUserController(AdminConsoleService adminConsoleService) {
        this.adminConsoleService = adminConsoleService;
    }

    @GetMapping
    public Result<PageResult<AdminUserListVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminConsoleService.pageUsers(page, pageSize, keyword));
    }

    @PutMapping("/{userId}/credits")
    public Result<AdminUserCreditsVO> setCredits(
            @PathVariable long userId,
            @Valid @RequestBody AdminUserSetCreditsRequest body) {
        return Result.success(adminConsoleService.setUserTotalCredits(userId, body.getTotalCredits()));
    }
}
