package com.fashtry.backend.bean.dto.gemini;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GeminiInterleavedTaskPayload {

    private String prompt;
    private String secondPrompt;
    private String modelId;
    private String aspectRatio;
    private String imageSize;

    /** 已上传 COS 的参考图 URL，空则纯文本交织 */
    private List<String> imageCosUrls = new ArrayList<>();
}
