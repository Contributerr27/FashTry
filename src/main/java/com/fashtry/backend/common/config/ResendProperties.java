package com.fashtry.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Resend 邮件（验证码登录）；api-key 为空时不发信。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.resend")
public class ResendProperties {

    /** Resend API Key，勿提交到仓库，用环境变量或本地配置覆盖 */
    private String apiKey = "";

    /**
     * 发件人，需与 Resend 已验证域名一致；测试可用 onboarding@resend.dev
     */
    private String from = "FashTry <onboarding@resend.dev>";
}
