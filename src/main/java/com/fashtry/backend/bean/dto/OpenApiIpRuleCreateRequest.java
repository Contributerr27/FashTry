package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OpenApiIpRuleCreateRequest {

    @NotBlank(message = "ipRule 不能为空")
    /** IPv4 地址或 IPv4 CIDR，如 203.0.113.10 或 10.0.0.0/24 */
    private String ipRule;

    private String remark;
}
