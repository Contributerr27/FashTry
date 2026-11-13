package com.fashtry.backend.bean.vo;

import lombok.Data;

/**
 * 后台管理：品牌进店统一链接与二维码（PNG Base64，不含 data URL 前缀）。
 * PC/H5 共用同一链接，扫码后由前端根据 UA 适配；小程序路径可单独配置仅作预留展示/复制。
 */
@Data
public class BrandEntryVO {

    private Long brandId;

    /** 品牌唯一编码，对应落地页中的 brand 参数 */
    private String brandCode;

    private String brandName;

    /** 统一进店链接（二维码内容） */
    private String landingUrl;

    /** PNG 二进制经 Base64 编码 */
    private String qrPngBase64;

    /**
     * 小程序侧预留路径或中转链接（配置了 app.brand-entry.mini-program-url-template 时返回，否则为 null）
     */
    private String miniProgramLandingUrl;
}
