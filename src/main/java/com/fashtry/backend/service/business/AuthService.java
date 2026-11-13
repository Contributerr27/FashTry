package com.fashtry.backend.service.business;

import com.fashtry.backend.bean.dto.EmailLoginRequest;
import com.fashtry.backend.bean.dto.PhoneLoginRequest;
import com.fashtry.backend.bean.dto.TokenResponse;
import com.fashtry.backend.bean.dto.WxMiniPhoneLoginRequest;

public interface AuthService {

    /**
     * 发送登录验证码（需先校验图形验证码）
     */
    void sendLoginCode(String mobile, String captchaKey, String captchaCode);

    /**
     * 发送邮箱登录验证码（需先校验图形验证码）
     */
    void sendEmailLoginCode(String email, String captchaKey, String captchaCode);

    /**
     * 手机号+验证码登录，未注册则自动注册(随机昵称，赠送免费点数)
     */
    TokenResponse phoneLogin(PhoneLoginRequest request);

    /**
     * 微信小程序：微信授权手机号（getPhoneNumber 的 code）登录/注册，与短信登录同一套用户体系
     */
    TokenResponse wxMiniPhoneLogin(WxMiniPhoneLoginRequest request);

    /**
     * 邮箱+验证码登录/注册，与手机号体系共用 users 表
     */
    TokenResponse emailLogin(EmailLoginRequest request);

    /**
     * 刷新 token：用 refreshToken 换取新的 accessToken
     */
    TokenResponse refreshToken(String refreshToken);
}
