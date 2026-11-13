package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 订单状态查询返回
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderStatusVO {

    /** 订单 ID */
    private Long orderId;

    /** 订单状态: 0-待支付, 1-已支付, 2-支付超时 */
    private Integer status;

    /** 状态文案 */
    private String statusText;

    /** 支付截止时间（待支付时有效，用于前端倒计时） */
    private LocalDateTime expireAt;
}
