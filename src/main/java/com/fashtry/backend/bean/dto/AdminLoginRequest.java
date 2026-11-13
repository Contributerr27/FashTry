package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminLoginRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String captchaKey;

    @NotBlank
    private String captchaCode;
}
