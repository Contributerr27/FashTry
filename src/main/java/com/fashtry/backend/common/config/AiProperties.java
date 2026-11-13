package com.fashtry.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private String callbackBaseUrl;
    private String fashnApiBaseUrl;
    private String fashnApiKey;
}
