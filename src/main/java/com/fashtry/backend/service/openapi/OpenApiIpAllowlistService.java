package com.fashtry.backend.service.openapi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fashtry.backend.bean.db.OpenApiIpRule;
import com.fashtry.backend.common.config.OpenApiProperties;
import com.fashtry.backend.mapper.OpenApiIpRuleMapper;
import com.fashtry.backend.utils.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 开放 API IP 白名单：规则存库，带短 TTL 内存缓存。
 */
@Service
public class OpenApiIpAllowlistService {

    private static final Logger log = LoggerFactory.getLogger(OpenApiIpAllowlistService.class);

    private final OpenApiIpRuleMapper ruleMapper;
    private final OpenApiProperties openApiProperties;

    private final AtomicReference<CachedRules> cache = new AtomicReference<>(new CachedRules(0, List.of()));

    public OpenApiIpAllowlistService(OpenApiIpRuleMapper ruleMapper, OpenApiProperties openApiProperties) {
        this.ruleMapper = ruleMapper;
        this.openApiProperties = openApiProperties;
    }

    public void evictCache() {
        cache.set(new CachedRules(0, List.of()));
    }

    public boolean isClientIpAllowed(HttpServletRequest request) {
        String rawClientIp = resolveClientIp(request);
        String clientIp = normalizeForMatch(rawClientIp);
        if (!StringUtils.hasText(clientIp)) {
            log.warn("[open-api] 无法解析客户端 IP");
            return false;
        }
        List<String> rules = currentRules();
        if (rulesAllowed(clientIp, rules)) {
            return true;
        }
        List<String> fresh = loadRulesFromDb();
        cache.set(new CachedRules(System.currentTimeMillis(), List.copyOf(fresh)));
        boolean ok = rulesAllowed(clientIp, fresh);
        if (!ok) {
            log.warn(
                    "[open-api] IP 未命中白名单: clientIp={} (原始={}), rules=[{}], useForwardedFor={}。"
                            + " 当前请求 IP 须与表中 ip_rule 完全一致，或在 IPv4 CIDR 内；本机访问请增加规则 127.0.0.1。"
                            + " 若在 Nginx/网关后，请 use-forwarded-for=true 且规则填客户端公网出口 IP。",
                    clientIp,
                    rawClientIp,
                    String.join(", ", fresh),
                    openApiProperties.isUseForwardedFor());
        }
        return ok;
    }

    private static boolean rulesAllowed(String clientIp, List<String> rules) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        for (String rule : rules) {
            if (!StringUtils.hasText(rule)) {
                continue;
            }
            if (matches(clientIp, normalizeForMatch(rule.trim()))) {
                return true;
            }
        }
        return false;
    }

    public String resolveClientIp(HttpServletRequest request) {
        if (openApiProperties.isUseForwardedFor()) {
            return stripZoneId(ClientIpUtils.getClientIp(request));
        }
        return stripZoneId(request.getRemoteAddr());
    }

    private static String stripZoneId(String ip) {
        String s = ip.trim();
        int z = s.indexOf('%');
        if (z > 0) {
            s = s.substring(0, z);
        }
        if (s.startsWith("[") && s.endsWith("]") && s.length() > 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String normalizeForMatch(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "";
        }
        String s = ip.trim();
        String lower = s.toLowerCase();
        if ("::1".equals(lower) || "0:0:0:0:0:0:0:1".equals(lower)) {
            return "127.0.0.1";
        }
        if (lower.startsWith("::ffff:") && lower.length() > 7) {
            String tail = s.substring(7);
            if (isLikelyIpv4(tail)) {
                return tail;
            }
        }
        return s;
    }

    private static boolean isLikelyIpv4(String s) {
        int dots = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '.') {
                dots++;
            }
        }
        return dots == 3;
    }

    private List<String> currentRules() {
        long now = System.currentTimeMillis();
        int ttlMs = Math.max(5, openApiProperties.getAllowlistCacheTtlSeconds()) * 1000;
        CachedRules c = cache.get();
        if (now - c.loadedAtMs < ttlMs && !c.rules.isEmpty()) {
            return c.rules;
        }
        synchronized (this) {
            c = cache.get();
            if (now - c.loadedAtMs < ttlMs && !c.rules.isEmpty()) {
                return c.rules;
            }
            List<String> rules = loadRulesFromDb();
            cache.set(new CachedRules(now, List.copyOf(rules)));
            return rules;
        }
    }

    private List<String> loadRulesFromDb() {
        LambdaQueryWrapper<OpenApiIpRule> w = new LambdaQueryWrapper<>();
        w.eq(OpenApiIpRule::getEnabled, 1).select(OpenApiIpRule::getIpRule);
        List<OpenApiIpRule> rows = ruleMapper.selectList(w);
        List<String> rules = new ArrayList<>();
        for (OpenApiIpRule r : rows) {
            if (r != null && StringUtils.hasText(r.getIpRule())) {
                rules.add(r.getIpRule().trim());
            }
        }
        return rules;
    }

    private static boolean matches(String clientIp, String rule) {
        if (!StringUtils.hasText(rule)) {
            return false;
        }
        if (rule.contains("/")) {
            return ipv4InCidr(clientIp, rule);
        }
        return clientIp.equalsIgnoreCase(rule);
    }

    private static boolean ipv4InCidr(String clientIp, String cidrRule) {
        try {
            String[] parts = cidrRule.split("/");
            if (parts.length != 2) {
                return false;
            }
            int prefix = Integer.parseInt(parts[1].trim());
            if (prefix < 0 || prefix > 32) {
                return false;
            }
            long network = ipv4ToLong(parts[0].trim());
            long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            long ip = ipv4ToLong(clientIp);
            return (ip & mask) == (network & mask);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static long ipv4ToLong(String ip) throws Exception {
        byte[] addr = InetAddress.getByName(ip).getAddress();
        if (addr.length != 4) {
            throw new IllegalArgumentException("not ipv4");
        }
        return ((addr[0] & 0xFFL) << 24)
                | ((addr[1] & 0xFFL) << 16)
                | ((addr[2] & 0xFFL) << 8)
                | (addr[3] & 0xFFL);
    }

    private record CachedRules(long loadedAtMs, List<String> rules) {}
}
