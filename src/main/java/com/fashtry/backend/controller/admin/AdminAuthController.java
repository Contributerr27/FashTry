package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.vo.AdminLoginVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.admin.AdminAuthService;
import com.fashtry.backend.bean.dto.AdminLoginRequest;
import com.fashtry.backend.bean.vo.CaptchaVO;
import com.fashtry.backend.service.business.CaptchaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final CaptchaService captchaService;
    private final AdminAuthService adminAuthService;

    public AdminAuthController(CaptchaService captchaService, AdminAuthService adminAuthService) {
        this.captchaService = captchaService;
        this.adminAuthService = adminAuthService;
    }

    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        String[] arr = captchaService.generate();
        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaKey(arr[0]);
        vo.setCaptchaImage(arr[1]);
        return Result.success(vo);
    }

    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.success(adminAuthService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        adminAuthService.logout(auth);
        return Result.success();
    }
}
