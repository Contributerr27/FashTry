package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PhoneLoginRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(
            regexp = "^\\+?[1-9]\\d{1,14}$",
            message = "手机号格式不正确"
    )
    private String mobile;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为6位数字")
    private String code;

    /** 邀请码，新用户注册时传入可绑定邀请关系 */
    private String invitationCode;

    /** 品牌唯一编码（扫码进店），新用户注册时写入 users.brand_id */
    private String brandCode;

    /**
     * 注册来源：web | mp_weixin | app；未传或非法时落库为 null，不报错
     */
    private String registerSource;
}
