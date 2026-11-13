package com.fashtry.backend.security;

import com.fashtry.backend.common.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String createAccessToken(Long userId, String username, String role) {
        return createToken(userId, username, role, jwtProperties.getAccessTokenExpireSeconds());
    }

    public String createRefreshToken(Long userId, String username, String role) {
        return createToken(userId, username, role, jwtProperties.getRefreshTokenExpireSeconds());
    }

    /**
     * 后台管理员 accessToken，claims 含 aid（与前台 uid 区分）
     */
    public String createAdminAccessToken(Long adminId, String username) {
        Instant now = Instant.now();
        // 后台管理 token 采用更长的有效期（与 refreshToken 一致：默认 7 天）
        Instant exp = now.plusSeconds(jwtProperties.getRefreshTokenExpireSeconds());
        String role = "ADMIN";
        Map<String, Object> claims = Map.of(
                "aid", adminId,
                "uname", username,
                "role", role
        );
        return Jwts.builder()
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String createToken(Long userId, String username, String role, Long expireSeconds) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expireSeconds);
        Map<String, Object> claims = Map.of(
                "uid", userId,
                "uname", username,
                "role", role
        );
        return Jwts.builder()
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey())
                .compact();
    }

    private SecretKey signingKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        // HS256 要求密钥至少 256 位(32 字节)，不足时用 SHA-256 哈希扩展
        if (keyBytes.length < 32) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                keyBytes = digest.digest(keyBytes);
            } catch (Exception e) {
                throw new RuntimeException("JWT 密钥处理失败", e);
            }
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}
