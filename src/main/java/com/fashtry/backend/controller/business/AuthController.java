package com.fashtry.backend.controller.business;

import com.fashtry.backend.bean.dto.EmailLoginRequest;
import com.fashtry.backend.bean.dto.PhoneLoginRequest;
import com.fashtry.backend.bean.dto.SendEmailCodeRequest;
import com.fashtry.backend.bean.dto.WxMiniPhoneLoginRequest;
import com.fashtry.backend.bean.dto.SendSmsRequest;
import com.fashtry.backend.bean.dto.TokenResponse;
import com.fashtry.backend.bean.vo.CaptchaVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.business.AuthService;
import com.fashtry.backend.service.business.CaptchaService;
import com.fashtry.backend.bean.dto.RefreshTokenRequest;
import com.fashtry.backend.common.config.GoogleAuthProperties;
import com.fashtry.backend.service.business.GoogleAuthService;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final GoogleAuthService googleAuthService;
    private final GoogleAuthProperties googleAuthProperties;

    public AuthController(AuthService authService, CaptchaService captchaService, GoogleAuthService googleAuthService,
                          GoogleAuthProperties googleAuthProperties) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.googleAuthService = googleAuthService;
        this.googleAuthProperties = googleAuthProperties;
    }

    /**
     * 获取图形验证码（发短信前先调此接口，再带 captchaKey、captchaCode 调 /sms/send）
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
     * 发送登录验证码（需先调 /captcha 获取图形验证码，提交时带上 captchaKey、captchaCode）
     */
    @PostMapping("/sms/send")
    public Result<Void> sendCode(@Valid @RequestBody SendSmsRequest request) {
        authService.sendLoginCode(request.getMobile(), request.getCaptchaKey(), request.getCaptchaCode());
        return Result.success();
    }

    /**
     * 发送邮箱登录验证码（需先调 /captcha，与短信共用图形验证码）
     */
    @PostMapping("/email/send")
    public Result<Void> sendEmailCode(@Valid @RequestBody SendEmailCodeRequest request) {
        authService.sendEmailLoginCode(request.getEmail(), request.getCaptchaKey(), request.getCaptchaCode());
        return Result.success();
    }

    /**
     * 手机号+验证码登录
     * 未注册用户自动注册，昵称随机生成，赠送免费试用点数（后台「注册赠送」可配每人额度与每天总额度）
     */
    @PostMapping("/phone/login")
    public Result<TokenResponse> phoneLogin(@Valid @RequestBody PhoneLoginRequest request) {
        return Result.success(authService.phoneLogin(request));
    }

    /**
     * 邮箱+验证码登录/注册（Resend 发信），与手机号同一用户体系
     */
    @PostMapping("/email/login")
    public Result<TokenResponse> emailLogin(@Valid @RequestBody EmailLoginRequest request) {
        return Result.success(authService.emailLogin(request));
    }

    /**
     * 微信小程序：button getPhoneNumber 返回的 code 换手机号并登录（与 /phone/login 同一账号体系）
     */
    @PostMapping("/wx/phone-login")
    public Result<TokenResponse> wxMiniPhoneLogin(@Valid @RequestBody WxMiniPhoneLoginRequest request) {
        return Result.success(authService.wxMiniPhoneLogin(request));
    }

    /**
     * 刷新 token：用 refreshToken 换取新的 accessToken
     */
    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refreshToken(request.getRefreshToken()));
    }

    /**
     * 获取 Google 授权 URL，前端跳转用
     *
     * @param state 可选，建议传 returnUrl 用于登录后跳转
     */
    @GetMapping("/google/login-url")
    public Result<Map<String, String>> getGoogleLoginUrl(@RequestParam(required = false) String state) {
        String url = googleAuthService.buildAuthorizationUrl(state);
        return Result.success(Map.of("url", url != null ? url : ""));
    }

    /**
     * Google 授权回调，用 code 换取 token 并登录，重定向到登录页并带上 token
     */
    @GetMapping("/google/callback")
    public ResponseEntity<String> googleCallback(@RequestParam String code, @RequestParam(required = false) String state) {
        TokenResponse token = googleAuthService.loginWithCode(code, state);
        String rawState = state != null ? state.trim() : "";
        int bcSep = rawState.indexOf("|bc:");
        if (bcSep >= 0) {
            rawState = rawState.substring(0, bcSep);
        }
        boolean mobile = rawState.startsWith("m:");
        String returnTo = mobile ? rawState.substring(2) : rawState;
        returnTo = (returnTo != null && !returnTo.isBlank()) ? returnTo.trim() : "/";
        if (!returnTo.startsWith("/") || returnTo.startsWith("//")) {
            returnTo = "/";
        }

        String base = oauthFrontendBase();
        if (mobile) {
            base = base + "/mobile";
        }
        String path = "/login?googleSuccess=1&accessToken="
                + URLEncoder.encode(token.getAccessToken(), StandardCharsets.UTF_8)
                + "&refreshToken="
                + URLEncoder.encode(token.getRefreshToken() != null ? token.getRefreshToken() : "", StandardCharsets.UTF_8)
                + "&return=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
        if (Boolean.TRUE.equals(token.getNewRegistration())) {
            path += "&newRegistration=1";
        }
        return ResponseEntity.status(302).header("Location", base + path).build();
    }

    /** 前后端分离：跳转到前端站点；未配置 frontend-base-url 时退回 redirect-base-url（同机部署时可用） */
    private String oauthFrontendBase() {
        String raw = StringUtils.hasText(googleAuthProperties.getFrontendBaseUrl())
                ? googleAuthProperties.getFrontendBaseUrl()
                : googleAuthProperties.getRedirectBaseUrl();
        raw = raw != null ? raw.trim() : "";
        if (raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        return raw;
    }
}
