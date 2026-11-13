package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendEmailCodeRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "图形验证码 key 不能为空")
    private String captchaKey;

    @NotBlank(message = "图形验证码不能为空")
    private String captchaCode;
}
