package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 虚拟试穿异步提交：传入已可访问的模特图、服装图 URL（如先经 /api/upload/business 上传 COS）。
 */
@Data
public class GeminiTryOnRequest {

    @NotBlank(message = "modelUrl 不能为空")
    private String modelUrl;

    @NotBlank(message = "garmentUrl 不能为空")
    private String garmentUrl;

    private String instruction;
    private String modelId;
    private String aspectRatio;
    private String imageSize;
}
