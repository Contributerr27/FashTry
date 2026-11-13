package com.fashtry.backend.pay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信小程序（code 换 openid，供支付 JSAPI 使用）。secret 未配置时无法走小程序内支付。
 */
@Data
@ConfigurationProperties(prefix = "wx.mini-program")
public class WxMiniProgramProperties {

    /**
     * 小程序 AppId；留空则沿用 {@code wx.pay.app-id}（需与发起支付的商户绑定同一小程序）。
     */
    private String appId;

    /** 小程序 AppSecret，用于 jscode2session */
    private String secret;
}
