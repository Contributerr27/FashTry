package com.fashtry.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付相关配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.pay")
public class PayProperties {

    /** 支付回调基础地址 */
    private String notifyBaseUrl = "https://fashtry.com";

    /** 支付超时时间（分钟），超时后订单自动关闭 */
    private int paymentTimeoutMinutes = 10;
}
