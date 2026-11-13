package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.dto.RegisterGiftPolicyRequest;
import com.fashtry.backend.bean.vo.RegisterGiftSettingsVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.business.RegisterGiftService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/register-gift")
@Validated
public class AdminRegisterGiftController {

    private final RegisterGiftService registerGiftService;

    public AdminRegisterGiftController(RegisterGiftService registerGiftService) {
        this.registerGiftService = registerGiftService;
    }

    @GetMapping
    public Result<RegisterGiftSettingsVO> settings() {
        return Result.success(registerGiftService.settings());
    }

    @PutMapping
    public Result<RegisterGiftSettingsVO> update(@Valid @RequestBody RegisterGiftPolicyRequest body) {
        registerGiftService.updatePolicy(body.getPerUserCredits(), body.getDailyTotalCredits());
        return Result.success(registerGiftService.settings());
    }
}
