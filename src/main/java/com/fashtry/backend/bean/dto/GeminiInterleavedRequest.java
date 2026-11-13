package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 图文交织：第一段为主提示，第二段为「边生成边配图」类补充说明（与官方食谱示例一致）
 */
@Data
public class GeminiInterleavedRequest {

    @NotBlank(message = "prompt 不能为空")
    private String prompt;

    private String secondPrompt = "Create images to go alongside the text as you generate the content.";

    private String modelId;

    /** 可选：宽高比，与 generate 接口相同 */
    private String aspectRatio;

    /** 可选：分辨率 512 / 1K / 2K / 4K 等，与 generate 接口相同 */
    private String imageSize;
}
