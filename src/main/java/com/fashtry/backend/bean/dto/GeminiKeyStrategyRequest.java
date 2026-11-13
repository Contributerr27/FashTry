package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GeminiKeyStrategyRequest {

    /** ROUND_ROBIN | FAILOVER | RANDOM */
    @NotBlank
    private String strategy;
}
