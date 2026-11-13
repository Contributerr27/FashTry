package com.fashtry.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 品牌扫码进店：模板中必须包含占位符 {@code {code}}，将替换为品牌的 {@code code}（URL 编码后写入链接）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.brand-entry")
public class BrandEntryProperties {

    /**
     * 统一进店地址（PC / H5 / 手机浏览器扫码均使用此链接，携带 brand 参数即可）
     */
    private String landingUrlTemplate = "http://127.0.0.1:5173/?brand={code}";

    /**
     * 小程序端预留：页面路径或完整 URL，需含 {@code {code}}；不配或留空则不在接口中返回该字段。
     * 例：{@code pages/brand/index?brand={code}} 或业务域名 H5 中转页。
     */
    private String miniProgramUrlTemplate = "";

    /** 二维码边长（像素），建议 240–320 */
    private int qrSize = 280;
}
