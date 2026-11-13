package com.fashtry.backend.bean.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付查询结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayQueryResult {

    /** 是否已支付 */
    private boolean paid;

    /** 第三方交易号（微信 transaction_id / 支付宝 trade_no） */
    private String transactionId;
}
