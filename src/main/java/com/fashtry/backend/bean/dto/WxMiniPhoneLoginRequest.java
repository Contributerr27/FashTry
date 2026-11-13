package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信小程序：button getPhoneNumber 返回的 code，由后端换真实手机号并登录
 */
@Data
public class WxMiniPhoneLoginRequest {

    /**
     * 前端 getPhoneNumber 回调 detail.code（与 wx.login 的 code 不同）
     */
    @NotBlank(message = "缺少手机号授权码")
    private String wxPhoneCode;

    private String invitationCode;

    private String brandCode;

    /**
     * 注册来源：web | mp_weixin | app；未传或非法时落库为 null，不报错
     */
    private String registerSource;
}
