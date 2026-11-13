package com.fashtry.backend.bean.dto.gemini;

import lombok.Data;

/** 已上传 COS 的模特图、服装图 URL，供定时任务拉取字节后调 Gemini */
@Data
public class GeminiTryOnTaskPayload {

    private String modelCosUrl;
    private String garmentCosUrl;
    private String instruction;
    private String modelId;
    private String aspectRatio;
    private String imageSize;
}
