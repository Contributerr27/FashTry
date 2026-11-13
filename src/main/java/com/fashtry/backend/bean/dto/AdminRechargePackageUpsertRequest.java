package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminRechargePackageUpsertRequest {

    private Integer id;

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    private String nameEn;

    private BigDecimal price;

    private BigDecimal priceUsd;

    @NotNull
    private Integer credits;

    private BigDecimal unitPrice;

    private BigDecimal cost;

    private Boolean firstPurchaseOnly;

    private Integer expirationDays;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer sortOrder;

    @NotNull
    private Boolean active;

    private String strategyTag;

    private String strategyTagEn;
}
