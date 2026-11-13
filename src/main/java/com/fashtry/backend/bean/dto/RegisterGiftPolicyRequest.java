package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterGiftPolicyRequest {

    /** 每人注册赠送点数 */
    @NotNull
    @Min(0)
    @Max(1_000_000)
    private Integer perUserCredits;

    /** 每天赠送总额度 */
    @NotNull
    @Min(0)
    @Max(1_000_000)
    private Integer dailyTotalCredits;
}
