package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterAbusePolicyRequest {

    @NotNull
    @Min(1)
    @Max(10_080)
    private Integer ipWindowMinutes;

    /** 0 关闭 */
    @NotNull
    @Min(0)
    @Max(100_000)
    private Integer ipDenyFrom;

    @NotNull
    @Min(1)
    @Max(10_080)
    private Integer prefixWindowMinutes;

    /** 0 关闭 */
    @NotNull
    @Min(0)
    @Max(100_000)
    private Integer prefixDenyFrom;

    @NotNull
    @Min(1)
    @Max(10_080)
    private Integer globalWindowMinutes;

    /** 0 关闭 */
    @NotNull
    @Min(0)
    @Max(100_000)
    private Integer globalAllow;

    @NotNull
    @Min(1)
    @Max(10_080)
    private Integer smsWindowMinutes;

    /** 0 关闭 */
    @NotNull
    @Min(0)
    @Max(100_000)
    private Integer smsMaxPerIp;
}
