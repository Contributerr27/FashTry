package com.fashtry.backend.bean.dto.gemini;

import java.util.List;
import lombok.Data;

/**
 * AI 写真：1～3 张参考自拍 + 风格预设 + 妆容预设 + 生成张数；每成片一次 Gemini 调用。
 */
@Data
public class GeminiPortraitStudioTaskPayload {

    /** 新任务：1～3 张 COS 地址，顺序与上传一致 */
    private List<String> sourceCosUrls;

    /**
     * 兼容旧任务单字段；与 {@link #sourceCosUrls} 二选一，调度器优先使用非空的 {@code
     * sourceCosUrls}。
     */
    private String sourceCosUrl;

    /** {@link com.fashtry.backend.service.ai.PortraitStudioPrompts#STYLE_KOREAN} 等 */
    private String stylePreset;

    /**
     * {@link com.fashtry.backend.service.ai.PortraitStudioPrompts#MAKEUP_ORIGINAL}、{@link
     * PortraitStudioPrompts#MAKEUP_LIGHT}、{@link PortraitStudioPrompts#MAKEUP_HEAVY}
     */
    private String makeupPreset;

    /**
     * 服装：{@link com.fashtry.backend.service.ai.PortraitStudioPrompts#OUTFIT_STYLE} 按所选写真风格搭配服装；{@link
     * com.fashtry.backend.service.ai.PortraitStudioPrompts#OUTFIT_KEEP_REFERENCE} 尽量保留参考自拍中的衣着。
     */
    private String outfitMode;

    private Integer outputCount;
    private String aspectRatio;
    private String imageSize;
    /** 可选；空则调度器使用默认生图模型 */
    private String modelId;

    /**
     * 单张重生成：非空时与 {@link #parentTaskId} 成对出现；仅调度该槽位（0～outputCount-1），成功后合并进父任务
     * {@code result_json}。
     */
    private Integer regenerateSlotIndex;

    /** 单张重生成时指向整套 AI 写真父任务 ID；普通提交为 null */
    private Long parentTaskId;
}
