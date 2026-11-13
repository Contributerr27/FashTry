package com.fashtry.backend.common.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Gemini Developer API：仅用 API Key，不使用 Vertex AI / GCP 服务账号。
 */
@Configuration
@EnableConfigurationProperties(GeminiApiProperties.class)
public class GeminiApiConfig {

    private static final Logger log = LoggerFactory.getLogger(GeminiApiConfig.class);

    /**
     * 兼容旧的单 Key 客户端。实际出图走 {@link com.fashtry.backend.service.ai.GeminiKeyPool}，
     * 配置文件没写 Key 时也能启动，改由后台密钥池提供。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.gemini.enabled", havingValue = "true")
    public Client geminiClient(GeminiApiProperties props) {
        int ms = props.getHttpTimeoutMs() > 0 ? props.getHttpTimeoutMs() : 300_000;
        HttpOptions http = HttpOptions.builder().timeout(ms).build();
        if (!StringUtils.hasText(props.getApiKey())) {
            log.warn("app.gemini.api-key 为空，请在后台「Gemini Key」中配置");
            return Client.builder().apiKey("unconfigured").httpOptions(http).build();
        }
        return Client.builder().apiKey(props.getApiKey().trim()).httpOptions(http).build();
    }
}
