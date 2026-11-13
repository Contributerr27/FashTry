package com.fashtry.backend.pay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信支付配置属性（平台证书模式）
 */
@Data
@ConfigurationProperties(prefix = "wx.pay")
public class WxPayProperties {

    private String appId;
    private String mchId;
    /** 商户私钥文件路径（apiclient_key.pem），支持 classpath: 或绝对路径 */
    private String privateKeyPath;
    /** 证书序列号，商户平台 API 安全 中查看 */
    private String certSerialNo;
    /** 微信平台证书文件路径（wechatpay.pem），支持 classpath: 或绝对路径 */
    private String platformCertPath;
}
