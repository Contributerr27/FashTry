package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelVideoStatusVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long taskId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long sourceTryonTaskId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long sourceImageEditTaskId;

    /** TRYON | IMAGE_EDIT | UPLOAD */
    private String sourceType;

    private Integer status;
    private String statusDesc;
    private String prompt;
    private String sourceImageUrl;
    private String resultVideoUrl;
    private String errorMsg;
    private Integer consumeCredits;
    private Long submittedAt;
    private Long processingStartedAt;
    private Long generationDurationMs;

    /**
     * 排队/生成中的经验进度 0–99（完成/失败为 null）；供前端展示百分比，非 API 精确进度。
     */
    private Integer progressPercent;

    private Integer durationSeconds;

    private String resolution;

    /** 16:9 / 9:16 */
    private String aspectRatio;
}
