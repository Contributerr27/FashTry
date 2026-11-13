package com.fashtry.backend.pay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lemon Squeezy（托管结账 + Webhook）
 * <p>配置前缀：{@code lemon-squeezy}</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "lemon-squeezy")
public class LemonSqueezyProperties {

    /** Store ID（仪表盘 Stores 数字 id；走 Checkout API 时必填） */
    private Long id;

    /**
     * Product ID（与 variant-by-package-id 中各 variant 同属一个 Lemon Squeezy Product 时填写）。
     * 与 api-key、id（Store）齐配时走「创建 Checkout」接口，结账页仅展示 enabled_variants 中的档位。
     */
    private Long productId;

    /** API Key（创建 Checkout 单档结账时必填；仅托管直链时可不配） */
    private String apiKey = "";

    /** 例如 https://fashtry.lemonsqueezy.com/checkout/buy（勿带尾 UUID） */
    private String checkoutBaseUrl = "";

    /**
     * 与 recharge_package.id 对应，值为 Hosted Checkout 路径中的 variant UUID
     */
    private Map<String, String> variantByPackageId = new LinkedHashMap<>();

    /** Webhook Signing Secret（未配置时拒绝回调验签） */
    private String webhookSigningSecret = "";
}
