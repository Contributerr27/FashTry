package com.fashtry.backend.service.business;

import com.fashtry.backend.bean.db.RegisterAbusePolicy;
import com.fashtry.backend.bean.dto.RegisterAbusePolicyRequest;
import com.fashtry.backend.bean.vo.RegisterAbuseSettingsVO;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.RegisterAbusePolicyMapper;
import com.fashtry.backend.utils.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.UUID;

/**
 * 注册赠送与短信的爆发限制。阈值在后台「注册风控」可改，填 0 关闭对应规则。
 * 命中后注册仍然成功，只是不送额度；接口不返回原因。
 */
@Service
@Slf4j
public class RegisterAbuseGuard {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int POLICY_ID = 1;

    private static final String IP_KEY = "reg:burst:ip:";
    private static final String PREFIX_KEY = "reg:burst:pfx:";
    private static final String GLOBAL_KEY = "reg:burst:global";
    private static final String SMS_IP_KEY = "sms:ip:hour:";

    private static final DefaultRedisScript<Long> WINDOW_SCRIPT = new DefaultRedisScript<>();
    /** 未超限才写入，避免超限后的重试把窗口一直往后推。返回值大于 limit 表示拒绝。 */
    private static final DefaultRedisScript<Long> LIMITED_WINDOW_SCRIPT = new DefaultRedisScript<>();

    static {
        WINDOW_SCRIPT.setResultType(Long.class);
        WINDOW_SCRIPT.setScriptText(
                "local key = KEYS[1]\n"
                        + "local now = tonumber(ARGV[1])\n"
                        + "local window = tonumber(ARGV[2])\n"
                        + "local member = ARGV[3]\n"
                        + "local ttl = tonumber(ARGV[4])\n"
                        + "redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)\n"
                        + "redis.call('ZADD', key, now, member)\n"
                        + "redis.call('PEXPIRE', key, ttl)\n"
                        + "return redis.call('ZCARD', key)\n");
        LIMITED_WINDOW_SCRIPT.setResultType(Long.class);
        LIMITED_WINDOW_SCRIPT.setScriptText(
                "local key = KEYS[1]\n"
                        + "local now = tonumber(ARGV[1])\n"
                        + "local window = tonumber(ARGV[2])\n"
                        + "local member = ARGV[3]\n"
                        + "local limit = tonumber(ARGV[4])\n"
                        + "local ttl = tonumber(ARGV[5])\n"
                        + "redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)\n"
                        + "local n = redis.call('ZCARD', key)\n"
                        + "if n >= limit then\n"
                        + "  return n + 1\n"
                        + "end\n"
                        + "redis.call('ZADD', key, now, member)\n"
                        + "redis.call('PEXPIRE', key, ttl)\n"
                        + "return n + 1\n");
    }

    private final StringRedisTemplate redisTemplate;
    private final RegisterAbusePolicyMapper policyMapper;
    private volatile boolean missingTableLogged;

    public RegisterAbuseGuard(StringRedisTemplate redisTemplate, RegisterAbusePolicyMapper policyMapper) {
        this.redisTemplate = redisTemplate;
        this.policyMapper = policyMapper;
    }

    public RegisterAbuseSettingsVO settings() {
        return toVo(ensurePolicyOrThrow());
    }

    public void updatePolicy(RegisterAbusePolicyRequest body) {
        ensurePolicyOrThrow();
        RegisterAbusePolicy row = new RegisterAbusePolicy();
        row.setId(POLICY_ID);
        row.setIpWindowMinutes(body.getIpWindowMinutes());
        row.setIpDenyFrom(body.getIpDenyFrom());
        row.setPrefixWindowMinutes(body.getPrefixWindowMinutes());
        row.setPrefixDenyFrom(body.getPrefixDenyFrom());
        row.setGlobalWindowMinutes(body.getGlobalWindowMinutes());
        row.setGlobalAllow(body.getGlobalAllow());
        row.setSmsWindowMinutes(body.getSmsWindowMinutes());
        row.setSmsMaxPerIp(body.getSmsMaxPerIp());
        row.setUpdatedAt(LocalDateTime.now(ZONE));
        policyMapper.updateById(row);
    }

