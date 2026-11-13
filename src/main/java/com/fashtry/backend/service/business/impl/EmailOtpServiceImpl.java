package com.fashtry.backend.service.business.impl;

import com.fashtry.backend.common.config.AuthProperties;
import com.fashtry.backend.common.config.ResendProperties;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.service.business.EmailOtpService;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码：Redis key email:code:{email}，发送走 Resend。
 */
@Service
public class EmailOtpServiceImpl implements EmailOtpService {

    private static final Logger log = LoggerFactory.getLogger(EmailOtpServiceImpl.class);
    private static final String CODE_PREFIX = "email:code:";
    private static final String INTERVAL_PREFIX = "email:interval:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;
    private final ResendProperties resendProperties;

    public EmailOtpServiceImpl(StringRedisTemplate redisTemplate,
                               AuthProperties authProperties,
                               ResendProperties resendProperties) {
        this.redisTemplate = redisTemplate;
        this.authProperties = authProperties;
        this.resendProperties = resendProperties;
    }

    @Override
    public void sendCode(String normalizedEmail) {
        if (!StringUtils.hasText(resendProperties.getApiKey())) {
            throw new BusinessException(ResultCode.SMS_SEND_FAIL, "邮件服务未配置（Resend API Key）");
        }
        String intervalKey = INTERVAL_PREFIX + normalizedEmail;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(intervalKey))) {
            throw new BusinessException(4290, "发送过于频繁，请稍后再试");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String codeKey = CODE_PREFIX + normalizedEmail;
        redisTemplate.opsForValue().set(codeKey, code, Duration.ofSeconds(authProperties.getSmsCodeTtlSeconds()));
        redisTemplate.opsForValue().set(intervalKey, "1", authProperties.getSmsSendIntervalSeconds(), TimeUnit.SECONDS);

        Resend resend = new Resend(resendProperties.getApiKey().trim());
        int validMinutes = Math.max(1, (authProperties.getSmsCodeTtlSeconds() + 59) / 60);
        String html = """
<!DOCTYPE html>
<html lang="zh-CN">
<head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/></head>
<body style="margin:0;padding:0;background-color:#eef0f4;">
<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#eef0f4;">
  <tr>
    <td align="center" style="padding:40px 16px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Hiragino Sans GB','Microsoft YaHei',sans-serif;">
      <table role="presentation" width="480" cellspacing="0" cellpadding="0" border="0" style="max-width:480px;width:100%%;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 8px 32px rgba(15,23,42,0.08);">
        <tr>
          <td style="padding:36px 32px 28px 32px;">
            <p style="margin:0 0 8px 0;font-size:13px;font-weight:600;letter-spacing:0.06em;color:#64748b;text-transform:uppercase;">FashTry</p>
            <h1 style="margin:0 0 24px 0;font-size:22px;font-weight:700;line-height:1.35;color:#0f172a;">登录验证码</h1>
            <p style="margin:0 0 20px 0;font-size:15px;line-height:1.6;color:#475569;">请使用以下验证码完成登录（如非本人操作请忽略）。</p>
            <div style="text-align:center;padding:22px 20px;margin:0 0 24px 0;background-color:#f1f5f9;border:1px solid #e2e8f0;border-radius:12px;">
              <span style="font-size:32px;font-weight:700;letter-spacing:0.45em;color:#0f172a;font-family:ui-monospace,SFMono-Regular,'SF Mono',Menlo,Consolas,monospace;padding-left:0.45em;">%s</span>
            </div>
            <p style="margin:0 0 8px 0;font-size:13px;line-height:1.6;color:#64748b;">%d 分钟内有效，请勿向他人泄露。</p>
            <p style="margin:0;font-size:12px;line-height:1.5;color:#94a3b8;">此邮件由系统自动发送，请勿直接回复。</p>
          </td>
        </tr>
        <tr>
          <td style="padding:16px 32px 28px 32px;border-top:1px solid #f1f5f9;">
            <p style="margin:0;font-size:12px;color:#94a3b8;">FashTry 安全团队</p>
          </td>
        </tr>
      </table>
    </td>
  </tr>
</table>
</body>
</html>
""".formatted(code, validMinutes);

        CreateEmailOptions req = CreateEmailOptions.builder()
                .from(resendProperties.getFrom())
                .to(normalizedEmail)
                .subject("FashTry 登录验证码")
                .html(html)
                .build();
        try {
            CreateEmailResponse sent = resend.emails().send(req);
            log.info("[EMAIL] 验证码已发送: email={}, id={}", normalizedEmail, sent != null ? sent.getId() : null);
        } catch (ResendException e) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(intervalKey);
            log.warn("[EMAIL] Resend 发送失败: email={}, {}", normalizedEmail, e.getMessage());
            throw new BusinessException(ResultCode.SMS_SEND_FAIL, "邮件发送失败，请稍后重试");
        }
    }

    @Override
    public boolean verifyCode(String normalizedEmail, String code) {
        String codeKey = CODE_PREFIX + normalizedEmail;
        String cached = redisTemplate.opsForValue().get(codeKey);
        if (cached == null || !cached.equals(code)) {
            return false;
        }
        redisTemplate.delete(codeKey);
        return true;
    }
}
