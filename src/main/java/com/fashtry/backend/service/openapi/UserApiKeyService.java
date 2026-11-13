package com.fashtry.backend.service.openapi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fashtry.backend.bean.db.UserApiKey;
import com.fashtry.backend.bean.vo.UserApiKeyCreatedVO;
import com.fashtry.backend.bean.vo.UserApiKeyVO;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.UserApiKeyMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserApiKeyService {

    private static final String KEY_PREFIX = "fsk_";
    private static final int MAX_KEYS_PER_USER = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserApiKeyMapper userApiKeyMapper;

    public UserApiKeyService(UserApiKeyMapper userApiKeyMapper) {
        this.userApiKeyMapper = userApiKeyMapper;
    }

    public List<UserApiKeyVO> listForUser(Long userId) {
        LambdaQueryWrapper<UserApiKey> w = new LambdaQueryWrapper<>();
        w.eq(UserApiKey::getUserId, userId)
                .eq(UserApiKey::getEnabled, 1)
                .orderByDesc(UserApiKey::getId);
        return userApiKeyMapper.selectList(w).stream().map(this::toVo).toList();
    }

    public UserApiKeyCreatedVO create(Long userId, String name) {
        long count = countEnabled(userId);
        if (count >= MAX_KEYS_PER_USER) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "最多创建 " + MAX_KEYS_PER_USER + " 个 API 密钥");
        }
        String rawKey = generateRawKey();
        UserApiKey row = new UserApiKey();
        row.setUserId(userId);
        row.setName(StringUtils.hasText(name) ? name.trim() : "Default");
        row.setKeyPrefix(maskPrefix(rawKey));
        row.setKeyHash(hashKey(rawKey));
        row.setEnabled(1);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        userApiKeyMapper.insert(row);
        return UserApiKeyCreatedVO.builder()
                .id(row.getId())
                .name(row.getName())
                .apiKey(rawKey)
                .createdAt(row.getCreatedAt())
                .build();
    }

    public void revoke(Long userId, Long keyId) {
        UserApiKey row = userApiKeyMapper.selectById(keyId);
        if (row == null || !userId.equals(row.getUserId())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "密钥不存在");
        }
        if (row.getEnabled() != null && row.getEnabled() == 0) {
            return;
        }
        LambdaUpdateWrapper<UserApiKey> w = new LambdaUpdateWrapper<>();
        w.eq(UserApiKey::getId, keyId)
                .eq(UserApiKey::getUserId, userId)
                .set(UserApiKey::getEnabled, 0)
                .set(UserApiKey::getUpdatedAt, LocalDateTime.now());
        userApiKeyMapper.update(null, w);
    }

    /** 校验开放 API 密钥，返回所属用户 ID */
    public Optional<Long> authenticate(String rawKey) {
        if (!StringUtils.hasText(rawKey) || !rawKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        String hash = hashKey(rawKey.trim());
        LambdaQueryWrapper<UserApiKey> w = new LambdaQueryWrapper<>();
        w.eq(UserApiKey::getKeyHash, hash).eq(UserApiKey::getEnabled, 1).last("LIMIT 1");
        UserApiKey row = userApiKeyMapper.selectOne(w);
        if (row == null) {
            return Optional.empty();
        }
        touchLastUsed(row.getId());
        return Optional.of(row.getUserId());
    }

    private long countEnabled(Long userId) {
        LambdaQueryWrapper<UserApiKey> w = new LambdaQueryWrapper<>();
        w.eq(UserApiKey::getUserId, userId).eq(UserApiKey::getEnabled, 1);
        return userApiKeyMapper.selectCount(w);
    }

    private void touchLastUsed(Long id) {
        LambdaUpdateWrapper<UserApiKey> w = new LambdaUpdateWrapper<>();
        w.eq(UserApiKey::getId, id)
                .set(UserApiKey::getLastUsedAt, LocalDateTime.now())
                .set(UserApiKey::getUpdatedAt, LocalDateTime.now());
        userApiKeyMapper.update(null, w);
    }

    private UserApiKeyVO toVo(UserApiKey row) {
        return UserApiKeyVO.builder()
                .id(row.getId())
                .name(row.getName())
                .keyPrefix(row.getKeyPrefix())
                .lastUsedAt(row.getLastUsedAt())
                .createdAt(row.getCreatedAt())
                .build();
    }

    static String generateRawKey() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return KEY_PREFIX + HexFormat.of().formatHex(bytes);
    }

    static String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static String maskPrefix(String rawKey) {
        if (rawKey == null || rawKey.length() <= KEY_PREFIX.length() + 4) {
            return rawKey;
        }
        return rawKey.substring(0, KEY_PREFIX.length() + 8) + "****";
    }
}
