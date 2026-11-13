package com.fashtry.backend.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStorageService tokenStorageService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, TokenStorageService tokenStorageService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenStorageService = tokenStorageService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token) && !looksLikeJwt(token)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (StringUtils.hasText(token)) {
            try {
                Claims claims = jwtTokenProvider.parseToken(token);
                Object aid = claims.get("aid");
                if (aid != null) {
                    if (!tokenStorageService.existsAdminToken(token)) {
                        SecurityContextHolder.clearContext();
                        filterChain.doFilter(request, response);
                        return;
                    }
                    String uname = claims.get("uname", String.class);
                    LoginUser loginUser = LoginUser.builder()
                            .userId(Long.valueOf(aid.toString()))
                            .username(uname)
                            .nickname(uname)
                            .role(claims.get("role", String.class))
                            .password("")
                            .enabled(true)
                            .build();
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                } else {
                    if (!tokenStorageService.existsToken(token)) {
                        SecurityContextHolder.clearContext();
                        filterChain.doFilter(request, response);
                        return;
                    }
                    String uname = claims.get("uname", String.class);
                    LoginUser loginUser = LoginUser.builder()
                            .userId(Long.valueOf(claims.get("uid").toString()))
                            .username(uname)
                            .nickname(uname)
                            .role(claims.get("role", String.class))
                            .password("")
                            .enabled(true)
                            .build();

                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(auth)) {
            return null;
        }
        if (auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return auth;
    }

    /** 避免将 fsk_* API 密钥当作 JWT 解析并清空 SecurityContext */
    private static boolean looksLikeJwt(String token) {
        if (token == null) {
            return false;
        }
        int dots = 0;
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) == '.') {
                dots++;
            }
        }
        return dots == 2;
    }
}
