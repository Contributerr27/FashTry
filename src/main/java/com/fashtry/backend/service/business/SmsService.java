package com.fashtry.backend.service.business;

/**
 * 短信验证码服务
 * 开发环境可 mock 实现(控制台输出)，生产环境接入腾讯云短信等
 */
public interface SmsService {

    /**
     * 发送验证码到指定手机号
     *
     * @param mobile 手机号
     * @return 验证码(开发环境返回便于调试，生产环境不返回)
     */
    String sendCode(String mobile);

    /**
     * 校验验证码，校验成功后删除
     */
    boolean verifyCode(String mobile, String code);
}
