package com.fashtry.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 第三方开放 API：用户 API 密钥（推荐）或 IP 白名单 + 统一计费用户。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.open-api")
public class OpenApiProperties {

    /**
     * 开放调用写入的任务归属用户 ID（用于扣点与任务归属；未配置时为 null，由业务层自行校验）。
     */
    private Long billingUserId;

    /**
     * 为 true 时使用 {@link com.fashtry.backend.utils.ClientIpUtils#getClientIp} 解析客户端 IP（x-forwarded-for、Proxy-Client-IP、
     * WL-Proxy-Client-IP、HTTP_CLIENT_IP、HTTP_X_FORWARDED_FOR、X-Real-IP、RemoteAddr；多 IP 取第一段）。在 Nginx/网关后通常需开启。
     * 为 false 时仅使用 {@code request.getRemoteAddr()}，避免未部署代理时客户端伪造头。
     */
    private boolean useForwardedFor = false;

    /** 白名单规则内存缓存秒数 */
    private int allowlistCacheTtlSeconds = 30;
}
