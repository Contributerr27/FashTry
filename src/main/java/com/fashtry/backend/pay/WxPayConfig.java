package com.fashtry.backend.pay;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信支付配置
 */
@Configuration
@EnableConfigurationProperties({WxPayProperties.class, WxMiniProgramProperties.class})
public class WxPayConfig {
}
