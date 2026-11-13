package com.fashtry.backend.bean.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadPresignVO {
    /** 浏览器可直接 PUT 的预签名 URL */
    private String uploadUrl;
    /** 对外访问 URL（上传成功后可直接用） */
    private String fileUrl;
    /** COS ObjectKey（用于排查/落库） */
    private String objectKey;
    /** 过期时间戳（毫秒） */
    private Long expireAt;
}

