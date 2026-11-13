package com.fashtry.backend.bean.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminImagePresignVO {
    /** 原图 PUT 预签名 URL */
    private String uploadUrl;
    /** 原图对外访问 URL */
    private String imageUrl;

    /** 缩略图 PUT 预签名 URL（建议前端生成 JPEG，宽 200px 等比） */
    private String uploadThumbUrl;
    /** 缩略图对外访问 URL */
    private String thumbUrl;

    /** 过期时间戳（毫秒） */
    private Long expireAt;
}

