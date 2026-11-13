package com.fashtry.backend.common.constants;

/**
 * C 端品牌进店上下文：与扫码落地页 {@code ?brand=} 对应，移动端可持久化后在请求中携带。
 */
public final class ApiBrandHeaders {

    /** 品牌唯一编码（brands.code），非 id */
    public static final String X_BRAND_CODE = "X-Brand-Code";

    private ApiBrandHeaders() {
    }
}
