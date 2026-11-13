package com.fashtry.backend.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fashtry.backend.bean.db.GeminiApiKey;
import com.fashtry.backend.bean.db.GeminiKeyPolicy;
import com.fashtry.backend.common.config.GeminiApiProperties;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.GeminiApiKeyMapper;
import com.fashtry.backend.mapper.GeminiKeyPolicyMapper;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多把 Gemini API Key 的选用与故障转移。数据库里有启用的 Key 时只用数据库；否则回退 {@code app.gemini.api-key}。
 * 策略只决定第一把用谁；额度用尽、Key 无效、401/403/429 时自动换下一把。
 */
@Service
public class GeminiKeyPool {

    public static final String ROUND_ROBIN = "ROUND_ROBIN";
    public static final String FAILOVER = "FAILOVER";
    public static final String RANDOM = "RANDOM";

    /** 配置文件里的 Key，不入库 */
    public static final long YAML_KEY_ID = -1L;

    private static final Logger log = LoggerFactory.getLogger(GeminiKeyPool.class);
    private static final long CACHE_MS = 8_000L;
    private static final long COOLDOWN_SEC = 180L;
    private static final long OK_WRITE_INTERVAL_MS = 30_000L;

    private final GeminiApiKeyMapper keyMapper;
    private final GeminiKeyPolicyMapper policyMapper;
    private final GeminiApiProperties props;

    private final Object loadLock = new Object();
    private volatile List<Slot> slots = List.of();
    private volatile String strategy = ROUND_ROBIN;
    private volatile long loadedAt = 0L;
    private final AtomicInteger roundRobin = new AtomicInteger();
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final Map<Long, Long> cooldownUntilMs = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastOkWriteMs = new ConcurrentHashMap<>();

    public GeminiKeyPool(
            GeminiApiKeyMapper keyMapper,
            GeminiKeyPolicyMapper policyMapper,
            GeminiApiProperties props) {
        this.keyMapper = keyMapper;
        this.policyMapper = policyMapper;
        this.props = props;
    }

    public boolean isReady() {
        return props.isEnabled() && !plan().isEmpty();
    }

    public String strategy() {
        ensureFresh();
        return strategy;
    }