    /**
     * 记下这次新注册，并判断是否仍赠送额度。手机号可为空（邮箱、Google）。
     * Redis 异常时按正常赠送，避免计数故障把试用点全部停掉。
     */
    public boolean allowGift(String normalizedPhone) {
        try {
            RegisterAbusePolicy policy = policyOrDefault();
            int ipDenyFrom = nz(policy.getIpDenyFrom());
            int prefixDenyFrom = nz(policy.getPrefixDenyFrom());
            int globalAllow = nz(policy.getGlobalAllow());
            if (ipDenyFrom <= 0 && prefixDenyFrom <= 0 && globalAllow <= 0) {
                return true;
            }

            long now = System.currentTimeMillis();
            String member = now + ":" + UUID.randomUUID();
            String ip = currentIp();
            String prefix = cnNationalPrefix7(normalizedPhone);

            long globalCount = 0;
            if (globalAllow > 0) {
                globalCount = record(GLOBAL_KEY, member, now, windowMs(policy.getGlobalWindowMinutes()));
            }
            long ipCount = 0;
            boolean hasIp = StringUtils.hasText(ip);
            if (hasIp && ipDenyFrom > 0) {
                ipCount = record(IP_KEY + ip, member, now, windowMs(policy.getIpWindowMinutes()));
            }
            long prefixCount = 0;
            boolean hasPrefix = prefix != null;
            if (hasPrefix && prefixDenyFrom > 0) {
                prefixCount = record(PREFIX_KEY + prefix, member, now, windowMs(policy.getPrefixWindowMinutes()));
            }

            if (!denyGift(ipCount, hasIp, prefixCount, hasPrefix, globalCount, ipDenyFrom, prefixDenyFrom, globalAllow)) {
                return true;
            }
            log.info("注册不赠送额度: ip={}, prefix={}, ipCount={}/{}, prefixCount={}/{}, globalCount={}/{}",
                    hasIp ? ip : "-", prefix == null ? "-" : prefix,
                    ipCount, ipDenyFrom, prefixCount, prefixDenyFrom, globalCount, globalAllow);
            return false;
        } catch (RuntimeException e) {
            log.error("注册风控计数失败，按正常赠送处理", e);
            return true;
        }
    }

    /**
     * 图形验证码通过后、真正发短信前调用。同一 IP 在配置窗口内超过条数则拒绝。0 表示不限制。
     */
    public void assertSmsIpAllowed() {
        RegisterAbusePolicy policy = policyOrDefault();
        int max = nz(policy.getSmsMaxPerIp());
        if (max <= 0) {
            return;
        }
        String ip = currentIp();
        if (!StringUtils.hasText(ip)) {
            log.warn("发短信未解析到客户端 IP，跳过条数限制");
            return;
        }
        long windowMs = windowMs(policy.getSmsWindowMinutes());
        long now = System.currentTimeMillis();
        String member = now + ":" + UUID.randomUUID();
        Long n = redisTemplate.execute(
                LIMITED_WINDOW_SCRIPT,
                Collections.singletonList(SMS_IP_KEY + ip),
                String.valueOf(now),
                String.valueOf(windowMs),
                member,
                String.valueOf(max),
                String.valueOf(windowMs));
        if (n != null && n > max) {
            log.info("短信 IP 超限: ip={}, count={}, max={}", ip, n, max);
            throw new BusinessException(ResultCode.RATE_LIMIT);
        }
    }

