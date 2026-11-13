package com.fashtry.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 试穿模块配置
 * <p>
 * 对应 application.yml 中 app.tryon 前缀
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.tryon")
public class TryonProperties {

    /** Redis 任务状态 key 前缀 */
    private String redisKeyPrefix = "tryon:task:";

    /** Redis 任务状态 TTL（秒），默认 7 天 */
    private long redisTtlSeconds = 604800;

    /** FASHN 试穿模型：tryon-max | tryon-v1.6 */
    private String fashnModelName = "tryon-max";

    /** tryon-max 输出分辨率：1k | 2k | 4k */
    private String fashnResolution = "1k";

    /** tryon-max 默认 generation_mode（仅当请求未指定时由客户端兜底；用户提交以接口为准） */
    private String fashnGenerationMode = "quality";

    /** tryon-v1.6 的 mode：performance | balanced | quality */
    private String fashnLegacyMode = "balanced";

    /**
     * 进度条估算：从进入「生成中」到完成的参考秒数（用于把已耗时映射到 20%～99%）。
     * 默认按线上 Gemini 试穿 {@code generation_duration_ms} 样本（约 76s～155s，均值约 125s）取整为 128s；可按实际 P50/P80 调整。
     */
    private int tryonProgressEstimateSeconds = 128;
}
