package com.fashtry.backend.bean.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenApiUnifiedTaskVO {

    private String model;

    /** tryon | gemini */
    private String taskKind;

    private TryonStatusVO tryon;

    private GeminiTaskQueryVO gemini;
}
