package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.dto.RegisterAbusePolicyRequest;
import com.fashtry.backend.bean.vo.RegisterAbuseSettingsVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.business.RegisterAbuseGuard;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/register-abuse")
@Validated
public class AdminRegisterAbuseController {

    private final RegisterAbuseGuard registerAbuseGuard;

    public AdminRegisterAbuseController(RegisterAbuseGuard registerAbuseGuard) {
        this.registerAbuseGuard = registerAbuseGuard;
    }

    @GetMapping
    public Result<RegisterAbuseSettingsVO> settings() {
        return Result.success(registerAbuseGuard.settings());
    }

    @PutMapping
    public Result<RegisterAbuseSettingsVO> update(@Valid @RequestBody RegisterAbusePolicyRequest body) {
        registerAbuseGuard.updatePolicy(body);
        return Result.success(registerAbuseGuard.settings());
    }
}
