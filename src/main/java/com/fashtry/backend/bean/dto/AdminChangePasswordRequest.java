package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminChangePasswordRequest {

    @NotBlank
    private String oldPassword;

    @NotBlank
    @Size(min = 6, message = "新密码至少6位")
    private String newPassword;

    @NotBlank
    private String captchaKey;

    @NotBlank
    private String captchaCode;
}
