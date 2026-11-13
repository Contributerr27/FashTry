package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建订单返回（含支付二维码）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderCreateVO {

    /** 订单 ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 支付方式 */
    private String payChannel;

    /** 支付金额 */
    private BigDecimal amount;

    /** 币种：CNY / USD */
    private String currency;

    /**
     * USDT 链上支付：用户需支付的 USDT 数量（与 NOWPayments price_amount 一致），非 USDT 渠道为 null
     */
    private BigDecimal cryptoPayAmount;

    /** 展示用，固定 USDT */
    private String cryptoPayCurrency;

    /** 获得点数 */
    private Integer credits;

    /** 支付二维码内容（微信为 code_url，支付宝为 qr_code，前端用此生成二维码） */
    private String codeUrl;

    /** 微信小程序内支付：调起 wx.requestPayment 的参数（小程序 + 传 wxLoginCode 时返回） */
    private WxPayMiniSignVO wxPay;

    /** 订单状态: 0-待支付 */
    private Integer status;

    /** 支付截止时间，超时后订单自动关闭 */
    private LocalDateTime expireAt;
}
