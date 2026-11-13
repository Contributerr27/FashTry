package com.fashtry.backend.bean.dto.gemini;

import lombok.Data;

import java.util.List;

/**
 * 服装提取：多图批量，同源参数；每图一次 Gemini 编辑出图，结果 URL 与输入顺序一一对应。
 */
@Data
public class GeminiProductRetouchTaskPayload {

    private List<String> sourceCosUrls;
    private String backgroundColor;
    private String aspectRatio;
    /**
     * 提取范围：{@link com.fashtry.backend.service.ai.ProductRetouchPrompts#SCOPE_FULL} 一套、{@link
     * ProductRetouchPrompts#SCOPE_UPPER} 上装、{@link ProductRetouchPrompts#SCOPE_LOWER} 下装。旧任务 JSON 可能无此字段。
     */
    private String garmentScope;
    /** 旧版精修任务；新任务不再写入，读档时忽略。 */
    private String removeHanger;
    private String removePriceTag;
    private String removeBadge;
    private String shootHint;
    private String cropPreference;
    /** 历史任务 JSON 可能含此字段；当前出图模型由 {@code app.gemini.product-retouch-model-id} 固定，不读本字段。 */
    private String modelId;
    private String imageSize;

    /**
     * 单张重生成：与 {@link #parentTaskId} 成对出现；仅处理 {@code sourceCosUrls} 中该索引对应的一张，成功后合并进父任务
     * {@code result_json}。
     */
    private Integer regenerateSlotIndex;

    /** 单张重生成时指向整套服装提取父任务 ID；普通提交为 null */
    private Long parentTaskId;
}
