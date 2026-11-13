package com.fashtry.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 前后端分离时浏览器跨域访问 API 的允许来源。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /** 例：http://localhost:5173、https://fashtry.com */
    private List<String> allowedOrigins = new ArrayList<>();
}
