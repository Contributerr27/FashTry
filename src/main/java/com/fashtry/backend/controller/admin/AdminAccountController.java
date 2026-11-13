package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.dto.AdminChangePasswordRequest;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.security.LoginUser;
import com.fashtry.backend.service.admin.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/account")
public class AdminAccountController {

    private final AdminAuthService adminAuthService;

    public AdminAccountController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(
            @AuthenticationPrincipal LoginUser user,
            @Valid @RequestBody AdminChangePasswordRequest body) {
        adminAuthService.changePassword(user, body);
        return Result.success();
    }
}
