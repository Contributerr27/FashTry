package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GeminiApiKeyUpsertRequest {

    @NotBlank
    @Size(max = 64)
    private String name;

    /** 更新时留空表示不改 Key */
    @Size(max = 256)
    private String apiKey;

    private Boolean enabled;

    private Integer sortOrder;
}
