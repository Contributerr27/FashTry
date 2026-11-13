package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminPublicModelUpsertRequest {

    private Integer id;

    @NotBlank
    private String name;

    @NotBlank
    private String gender;

    private String region;

    private String ageGroup;

    private String style;

    @NotBlank
    private String imageUrl;

    private String thumbUrl;

    @NotNull
    private Integer isActive;

    @NotNull
    private Integer sortOrder;
}
