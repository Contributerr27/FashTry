package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendSmsRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "手机号格式不正确，支持国际区号如 +86 13800138000")
    private String mobile;

    @NotBlank(message = "图形验证码key不能为空")
    private String captchaKey;

    @NotBlank(message = "图形验证码不能为空")
    private String captchaCode;
}
