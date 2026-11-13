package com.fashtry.backend.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Token 存储服务：将 accessToken 存 Redis，用于校验与登出
 */
@Service
public class TokenStorageService {

    private static final String KEY_PREFIX = "auth:token:";
    private static final String ADMIN_KEY_PREFIX = "auth:admin:token:";

    private final StringRedisTemplate redisTemplate;

    public TokenStorageService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 保存 accessToken，value 为 userId
     */
    public void saveToken(String token, Long userId, long expireSeconds) {
        String key = KEY_PREFIX + token;
        redisTemplate.opsForValue().set(key, String.valueOf(userId), Duration.ofSeconds(expireSeconds));
    }

    /**
     * 删除 token（登出时调用）
     */
    public void deleteToken(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }

    /**
     * 校验 token 是否有效（存在于 Redis）
     */
    public boolean existsToken(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }

    public void saveAdminToken(String token, Long adminId, long expireSeconds) {
        String key = ADMIN_KEY_PREFIX + token;
        redisTemplate.opsForValue().set(key, String.valueOf(adminId), Duration.ofSeconds(expireSeconds));
    }

    public void deleteAdminToken(String token) {
        redisTemplate.delete(ADMIN_KEY_PREFIX + token);
    }

    public boolean existsAdminToken(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ADMIN_KEY_PREFIX + token));
    }
}
