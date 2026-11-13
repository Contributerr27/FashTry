package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 平台审核品牌入驻：仅允许设为已通过(2)或已驳回(3)
 */
@Data
public class AdminBrandAuditRequest {

    @NotNull
    private Integer auditStatus;

    /** 审核备注/驳回原因 */
    private String remark;
}
