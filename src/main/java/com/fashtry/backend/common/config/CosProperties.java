package com.fashtry.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 腾讯云 COS 对象存储配置
 * 存储桶: fash-try-1305146494
 * 地域: 广州 (ap-guangzhou)
 * 访问权限: 公有读私有写
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.cos")
public class CosProperties {

    /** 地域，如 ap-guangzhou */
    private String region;

    /** 腾讯云 SecretId */
    private String secretId;

    /** 腾讯云 SecretKey */
    private String secretKey;

    /** 存储桶名称 */
    private String bucketName;

    /** 公有读访问域名，用于拼接文件访问 URL */
    private String domain;

    /**
     * 是否启用数据万象同步图片审核（需存储桶已绑定数据万象；仅小程序上传且带 X-Fashtry-Client 头时执行）。
     */
    private boolean ciModerationEnabled = false;

    /** 控制台审核策略 biz-type，可选；不填则使用默认策略 */
    private String ciBizType = "";

    /** 审核 Result 为 2（疑似）时是否拦截；默认 false，减少正常图误杀 */
    private boolean ciBlockSuspect = false;

    /**
     * COS Java SDK 连接超时（毫秒）。默认 30s。
     */
    private int connectionTimeoutMs = 30_000;

    /**
     * COS Java SDK 读写/上传超时（毫秒）。大图（如 2K 试穿结果 ~3MB）经公网上行慢时，过短易与 COS 侧
     * {@code UserNetworkTooSlow} 叠加；默认 10 分钟。
     */
    private int socketTimeoutMs = 600_000;

    /**
     * 全球加速（仅影响 SDK 访问 COS 的 endpoint，不改变桶内对象；对外拼接的 {@link #domain} 访问 URL 不变）。
     * <ul>
     *   <li>{@code off} — 默认，走地域域名</li>
     *   <li>{@code public} — 控制台「全球加速域名」{@code *.cos.accelerate.myqcloud.com}，适合海外/公网上行到国内桶</li>
     *   <li>{@code internal} — 「内网全球加速」{@code *.cos-internal.accelerate.tencentcos.cn}，仅腾讯云 VPC 内可解析访问</li>
     * </ul>
     */
    private String accelerateMode = "off";
}
