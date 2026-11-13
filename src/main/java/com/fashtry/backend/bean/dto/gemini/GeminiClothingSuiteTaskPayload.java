package com.fashtry.backend.bean.dto.gemini;

import lombok.Data;

import java.util.List;

/**
 * 服装电商套图：从同一张服装源图串行生成白底主图、高级主图、场景、细节、卖点与详情长图。
 */
@Data
public class GeminiClothingSuiteTaskPayload {

    private String sourceCosUrl;
    private String modelId;
    private String aspectRatio;
    private String imageSize;
    /**
     * 卖点图与详情长图上的文案语言：zh, en, ja, ko 等，见 {@link
     * com.fashtry.backend.service.ai.ClothingProductSuitePrompts#normalizeOutputLanguage(String)}
     */
    private String outputLanguage;
    /**
     * 若成片含模特：female / male / neutral（与 {@link
     * com.fashtry.backend.service.ai.ClothingPackshotPrompts#normalizeModelGender(String)} 一致）。
     */
    private String modelGender;
    /** 商家填写的卖点/品名/场景要求，用于卖点图、场景图与详情长图。 */
    private String productBrief;
    /** 目标平台：taobao / amazon / tiktok / dtc */
    private String marketplace;
    /**
     * 非 null 时仅重生成该步骤（0～9），同源图单步编辑；null 表示整套 10 步。
     */
    private Integer regenerateSlotIndex;
    /**
     * 单步重试任务时指向整套套图任务 ID，便于历史列表合并展示与前端恢复父任务。
     */
    private Long parentTaskId;

    /**
     * 单张重生成排队：当前正在执行的 {@link #regenerateSlotIndex} 之外，待执行的步骤索引（0～9），顺序执行。
     */
    private List<Integer> regenerateSlotQueue;
}
