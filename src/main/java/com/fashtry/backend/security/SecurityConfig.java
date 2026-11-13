package com.fashtry.backend.security;

import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.common.enums.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import com.fashtry.backend.common.config.OpenApiProperties;
import com.fashtry.backend.service.openapi.OpenApiIpAllowlistService;
import com.fashtry.backend.service.openapi.UserApiKeyService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 配置
 * - 放行 /api/auth/** 登录相关接口
 * - 放行 /api/public/** 公开接口
 * - /api/open/** 由 OpenApiBillingAuthenticationFilter 校验 API 密钥或 IP 白名单
 * - 其余接口需 JWT 认证
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OpenApiProperties openApiProperties;
    private final OpenApiIpAllowlistService openApiIpAllowlistService;
    private final UserApiKeyService userApiKeyService;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            OpenApiProperties openApiProperties,
            OpenApiIpAllowlistService openApiIpAllowlistService,
            UserApiKeyService userApiKeyService,
            ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.openApiProperties = openApiProperties;
        this.openApiIpAllowlistService = openApiIpAllowlistService;
        this.userApiKeyService = userApiKeyService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/common/**", "/api/public/**", "/api/recharge/packages",
                                "/api/pay/notify/**", "/actuator/health").permitAll()
                        .requestMatchers("/api/admin/auth/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> writeJson(res, ResultCode.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, e) -> writeJson(res, ResultCode.FORBIDDEN))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(openApiBillingAuthenticationFilter(), JwtAuthenticationFilter.class)
                .build();
    }

    private OpenApiBillingAuthenticationFilter openApiBillingAuthenticationFilter() {
        return new OpenApiBillingAuthenticationFilter(
                openApiProperties, openApiIpAllowlistService, userApiKeyService, objectMapper);
    }

    private void writeJson(HttpServletResponse response, ResultCode resultCode) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(resultCode)));
    }
}
