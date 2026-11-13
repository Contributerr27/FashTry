package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值订单记录（列表展示）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderRecordVO {

    private Long id;
    private String orderNo;
    private BigDecimal amount;
    private String currency;
    private Integer creditsBought;
    private String payChannel;
    private Integer status;
    private String statusText;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
