package com.fashtry.backend.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 雪花 ID 等 Long 超出 JS 安全整数时，JSON 数字会丢精度。
 * 仅通过 serializerByType 扩展序列化，勿调用 {@code builder.modules(...)}，否则会覆盖 Spring Boot 默认模块（含 JavaTime），导致 LocalDateTime 无法写出。
 */
@Configuration
public class JacksonLongAsStringConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longAsStringJsonCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}
