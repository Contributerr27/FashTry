package com.fashtry.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Product to Model 模块配置
 * <p>
 * 对应 application.yml 中 app.product-to-model 前缀
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.product-to-model")
public class ProductToModelProperties {

    /** Redis 任务状态 key 前缀 */
    private String redisKeyPrefix = "product-to-model:task:";

    /** Redis 任务状态 TTL（秒），默认 7 天 */
    private long redisTtlSeconds = 604800;
}
