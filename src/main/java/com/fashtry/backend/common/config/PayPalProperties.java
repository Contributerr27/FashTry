package com.fashtry.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PayPal REST API 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "paypal")
public class PayPalProperties {

    private String clientId = "";
    private String clientSecret = "";
    /** 沙盒: https://api-m.sandbox.paypal.com  正式: https://api-m.paypal.com */
    private String baseUrl = "https://api-m.sandbox.paypal.com";

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
