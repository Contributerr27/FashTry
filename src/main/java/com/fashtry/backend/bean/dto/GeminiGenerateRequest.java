package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GeminiGenerateRequest {

    @NotBlank(message = "prompt 不能为空")
    private String prompt;

    /** 可选，默认取配置 app.gemini.model-id */
    private String modelId;

    /**
     * 宽高比，如 1:1、16:9、9:16、5:4；Gemini 3 另支持 1:4、4:1、1:8、8:1 等（以官方文档为准）
     */
    private String aspectRatio;

    /**
     * 输出分辨率：如 512、1K、2K、4K（Gemini 3 系列；具体以当前模型文档为准）
     */
    private String imageSize;
}
