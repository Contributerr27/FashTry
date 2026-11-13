package com.fashtry.backend.service.business;

/**
 * 邮箱登录验证码（Redis + Resend）
 */
public interface EmailOtpService {

    /**
     * 向已规范化的邮箱发送 6 位验证码
     */
    void sendCode(String normalizedEmail);

    /**
     * 校验验证码，成功后删除缓存
     */
    boolean verifyCode(String normalizedEmail, String code);
}
