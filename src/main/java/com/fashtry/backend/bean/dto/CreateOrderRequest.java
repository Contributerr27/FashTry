package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建充值订单请求
 */
@Data
public class CreateOrderRequest {

    /** 套餐 ID */
    @NotNull(message = "套餐不能为空")
    private Integer packageId;

    /** 支付方式: wxpay, alipay, paypal, usdt_tron(TRON), usdt_bsc(BSC), lemonsqueezy(Lemon Squeezy 托管结账) */
    @NotNull(message = "支付方式不能为空")
    private String payChannel;

    /** 语言：zh-CNY, en-USD（PayPal 时使用 USD） */
    private String locale;

    /**
     * 微信小程序登录 code（仅小程序内微信充值时传，用于换 openid 并走 JSAPI 下单；H5/其它端不要传，仍返回 Native 的 code_url）
     */
    private String wxLoginCode;
}