    /** 本次调用的尝试顺序：第一把按策略选出，其余作为故障转移 */
    public List<Slot> plan() {
        ensureFresh();
        List<Slot> enabled = new ArrayList<>();
        for (Slot s : slots) {
            if (s.enabled) {
                enabled.add(s);
            }
        }
        if (enabled.isEmpty()) {
            return List.of();
        }
        List<Slot> healthy = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Slot s : enabled) {
            if (!cooling(s.id, now)) {
                healthy.add(s);
            }
        }
        List<Slot> pool = healthy.isEmpty() ? enabled : healthy;
        int start = startIndex(pool);
        List<Slot> order = new ArrayList<>(pool.size());
        for (int i = 0; i < pool.size(); i++) {
            order.add(pool.get((start + i) % pool.size()));
        }
        if (healthy.size() < enabled.size()) {
            for (Slot s : enabled) {
                if (cooling(s.id, now)) {
                    order.add(s);
                }
            }
        }
        return order;
    }

    public Client client(Slot slot) {
        int ms = props.getHttpTimeoutMs() > 0 ? props.getHttpTimeoutMs() : 300_000;
        return clients.computeIfAbsent(
                slot.apiKey,
                k -> {
                    HttpOptions http = HttpOptions.builder().timeout(ms).build();
                    return Client.builder().apiKey(k).httpOptions(http).build();
                });
    }

    public <T> T call(GeminiClientCall<T> action) throws Exception {
        List<Slot> order = plan();
        if (order.isEmpty()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "未配置可用的 Gemini API Key");
        }
        Exception last = null;
        for (int i = 0; i < order.size(); i++) {
            Slot slot = order.get(i);
            try {
                T result = action.apply(client(slot));
                markOk(slot);
                return result;
            } catch (Exception e) {
                last = e;
                markBad(slot, e);
                if (!failoverWorthy(e) || i == order.size() - 1) {
                    throw e;
                }
                log.warn("Gemini key [{}] 调用失败，换下一把: {}", slot.name, shortMsg(e));
            }
        }
        throw last != null ? last : new IllegalStateException("Gemini 调用失败");
    }

    public void markOk(Slot slot) {
        cooldownUntilMs.remove(slot.id);
        if (slot.id <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = lastOkWriteMs.get(slot.id);
        if (prev != null && now - prev < OK_WRITE_INTERVAL_MS) {
            return;
        }
        lastOkWriteMs.put(slot.id, now);
        try {
            GeminiApiKey row = keyMapper.selectById(slot.id);
            if (row == null) {
                return;
            }
            int n = row.getSuccessCount() == null ? 0 : row.getSuccessCount();
            keyMapper.update(
                    null,
                    new LambdaUpdateWrapper<GeminiApiKey>()
                            .eq(GeminiApiKey::getId, slot.id)
                            .set(GeminiApiKey::getSuccessCount, n + 1)
                            .set(GeminiApiKey::getLastOkAt, LocalDateTime.now())
                            .set(GeminiApiKey::getCooldownUntil, null)
                            .set(GeminiApiKey::getUpdatedAt, LocalDateTime.now()));
        } catch (Exception e) {
            log.debug("记录 Gemini key 成功状态失败: {}", e.getMessage());
        }
    }

    public void markBad(Slot slot, Throwable error) {
        if (!failoverWorthy(error)) {
            return;
        }
        cooldownUntilMs.put(slot.id, System.currentTimeMillis() + COOLDOWN_SEC * 1000L);
        if (slot.id <= 0) {
            return;
        }
        try {
            GeminiApiKey row = keyMapper.selectById(slot.id);
            if (row == null) {
                return;
            }
            int n = row.getFailCount() == null ? 0 : row.getFailCount();
            row.setFailCount(n + 1);
            row.setLastError(trim(shortMsg(error), 480));
            row.setLastErrorAt(LocalDateTime.now());
            row.setCooldownUntil(LocalDateTime.now().plusSeconds(COOLDOWN_SEC));
            row.setUpdatedAt(LocalDateTime.now());
            keyMapper.updateById(row);
        } catch (Exception e) {
            log.debug("记录 Gemini key 失败状态失败: {}", e.getMessage());
        }
    }

    public void reload() {
        synchronized (loadLock) {
            loadedAt = 0L;
        }
        ensureFresh();
    }

    public void clearCooldown(long id) {
        cooldownUntilMs.remove(id);
    }

    public static boolean failoverWorthy(Throwable e) {
        String msg = chain(e).toLowerCase();
        if (msg.contains("deadline_exceeded") || msg.contains("timed out") || msg.contains("timeout")) {
            return false;
        }
        if (msg.contains("failed to execute http request")
                || msg.contains("connection reset")
                || msg.contains("broken pipe")) {
            return false;
        }
        return msg.contains("resource_exhausted")
                || msg.contains("quota")
                || msg.contains("rate limit")
                || msg.contains("429")
                || msg.contains("api key not valid")
                || msg.contains("api_key_invalid")
                || msg.contains("permission_denied")
                || msg.contains("unauthenticated")
                || msg.contains("403")
                || msg.contains("401")
                || msg.contains("billing");
    }

    private int startIndex(List<Slot> pool) {
        if (pool.size() <= 1) {
            return 0;
        }
        if (RANDOM.equals(strategy)) {
            return ThreadLocalRandom.current().nextInt(pool.size());
        }
        if (FAILOVER.equals(strategy)) {
            return 0;
        }
        int n = roundRobin.getAndIncrement();
        if (n < 0) {
            roundRobin.set(0);
            n = 0;
        }
        return Math.floorMod(n, pool.size());
    }

    private boolean cooling(long id, long now) {
        Long until = cooldownUntilMs.get(id);
        return until != null && until > now;
    }

    private void ensureFresh() {
        if (System.currentTimeMillis() - loadedAt < CACHE_MS && loadedAt > 0) {
            return;
        }
        synchronized (loadLock) {
            if (System.currentTimeMillis() - loadedAt < CACHE_MS && loadedAt > 0) {
                return;
            }
            load();
        }
    }

    private void load() {
        String nextStrategy = ROUND_ROBIN;
        List<Slot> next = new ArrayList<>();
        try {
            GeminiKeyPolicy policy = policyMapper.selectById(1);
            if (policy != null && isStrategy(policy.getStrategy())) {
                nextStrategy = policy.getStrategy();
            }
            List<GeminiApiKey> rows =
                    keyMapper.selectList(
                            new LambdaQueryWrapper<GeminiApiKey>()
                                    .orderByAsc(GeminiApiKey::getSortOrder)
                                    .orderByAsc(GeminiApiKey::getId));
            long now = System.currentTimeMillis();
            for (GeminiApiKey row : rows) {
                if (!StringUtils.hasText(row.getApiKey())) {
                    continue;
                }
                boolean on = row.getEnabled() == null || row.getEnabled() == 1;
                next.add(
                        new Slot(
                                row.getId(),
                                StringUtils.hasText(row.getName()) ? row.getName().trim() : ("Key " + row.getId()),
                                row.getApiKey().trim(),
                                on,
                                row.getSortOrder() == null ? 0 : row.getSortOrder()));
                if (row.getCooldownUntil() != null) {
                    long ms = row.getCooldownUntil().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    if (ms > now) {
                        cooldownUntilMs.put(row.getId(), ms);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取 Gemini Key 池失败，回退配置文件: {}", e.getMessage());
            next.clear();
        }
        boolean anyEnabled = false;
        for (Slot s : next) {
            if (s.enabled) {
                anyEnabled = true;
                break;
            }
        }
        if (!anyEnabled && StringUtils.hasText(props.getApiKey())) {
            next.add(new Slot(YAML_KEY_ID, "配置文件", props.getApiKey().trim(), true, 0));
        }
        slots = List.copyOf(next);
        strategy = nextStrategy;
        loadedAt = System.currentTimeMillis();
        retainClients();
    }

    private void retainClients() {
        java.util.Set<String> live = new java.util.HashSet<>();
        for (Slot s : slots) {
            live.add(s.apiKey);
        }
        if (StringUtils.hasText(props.getApiKey())) {
            live.add(props.getApiKey().trim());
        }
        Iterator<Map.Entry<String, Client>> it = clients.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Client> e = it.next();
            if (!live.contains(e.getKey())) {
                it.remove();
                try {
                    e.getValue().close();
                } catch (Exception ignored) {
                    /* close best-effort */
                }
            }
        }
    }

    public static boolean isStrategy(String raw) {
        return ROUND_ROBIN.equals(raw) || FAILOVER.equals(raw) || RANDOM.equals(raw);
    }

    private static String chain(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = e;
        int n = 0;
        while (cur != null && n < 6) {
            if (cur.getMessage() != null) {
                sb.append(cur.getMessage()).append(' ');
            }
            cur = cur.getCause();
            n++;
        }
        return sb.toString();
    }

    private static String shortMsg(Throwable e) {
        String m = e == null ? "" : chain(e).trim();
        return m.length() > 300 ? m.substring(0, 300) + "…" : m;
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    public static String mask(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        String k = apiKey.trim();
        if (k.length() <= 10) {
            return "••••••••";
        }
        return k.substring(0, 4) + "····" + k.substring(k.length() - 4);
    }

    public static final class Slot {
        private final long id;
        private final String name;
        private final String apiKey;
        private final boolean enabled;
        private final int sortOrder;

        public Slot(long id, String name, String apiKey, boolean enabled, int sortOrder) {
            this.id = id;
            this.name = name;
            this.apiKey = apiKey;
            this.enabled = enabled;
            this.sortOrder = sortOrder;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getApiKey() {
            return apiKey;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getSortOrder() {
            return sortOrder;
        }
    }

    @FunctionalInterface
    public interface GeminiClientCall<T> {
        T apply(Client client) throws Exception;
    }

    /** 避免未使用导入告警：策略列表给管理端用 */
    public static List<String> strategies() {
        return Collections.unmodifiableList(List.of(ROUND_ROBIN, FAILOVER, RANDOM));
    }
}
