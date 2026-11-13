package com.fashtry.backend.service.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fashtry.backend.bean.db.UserFavorite;
import com.fashtry.backend.bean.dto.FavoriteAddRequest;
import com.fashtry.backend.bean.vo.FavoriteItemVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.UserFavoriteMapper;
import com.fashtry.backend.utils.SnowflakeIdGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 我的作品 → 收藏：按用户 + 图片 URL 去重。
 */
@Service
public class FavoriteService {

    private static final int MAX_PAGE_SIZE = 60;
    private static final Set<String> SOURCE_TYPES =
            Set.of("tryon", "image_edit", "packshot", "suite", "retouch", "portrait");

    private final UserFavoriteMapper favoriteMapper;
    private final SnowflakeIdGenerator idGenerator;

    public FavoriteService(UserFavoriteMapper favoriteMapper, SnowflakeIdGenerator idGenerator) {
        this.favoriteMapper = favoriteMapper;
        this.idGenerator = idGenerator;
    }

    public PageResult<FavoriteItemVO> list(Long userId, int page, int size, String sourceType) {
        int p = Math.max(1, page);
        int s = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        LambdaQueryWrapper<UserFavorite> q =
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getCreatedAt)
                        .orderByDesc(UserFavorite::getId);
        String st = normalizeSourceType(sourceType);
        if (st != null) {
            q.eq(UserFavorite::getSourceType, st);
        }
        Page<UserFavorite> res = favoriteMapper.selectPage(new Page<>(p, s), q);
        List<FavoriteItemVO> rows = res.getRecords().stream().map(FavoriteService::toVO).toList();
        return PageResult.of(rows, res.getTotal(), p, s);
    }

    public FavoriteItemVO add(Long userId, FavoriteAddRequest body) {
        String url = requireHttpUrl(body.getImageUrl());
        String hash = sha256(url);
        UserFavorite existing = findByHash(userId, hash);
        if (existing != null) {
            return toVO(existing);
        }
        UserFavorite f = new UserFavorite();
        f.setId(idGenerator.nextId());
        f.setUserId(userId);
        f.setImageUrl(url);
        f.setUrlHash(hash);
        f.setThumbUrl(optionalHttpUrl(body.getThumbUrl()));
        f.setSourceType(normalizeSourceType(body.getSourceType()));
        f.setSourceTaskId(trimToNull(body.getSourceTaskId()));
        f.setTitle(trimToNull(body.getTitle()));
        f.setCreatedAt(LocalDateTime.now());
        try {
            favoriteMapper.insert(f);
        } catch (DuplicateKeyException e) {
            UserFavorite again = findByHash(userId, hash);
            if (again != null) {
                return toVO(again);
            }
            throw e;
        }
        return toVO(f);
    }

    public void removeByUrl(Long userId, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "imageUrl 不能为空");
        }
        favoriteMapper.delete(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getUrlHash, sha256(imageUrl.trim())));
    }

    public void removeById(Long userId, Long id) {
        favoriteMapper.delete(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getId, id));
    }

    /** 返回入参中已收藏的 URL（保持入参顺序） */
    public List<String> check(Long userId, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }
        Map<String, String> hashToUrl = new HashMap<>();
        Set<String> ordered = new LinkedHashSet<>();
        for (String u : imageUrls) {
            if (u == null || u.isBlank() || u.length() > 1024) {
                continue;
            }
            String url = u.trim();
            ordered.add(url);
            hashToUrl.put(sha256(url), url);
        }
        if (hashToUrl.isEmpty()) {
            return List.of();
        }
        Set<String> hit = new java.util.HashSet<>();
        favoriteMapper
                .selectList(
                        new LambdaQueryWrapper<UserFavorite>()
                                .select(UserFavorite::getUrlHash)
                                .eq(UserFavorite::getUserId, userId)
                                .in(UserFavorite::getUrlHash, hashToUrl.keySet()))
                .forEach(f -> {
                    String url = hashToUrl.get(f.getUrlHash());
                    if (url != null) {
                        hit.add(url);
                    }
                });
        return ordered.stream().filter(hit::contains).toList();
    }

    private UserFavorite findByHash(Long userId, String hash) {
        return favoriteMapper.selectOne(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getUrlHash, hash)
                        .last("LIMIT 1"));
    }

    private static FavoriteItemVO toVO(UserFavorite f) {
        return FavoriteItemVO.builder()
                .id(f.getId())
                .imageUrl(f.getImageUrl())
                .thumbUrl(f.getThumbUrl())
                .sourceType(f.getSourceType())
                .sourceTaskId(f.getSourceTaskId())
                .title(f.getTitle())
                .createdAt(f.getCreatedAt())
                .build();
    }

    private static String requireHttpUrl(String raw) {
        String u = trimToNull(raw);
        if (u == null || !(u.startsWith("https://") || u.startsWith("http://")) || u.length() > 1024) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "图片 URL 无效");
        }
        return u;
    }

    private static String optionalHttpUrl(String raw) {
        String u = trimToNull(raw);
        if (u == null || !(u.startsWith("https://") || u.startsWith("http://")) || u.length() > 1024) {
            return null;
        }
        return u;
    }

    private static String normalizeSourceType(String raw) {
        String s = trimToNull(raw);
        if (s == null) {
            return null;
        }
        s = s.toLowerCase();
        return SOURCE_TYPES.contains(s) ? s : null;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
