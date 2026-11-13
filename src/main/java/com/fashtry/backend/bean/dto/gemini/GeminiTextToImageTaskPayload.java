package com.fashtry.backend.bean.dto.gemini;

import lombok.Data;

@Data
public class GeminiTextToImageTaskPayload {

    private String prompt;
    private String modelId;
    private String aspectRatio;
    private String imageSize;
}
