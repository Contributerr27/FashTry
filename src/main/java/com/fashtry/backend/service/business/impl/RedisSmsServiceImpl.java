package com.fashtry.backend.service.business.impl;

import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.service.business.RegisterAbuseGuard;
import com.fashtry.backend.service.business.SmsService;
import com.fashtry.backend.utils.TxSendSms;
import com.fashtry.backend.common.config.AuthProperties;
import com.fashtry.backend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 短信验证码服务实现
 * - 验证码存 Redis，key: sms:code:{mobile}
 * - 发送间隔限制，key: sms:interval:{mobile}
 * - 开发环境：仅打印到日志，不真实发送(可后续接入腾讯云短信)
 */
@Service
public class RedisSmsServiceImpl implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(RedisSmsServiceImpl.class);
    private static final String CODE_PREFIX = "sms:code:";
    private static final String INTERVAL_PREFIX = "sms:interval:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;
    private final TxSendSms txSendSms;
    private final RegisterAbuseGuard registerAbuseGuard;

    public RedisSmsServiceImpl(StringRedisTemplate redisTemplate, AuthProperties authProperties, TxSendSms txSendSms,
                               RegisterAbuseGuard registerAbuseGuard) {
        this.redisTemplate = redisTemplate;
        this.authProperties = authProperties;
        this.txSendSms = txSendSms;
        this.registerAbuseGuard = registerAbuseGuard;
    }

    @Override
    public String sendCode(String mobile) {
        String normalized = TxSendSms.normalizeForStorage(mobile);
        String intervalKey = INTERVAL_PREFIX + normalized;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(intervalKey))) {
            throw new BusinessException(4290, "发送过于频繁，请稍后再试");
        }
        registerAbuseGuard.assertSmsIpAllowed();

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String codeKey = CODE_PREFIX + normalized;
        redisTemplate.opsForValue().set(codeKey, code, Duration.ofSeconds(authProperties.getSmsCodeTtlSeconds()));
        redisTemplate.opsForValue().set(intervalKey, "1", authProperties.getSmsSendIntervalSeconds(), TimeUnit.SECONDS);

        // 腾讯云短信发送
        boolean sent = txSendSms.sendVc(code, mobile);
        if (!sent) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(intervalKey);
            throw new BusinessException(ResultCode.SMS_SEND_FAIL);
        }
        log.info("[SMS] 手机号 {} 验证码已发送", normalized);

        return code;
    }

    /**
     * 校验验证码(供 AuthService 调用)
     */
    public boolean verifyCode(String mobile, String code) {
        String normalized = TxSendSms.normalizeForStorage(mobile);
        String codeKey = CODE_PREFIX + normalized;
        String cached = redisTemplate.opsForValue().get(codeKey);
        if (cached == null || !cached.equals(code)) {
            return false;
        }
        redisTemplate.delete(codeKey);
        return true;
    }
}
