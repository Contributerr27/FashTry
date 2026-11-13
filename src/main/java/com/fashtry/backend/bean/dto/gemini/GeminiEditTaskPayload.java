package com.fashtry.backend.bean.dto.gemini;

import lombok.Data;

@Data
public class GeminiEditTaskPayload {

    private String sourceCosUrl;
    /** 可选：与源图同尺寸的黑白蒙版 COS 地址，白色区域表示允许修改 */
    private String maskCosUrl;
    /** 可选：参考图 COS 地址，辅助颜色/质感/风格（与 submit 的 {@code reference} 文件对应） */
    private String referenceCosUrl;
    private String instruction;
    private String modelId;
    private String aspectRatio;
    private String imageSize;
}
