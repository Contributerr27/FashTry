package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminBrandUserCreateRequest {

    @NotBlank
    @Size(min = 2, max = 64)
    private String username;

    @NotBlank
    @Size(min = 6, max = 64)
    private String password;

    private String displayName;

    private String mobile;

    private String role;

    private Integer status;
}
