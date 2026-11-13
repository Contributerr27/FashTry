package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminBrandUpsertRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String brandName;

    private String brandNameEn;

    private String companyName;

    private String companyNameEn;

    private String logoUrl;

    private String unifiedSocialCreditCode;

    private String businessLicenseUrl;

    private String legalName;

    private String legalIdFrontUrl;

    private String legalIdBackUrl;

    private String legalIdNoMasked;

    private String legalMobile;

    private String contactEmail;

    private String companyAddress;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String locationPrecision;

    /** 入驻审核：0-待完善 1-待审核 2-已通过 3-已驳回（新建可传，默认待审核） */
    private Integer auditStatus;

    /** 1-启用 0-停用 */
    private Integer status;

    private String remark;
}
