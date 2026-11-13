package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("brands")
public class Brand {

    /** 雪花 ID（MyBatis-Plus ASSIGN_ID），非数据库自增 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;

    @TableField("brand_name")
    private String brandName;

    @TableField("brand_name_en")
    private String brandNameEn;

    @TableField("company_name")
    private String companyName;

    @TableField("company_name_en")
    private String companyNameEn;

    @TableField("logo_url")
    private String logoUrl;

    @TableField("unified_social_credit_code")
    private String unifiedSocialCreditCode;

    @TableField("business_license_url")
    private String businessLicenseUrl;

    @TableField("legal_name")
    private String legalName;

    @TableField("legal_id_front_url")
    private String legalIdFrontUrl;

    @TableField("legal_id_back_url")
    private String legalIdBackUrl;

    @TableField("legal_id_no_masked")
    private String legalIdNoMasked;

    @TableField("legal_mobile")
    private String legalMobile;

    @TableField("contact_email")
    private String contactEmail;

    @TableField("company_address")
    private String companyAddress;

    private BigDecimal longitude;

    private BigDecimal latitude;

    @TableField("location_precision")
    private String locationPrecision;

    @TableField("audit_status")
    private Integer auditStatus;

    private Integer status;

    /** C 端品牌入驻提交人（users.id），仅 join-* 入驻流程使用 */
    @TableField("intake_user_id")
    private Long intakeUserId;

    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
