package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserApiKeyCreateRequest {

    /** 密钥备注，便于区分用途 */
    @Size(max = 64, message = "名称最多 64 个字符")
    private String name;
}
