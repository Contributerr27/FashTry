package com.fashtry.backend.bean.dto.gemini;

import lombok.Data;

/**
 * 模特出图（平铺 → 模特 → 多场景变体 → 白底主图），由调度器顺序调用 Gemini。
 */
@Data
public class GeminiPackshotTaskPayload {

    private String sourceCosUrl;
    /** 室内休闲 indoor_leisure / 城市街拍 urban_street / 简约棚拍 minimal_studio / 动态抓拍 dynamic_candid（兼容旧三档） */
    private String artDirection;
    /** 旧版任务可能仅存此字段；调度器会映射到 {@link #artDirection} */
    private String ecomScene;
    /** 女 female / 男 male / 不限 neutral */
    private String modelGender;
    /** 年龄段：CHILD 儿童 / ADULT 成人 / SENIOR 老人 */
    private String ageGroup;
    /** 体型：SLIM 偏瘦 / AVERAGE 标准 / FULL 偏胖或丰满 */
    private String bodyType;
    /** 模特气质简述，如 young Asian，可为空 */
    private String modelStyleNote;
    /**
     * 面孔来源：PRESET=下方预设；UPLOAD=用户上传参考脸图（{@link #faceReferenceCosUrl}）。
     */
    private String faceMode;
    /**
     * 预设面孔：ASIAN_ECOM, WESTERN_FASHION, MIXED_LUXE, EDITORIAL_SUPERMODEL（仅 PRESET 时有效；旧任务存量的历史键仍可由
     * {@link com.fashtry.backend.service.ai.ClothingPackshotPrompts#normalizeFacePreset} 归一）。
     */
    private String facePreset;
    /** 参考脸图 COS 地址（仅 UPLOAD 时有效）；第一步多图顺序为平铺图 + 本图 */
    private String faceReferenceCosUrl;
    /**
     * 构图策略：FULL_LENGTH=全程全身；HALF_LENGTH=全程半身；AUTO=每张由模型在全身/半身中择一（仍禁止含糊裁切）。
     */
    private String framingMode;
    /**
     * 场景变体数量（每张为不同环境 + 不同姿势），建议 2～7；总出图约 1 + sceneVariantCount + 1。
     */
    private Integer sceneVariantCount;
    private String modelId;
    private String aspectRatio;
    private String imageSize;

    /** 单张重生成时：父任务（整套模特出图）ID */
    private Long parentTaskId;
    /** 单张重生成时：在整套结果 urls 中的下标，0 = 上身，1…sceneN = 场景，sceneN+1 = 白底 */
    private Integer regenerateSlotIndex;
}
