package com.fashtry.backend.service.business;

/**
 * 图片验证码（防短信轰炸）
 */
public interface CaptchaService {

    /**
     * 生成图片验证码，返回 key 与 base64 图片
     *
     * @return [0]=captchaKey, [1]=base64 图片（不含 data:image/png;base64, 前缀）
     */
    String[] generate();

    /**
     * 校验验证码（校验后即失效，一次性）
     *
     * @param captchaKey 生成时返回的 key
     * @param userInput  用户输入的验证码
     * @return 是否通过
     */
    boolean validate(String captchaKey, String userInput);
}
