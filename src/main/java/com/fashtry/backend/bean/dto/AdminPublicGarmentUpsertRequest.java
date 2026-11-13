package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminPublicGarmentUpsertRequest {

    @NotBlank
    private String name;

    /**
     * 英文展示名称（可选）
     */
    private String nameEn;

    /**
     * wedding, workplace, campus, gu_feng, trendy, western
     */
    @NotBlank
    private String sceneCategory;

    /** male, female, unisex */
    @NotBlank
    private String gender;

    /** tops, bottoms, one-pieces，可空 */
    private String garmentType;

    @NotBlank
    private String imageUrl;

    private String thumbUrl;

    private String remark;

    /** 购买链接，可选 */
    private String purchaseUrl;

    @NotNull
    private Integer isActive;

    @NotNull
    private Integer sortOrder;
}