    /**
     * 阈值小于等于 0 的规则不生效。全站是「超过 globalAllow 个」才不送，IP 和号段是「达到 denyFrom 个」起不送。
     */
    static boolean denyGift(long ipCount, boolean hasIp, long prefixCount, boolean hasPrefix, long globalCount,
                            int ipDenyFrom, int prefixDenyFrom, int globalAllow) {
        if (globalAllow > 0 && globalCount > globalAllow) {
            return true;
        }
        if (hasIp && ipDenyFrom > 0 && ipCount >= ipDenyFrom) {
            return true;
        }
        return hasPrefix && prefixDenyFrom > 0 && prefixCount >= prefixDenyFrom;
    }

    /**
     * 大陆手机号（86 + 11 位）取国内号码前 7 位，例如 8619054293560 → 1905429。
     * 非大陆号码不参与号段计数。
     */
    static String cnNationalPrefix7(String normalizedPhone) {
        if (normalizedPhone == null || normalizedPhone.length() != 13 || !normalizedPhone.startsWith("86")) {
            return null;
        }
        char head = normalizedPhone.charAt(2);
        if (head < '1' || head > '9') {
            return null;
        }
        return normalizedPhone.substring(2, 9);
    }

    private RegisterAbusePolicy policyOrDefault() {
        try {
            return ensurePolicy();
        } catch (DataAccessException e) {
            if (!missingTableLogged) {
                missingTableLogged = true;
                log.error("注册风控配置表不可用，使用默认阈值", e);
            }
            return defaults();
        }
    }

    private RegisterAbusePolicy ensurePolicyOrThrow() {
        try {
            return ensurePolicy();
        } catch (DataAccessException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "注册风控配置表不存在，请先执行 migration-register-abuse-policy.sql");
        }
    }

    private RegisterAbusePolicy ensurePolicy() {
        policyMapper.insertIgnore();
        RegisterAbusePolicy row = policyMapper.selectById(POLICY_ID);
        if (row != null) {
            return row;
        }
        row = defaults();
        row.setId(POLICY_ID);
        row.setUpdatedAt(LocalDateTime.now(ZONE));
        policyMapper.insert(row);
        return row;
    }

    private static RegisterAbusePolicy defaults() {
        RegisterAbusePolicy row = new RegisterAbusePolicy();
        row.setIpWindowMinutes(10);
        row.setIpDenyFrom(3);
        row.setPrefixWindowMinutes(60);
        row.setPrefixDenyFrom(3);
        row.setGlobalWindowMinutes(10);
        row.setGlobalAllow(8);
        row.setSmsWindowMinutes(60);
        row.setSmsMaxPerIp(5);
        return row;
    }

    private static RegisterAbuseSettingsVO toVo(RegisterAbusePolicy row) {
        return RegisterAbuseSettingsVO.builder()
                .ipWindowMinutes(nz(row.getIpWindowMinutes()))
                .ipDenyFrom(nz(row.getIpDenyFrom()))
                .prefixWindowMinutes(nz(row.getPrefixWindowMinutes()))
                .prefixDenyFrom(nz(row.getPrefixDenyFrom()))
                .globalWindowMinutes(nz(row.getGlobalWindowMinutes()))
                .globalAllow(nz(row.getGlobalAllow()))
                .smsWindowMinutes(nz(row.getSmsWindowMinutes()))
                .smsMaxPerIp(nz(row.getSmsMaxPerIp()))
                .build();
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static long windowMs(Integer minutes) {
        int m = nz(minutes);
        if (m <= 0) {
            m = 1;
        }
        return m * 60_000L;
    }

    private long record(String key, String member, long now, long windowMs) {
        Long n = redisTemplate.execute(
                WINDOW_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(now),
                String.valueOf(windowMs),
                member,
                String.valueOf(windowMs));
        return n == null ? 0L : n;
    }

    private static String currentIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return "";
        }
        HttpServletRequest request = attrs.getRequest();
        String ip = ClientIpUtils.getClientIp(request);
        if (!StringUtils.hasText(ip)) {
            return "";
        }
        String trimmed = ip.trim();
        if (trimmed.length() > 64) {
            trimmed = trimmed.substring(0, 64);
        }
        return trimmed;
    }
}
