package com.fashtry.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * RestTemplate 配置
 * <p>
 * 使用 SimpleClientHttpRequestFactory 避免与 weixin-java-pay、alipay-easysdk 引入的 HttpClient 冲突。
 * 连接超时 30s，读取超时 60s（Google OAuth 等国际接口可能需要更长时间）。
 * </p>
 * <p>
 * 响应体按 {@link String} 反序列化时，默认 {@link StringHttpMessageConverter} 在无 charset 时可能使用 ISO-8859-1，
 * 导致腾讯云 COS 数据万象返回的 UTF-8 XML 中文（如 Label）乱码。统一使用 UTF-8。
 * </p>
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(30));
        factory.setReadTimeout(Duration.ofSeconds(60));
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getMessageConverters().removeIf(c -> c instanceof StringHttpMessageConverter);
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return restTemplate;
    }
}
