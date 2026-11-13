package com.fashtry.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** 新用户注册赠送的免费试用点数（仅作后台未配置时的默认值） */
    private Integer freeTrialCredits = 1;

    /** 每天注册赠送总额度（仅作后台未配置时的默认值） */
    private Integer dailyGiftTotalCredits = 20;

    /**
     * 新用户默认头像（手机号注册、Google 首次注册等均写入该地址；可在 yml 中覆盖 app.auth.default-avatar-url）
     */
    private String defaultAvatarUrl =
            "https://fash-try-1305146494.cos.ap-guangzhou.myqcloud.com/other/2026/04/e68ec45f65e24e869f4de37101ce9b4f.jpg";

    /** 验证码有效期(秒) */
    private Integer smsCodeTtlSeconds = 300;

    /** 同一手机号发送验证码间隔(秒) */
    private Integer smsSendIntervalSeconds = 60;
}
