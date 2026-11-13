package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UploadPresignRequest {

    /** 原始文件名（用于取扩展名） */
    @NotBlank
    private String fileName;

    /** MIME，如 image/jpeg */
    @NotBlank
    private String contentType;

    /** 文件大小（字节） */
    @NotNull
    private Long fileSize;
}

