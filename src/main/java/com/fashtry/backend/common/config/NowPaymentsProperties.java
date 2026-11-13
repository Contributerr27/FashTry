package com.fashtry.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * NOWPayments（USDT 等加密货币）配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "nowpayments")
public class NowPaymentsProperties {

    /** API Key（Dashboard → API keys） */
    private String apiKey = "";

    /** IPN 密钥（用于校验 x-nowpayments-sig） */
    private String ipnSecret = "";

    /** API 根地址，勿带末尾 / */
    private String baseUrl = "https://api.nowpayments.io/v1";

    /**
     * 低于此值的 USDT 金额会抬升到该值（NOWPayments 对 TRC20 等常有 ≥5 USDT 限制）
     */
    private BigDecimal minAmountUsd = new BigDecimal("5");

    /** TRC20 USDT 的 pay_currency（以 NOWPayments 后台可用币种为准） */
    private String payCurrencyTron = "usdttrc20";

    /** BSC 上 USDT 的 pay_currency */
    private String payCurrencyBsc = "usdtbsc";

    /** 生产环境必须为 true；本地无 IPN 时可临时 false（仅调试用，勿上生产） */
    private boolean verifyIpn = true;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String resolvePayCurrency(String payChannel) {
        if (payChannel == null) {
            return payCurrencyTron;
        }
        String c = payChannel.trim().toLowerCase();
        if ("usdt_bsc".equals(c)) {
            return payCurrencyBsc != null && !payCurrencyBsc.isBlank() ? payCurrencyBsc : "usdtbsc";
        }
        return payCurrencyTron != null && !payCurrencyTron.isBlank() ? payCurrencyTron : "usdttrc20";
    }
}
