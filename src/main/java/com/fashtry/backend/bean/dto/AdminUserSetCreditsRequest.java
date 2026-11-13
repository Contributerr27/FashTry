package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUserSetCreditsRequest {

    /** 调整后的钱包总点数（含冻结占用前的总额；须 ≥ 冻结点数） */
    @NotNull
    @Min(0)
    @Max(99_999_999)
    private Integer totalCredits;
}
