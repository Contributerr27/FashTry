package com.fashtry.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 腾讯云短信配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.sms")
public class TencentSmsProperties {

    private String secretId;
    private String secretKey;
    private String region = "ap-guangzhou";
    private String smsSdkAppId;
    private String signName;
    private String templateId;
    /** 国际/港澳台短信模板 ID（海外手机号注册用） */
    private String templateIdIntl;
    /** 国际短信签名（可选，不配置则用 signName） */
    private String signNameIntl;
    /** 国际短信 region，国际 API 仅支持 ap-singapore、eu-frankfurt */
    private String regionIntl = "ap-singapore";
}
