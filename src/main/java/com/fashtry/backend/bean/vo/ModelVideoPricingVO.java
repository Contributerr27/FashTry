package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelVideoPricingVO {

    private List<Integer> allowedDurationSeconds;
    private List<String> allowedResolutions;
    private List<String> allowedAspectRatios;
    private int defaultDurationSeconds;
    private String defaultResolution;
    private String defaultAspectRatio;
    /** 各「秒数 × 分辨率」组合对应的扣点，供前端展示 */
    private List<ModelVideoPricingCellVO> matrix;
}
