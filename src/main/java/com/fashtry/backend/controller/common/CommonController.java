package com.fashtry.backend.controller.common;

import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.bean.dto.SendSmsRequest;
import com.fashtry.backend.bean.vo.CaptchaVO;
import com.fashtry.backend.service.business.AuthService;
import com.fashtry.backend.service.business.CaptchaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 公共接口：图形验证码、发送短信验证码
 */
@RestController
@RequestMapping("/api/common")
public class CommonController {

    private final CaptchaService captchaService;
    private final AuthService authService;

    public CommonController(CaptchaService captchaService, AuthService authService) {
        this.captchaService = captchaService;
        this.authService = authService;
    }

    /**
     * 获取图形验证码（发短信前先调此接口，再带 captchaKey、captchaCode 调 sendCode）
     */
    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        String[] arr = captchaService.generate();
        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaKey(arr[0]);

        vo.setCaptchaImage(arr[1]);
        return Result.success(vo);
    }

    /**
     * 发送短信验证码（需先调 /captcha 获取图形验证码，提交时带上 captchaKey、captchaCode）
     */
    @PostMapping("/sendCode")
    public Result<Void> sendCode(@Valid @RequestBody SendSmsRequest request) {
        authService.sendLoginCode(request.getMobile(), request.getCaptchaKey(), request.getCaptchaCode());
        return Result.success("验证码已发送", null);
    }
}
