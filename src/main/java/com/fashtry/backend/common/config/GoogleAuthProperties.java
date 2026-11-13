package com.fashtry.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Google OAuth 2.0 配置
 * <p>
 * 在 Google Cloud Console 创建 OAuth 2.0 客户端（Web 应用）后获取：
 * https://console.cloud.google.com/apis/credentials
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.google")
public class GoogleAuthProperties {

    /** OAuth 2.0 客户端 ID（必填） */
    private String clientId;

    /** OAuth 2.0 客户端密钥（必填） */
    private String clientSecret;

    /** 回调基础 URL，如 https://fashtry.com，用于拼接 redirect_uri（必填，指向后端） */
    private String redirectBaseUrl;

    /**
     * 前端站点根 URL（前后端分离时必填），Google 登录成功后 302 到此前端下的 /login?googleSuccess=...
     * 本地示例：http://localhost:5173；生产示例：https://fashtry.com
     */
    private String frontendBaseUrl;

    /** 是否启用，未配置时禁用 */
    public boolean isEnabled() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && redirectBaseUrl != null && !redirectBaseUrl.isBlank();
    }
}
