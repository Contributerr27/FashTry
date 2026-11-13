package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** COS 直传 PUT 成功后确认落库 */
@Data
public class UploadConfirmRequest {

    @NotBlank
    private String fileUrl;

    /** COS 对象 key；不传则从 fileUrl 解析 */
    private String objectKey;

    private String fileName;

    private Long fileSize;

    /** business | other，默认 business */
    private String category;
}
