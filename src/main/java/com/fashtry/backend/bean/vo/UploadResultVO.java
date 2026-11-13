package com.fashtry.backend.bean.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResultVO {

    /** 文件访问 URL */
    private String url;
}
