package com.fashtry.backend.bean.vo;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertex Gemini 图像接口返回：文本分片 + PNG 图片（Base64）
 */
@Data
@Builder
public class GeminiImageResultVO {

    @Builder.Default
    private List<String> textParts = new ArrayList<>();

    /** image/png 字节经 Base64 编码（仅服务内部；异步任务结果见 {@code gemini_image_tasks.result_json}） */
    @Builder.Default
    private List<String> imagesBase64 = new ArrayList<>();

    /** 图文交织时可选：简易 Markdown（图片为相对路径占位说明，主要见 imagesBase64） */
    private String markdown;
}
