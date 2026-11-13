package com.fashtry.backend.bean.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Product to Model 任务参数（服务内部传递）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductToModelTaskMessage {

    private Long taskId;
    private Long userId;

    private String productUrl;
    private String modelUrl;
    private String imagePromptUrl;
    private String faceReferenceUrl;
    private String backgroundReferenceUrl;

    private String faceReferenceMode;
    private String prompt;
    private String aspectRatio;
    private String resolution;
    private Integer seed;
    private Integer numImages;
    private String outputFormat;
}
