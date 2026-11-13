package com.fashtry.backend.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fashtry.backend.bean.db.GeminiApiKey;
import com.fashtry.backend.bean.db.GeminiKeyPolicy;
import com.fashtry.backend.bean.dto.GeminiApiKeyUpsertRequest;
import com.fashtry.backend.bean.vo.GeminiApiKeyVO;
import com.fashtry.backend.bean.vo.GeminiKeyProbeVO;
import com.fashtry.backend.bean.vo.GeminiKeySettingsVO;
import com.fashtry.backend.common.config.GeminiApiProperties;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.GeminiApiKeyMapper;
import com.fashtry.backend.mapper.GeminiKeyPolicyMapper;
import com.fashtry.backend.service.ai.GeminiKeyPool;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class GeminiKeyAdminService {

    private static final String BALANCE_NOTE = "Gemini Developer API 不提供余额查询，这里只能检测 Key 是否可用。";
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GeminiApiKeyMapper keyMapper;
    private final GeminiKeyPolicyMapper policyMapper;
    private final GeminiApiProperties props;
    private final GeminiKeyPool pool;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();

    public GeminiKeyAdminService(
            GeminiApiKeyMapper keyMapper,
            GeminiKeyPolicyMapper policyMapper,
            GeminiApiProperties props,
            GeminiKeyPool pool) {
        this.keyMapper = keyMapper;
        this.policyMapper = policyMapper;
        this.props = props;
        this.pool = pool;
    }

    public GeminiKeySettingsVO settings() {
        List<GeminiApiKey> rows = listRows();
        boolean anyEnabled = rows.stream().anyMatch(r -> r.getEnabled() == null || r.getEnabled() == 1);
        List<GeminiApiKeyVO> vos = new ArrayList<>();
        for (GeminiApiKey row : rows) {
            vos.add(toVo(row));
        }
        return GeminiKeySettingsVO.builder()
                .strategy(currentStrategy())
                .yamlFallback(!anyEnabled && StringUtils.hasText(props.getApiKey()))
                .balanceNote(BALANCE_NOTE)
                .keys(vos)
                .build();
    }

    public void updateStrategy(String strategy) {
        if (!GeminiKeyPool.isStrategy(strategy)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "策略只能是轮询、故障转移或随机");
        }
        GeminiKeyPolicy row = policyMapper.selectById(1);
        if (row == null) {
            row = new GeminiKeyPolicy();
            row.setId(1);
            row.setStrategy(strategy);
            policyMapper.insert(row);
        } else {
            row.setStrategy(strategy);
            policyMapper.updateById(row);
        }
        pool.reload();
    }

    public void create(GeminiApiKeyUpsertRequest body) {
        String key = body.getApiKey() == null ? "" : body.getApiKey().trim();
        if (key.length() < 10) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请填写 API Key");
        }
        GeminiApiKey row = new GeminiApiKey();
        row.setName(body.getName().trim());
        row.setApiKey(key);
        row.setEnabled(body.getEnabled() == null || body.getEnabled() ? 1 : 0);
        row.setSortOrder(body.getSortOrder() == null ? 0 : body.getSortOrder());
        row.setFailCount(0);
        row.setSuccessCount(0);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        keyMapper.insert(row);
        pool.reload();
    }

    public void update(long id, GeminiApiKeyUpsertRequest body) {
        GeminiApiKey row = require(id);
        row.setName(body.getName().trim());
        if (StringUtils.hasText(body.getApiKey())) {
            String key = body.getApiKey().trim();
            if (key.length() < 10) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "API Key 太短");
            }
            row.setApiKey(key);
        }
        if (body.getEnabled() != null) {
            row.setEnabled(body.getEnabled() ? 1 : 0);
        }
        if (body.getSortOrder() != null) {
            row.setSortOrder(body.getSortOrder());
        }
        row.setUpdatedAt(LocalDateTime.now());
        keyMapper.updateById(row);
        pool.reload();
    }

    public void delete(long id) {
        require(id);
        keyMapper.deleteById(id);
        pool.clearCooldown(id);
        pool.reload();
    }

    public void clearCooldown(long id) {
        require(id);
        keyMapper.update(
                null,
                new LambdaUpdateWrapper<GeminiApiKey>()
                        .eq(GeminiApiKey::getId, id)
                        .set(GeminiApiKey::getCooldownUntil, null)
                        .set(GeminiApiKey::getUpdatedAt, LocalDateTime.now()));
        pool.clearCooldown(id);
        pool.reload();
    }

    public GeminiKeyProbeVO probe(long id) {
        GeminiApiKey row = require(id);
        GeminiKeyProbeVO vo = probeKey(row.getApiKey());
        if (vo.isOk()) {
            int n = row.getSuccessCount() == null ? 0 : row.getSuccessCount();
            keyMapper.update(
                    null,
                    new LambdaUpdateWrapper<GeminiApiKey>()
                            .eq(GeminiApiKey::getId, id)
                            .set(GeminiApiKey::getLastOkAt, LocalDateTime.now())
                            .set(GeminiApiKey::getCooldownUntil, null)
                            .set(GeminiApiKey::getSuccessCount, n + 1)
                            .set(GeminiApiKey::getUpdatedAt, LocalDateTime.now()));
            pool.clearCooldown(id);
            pool.reload();
            return vo;
        } else {
            row.setLastError(trim(vo.getMessage(), 480));
            row.setLastErrorAt(LocalDateTime.now());
            int n = row.getFailCount() == null ? 0 : row.getFailCount();
            row.setFailCount(n + 1);
        }
        row.setUpdatedAt(LocalDateTime.now());
        keyMapper.updateById(row);
        pool.reload();
        return vo;
    }

    private GeminiKeyProbeVO probeKey(String apiKey) {
        try {
            String url =
                    "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1&key="
                            + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            HttpRequest req =
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code == 200) {
                return GeminiKeyProbeVO.builder().ok(true).httpStatus(code).message("Key 可用").build();
            }
            if (code == 429) {
                return GeminiKeyProbeVO.builder().ok(false).httpStatus(code).message("频率或额度受限").build();
            }
            if (code == 400 || code == 401 || code == 403) {
                return GeminiKeyProbeVO.builder().ok(false).httpStatus(code).message("Key 无效或无权限").build();
            }
            return GeminiKeyProbeVO.builder().ok(false).httpStatus(code).message("检测失败 HTTP " + code).build();
        } catch (Exception e) {
            return GeminiKeyProbeVO.builder().ok(false).httpStatus(0).message("检测请求失败").build();
        }
    }

    private List<GeminiApiKey> listRows() {
        try {
            return keyMapper.selectList(
                    new LambdaQueryWrapper<GeminiApiKey>()
                            .orderByAsc(GeminiApiKey::getSortOrder)
                            .orderByAsc(GeminiApiKey::getId));
        } catch (Exception e) {
            throw new BusinessException(
                    ResultCode.SYSTEM_ERROR, "Gemini Key 表不存在，请先执行 migration-gemini-api-keys.sql");
        }
    }

    private String currentStrategy() {
        try {
            GeminiKeyPolicy row = policyMapper.selectById(1);
            if (row != null && GeminiKeyPool.isStrategy(row.getStrategy())) {
                return row.getStrategy();
            }
        } catch (Exception ignored) {
            /* 表未建时由 listRows 报错 */
        }
        return GeminiKeyPool.ROUND_ROBIN;
    }

    private GeminiApiKey require(long id) {
        GeminiApiKey row = keyMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "Key 不存在");
        }
        return row;
    }

    private static GeminiApiKeyVO toVo(GeminiApiKey row) {
        return GeminiApiKeyVO.builder()
                .id(row.getId())
                .name(row.getName())
                .keyMask(GeminiKeyPool.mask(row.getApiKey()))
                .enabled(row.getEnabled() == null || row.getEnabled() == 1)
                .sortOrder(row.getSortOrder() == null ? 0 : row.getSortOrder())
                .cooldownUntil(fmt(row.getCooldownUntil()))
                .lastError(row.getLastError())
                .lastErrorAt(fmt(row.getLastErrorAt()))
                .lastOkAt(fmt(row.getLastOkAt()))
                .failCount(row.getFailCount() == null ? 0 : row.getFailCount())
                .successCount(row.getSuccessCount() == null ? 0 : row.getSuccessCount())
                .build();
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : DT.format(t);
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
