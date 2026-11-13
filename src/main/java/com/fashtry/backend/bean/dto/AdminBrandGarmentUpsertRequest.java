package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminBrandGarmentUpsertRequest {

    @NotBlank
    private String name;

    private String nameEn;

    @NotBlank
    private String sceneCategory;

    @NotBlank
    private String gender;

    private String garmentType;

    @NotBlank
    private String imageUrl;

    private String thumbUrl;

    private String remark;

    private String purchaseUrl;

    /** 0-草稿 1-待审 2-已上架 3-驳回 */
    private Integer auditStatus;

    @NotNull
    private Integer isActive;

    @NotNull
    private Integer sortOrder;
}
