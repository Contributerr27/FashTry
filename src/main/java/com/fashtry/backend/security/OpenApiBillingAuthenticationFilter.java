package com.fashtry.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.common.config.OpenApiProperties;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.service.openapi.OpenApiIpAllowlistService;
import com.fashtry.backend.service.openapi.UserApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /api/open/**：优先校验用户 API 密钥（{@code X-Api-Key} 或 {@code Authorization: Bearer fsk_*}）；
 * 否则回退 IP 白名单 + 统一计费用户（兼容旧集成）。
 */
public class OpenApiBillingAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "/api/open/";
    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final String KEY_PREFIX = "fsk_";

    private final OpenApiProperties openApiProperties;
    private final OpenApiIpAllowlistService openApiIpAllowlistService;
    private final UserApiKeyService userApiKeyService;
    private final ObjectMapper objectMapper;

    public OpenApiBillingAuthenticationFilter(
            OpenApiProperties openApiProperties,
            OpenApiIpAllowlistService openApiIpAllowlistService,
            UserApiKeyService userApiKeyService,
            ObjectMapper objectMapper) {
        this.openApiProperties = openApiProperties;
        this.openApiIpAllowlistService = openApiIpAllowlistService;
        this.userApiKeyService = userApiKeyService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<Long> keyUserId = resolveApiKeyUserId(request);
        if (keyUserId.isPresent()) {
            injectUser(keyUserId.get(), "api-key");
            try {
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
            return;
        }

        if (!openApiIpAllowlistService.isClientIpAllowed(request)) {
            writeJson(response, Result.error(ResultCode.FORBIDDEN, "无效 API 密钥或 IP 不在白名单"));
            return;
        }

        Long billingUserId = openApiProperties.getBillingUserId();
        if (billingUserId == null) {
            writeJson(response, Result.error(ResultCode.UNAUTHORIZED, "请提供有效的 API 密钥（X-Api-Key）"));
            return;
        }

        injectUser(billingUserId, "open-api");
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<Long> resolveApiKeyUserId(HttpServletRequest request) {
        String fromHeader = request.getHeader(API_KEY_HEADER);
        if (StringUtils.hasText(fromHeader)) {
            return userApiKeyService.authenticate(fromHeader.trim());
        }
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            String token = auth.substring(7).trim();
            if (token.startsWith(KEY_PREFIX)) {
                return userApiKeyService.authenticate(token);
            }
        }
        return Optional.empty();
    }

    private void injectUser(Long userId, String label) {
        LoginUser billing =
                LoginUser.builder()
                        .userId(userId)
                        .username(label)
                        .nickname(label)
                        .role("USER")
                        .password("")
                        .enabled(true)
                        .build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(billing, null, billing.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void writeJson(HttpServletResponse response, Result<?> body) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
