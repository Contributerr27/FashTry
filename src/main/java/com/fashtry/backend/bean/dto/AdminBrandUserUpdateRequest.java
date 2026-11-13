package com.fashtry.backend.bean.dto;

import lombok.Data;

@Data
public class AdminBrandUserUpdateRequest {

    private String displayName;

    private String mobile;

    private String role;

    private Integer status;
}
