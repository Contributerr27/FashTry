package com.fashtry.backend.bean.vo;

import lombok.Data;

/**
 * C 端品牌入驻：公开可查询字段（不含联系人电话等敏感信息）
 */
@Data
public class BrandIntakePublicVO {

    private Long id;
    private String code;
    private String brandName;
    /** 品牌英文名称（可选） */
    private String brandNameEn;
    private String companyName;
    /** 与 brands.audit_status 一致：0 待完善 1 待审核 2 已通过 3 已驳回 */
    private Integer auditStatus;
    /** 与 brands.status 一致：品牌启用状态 */
    private Integer status;
    /** 驳回原因（audit_status=3 时来自后台审核备注；未填写或与入驻默认备注相同时为 null） */
    private String rejectReason;
}
