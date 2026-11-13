package com.fashtry.backend.config;

import com.fashtry.backend.common.config.CorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * CORS：{@code dev} profile（或未显式指定 profile 时默认 dev）下<strong>允许任意 Origin</strong>（{@code *}），便于局域网 IP、多端联调。
     * <p>
     * {@code pro} 等非 dev 环境仅使用 {@code app.cors.allowed-origins}，勿在线上误用 dev profile。
     * </p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties, Environment env) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        String[] profiles = env.getActiveProfiles();
        boolean devLike =
                profiles.length == 0 || Arrays.stream(profiles).anyMatch(p -> "dev".equalsIgnoreCase(p));

        List<String> patterns = new ArrayList<>();
        if (devLike) {
            patterns.add("*");
        } else {
            List<String> configured = corsProperties.getAllowedOrigins();
            if (!CollectionUtils.isEmpty(configured)) {
                for (String o : configured) {
                    if (StringUtils.hasText(o)) {
                        patterns.add(o.trim());
                    }
                }
            }
        }
        if (patterns.isEmpty()) {
            return source;
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(patterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
