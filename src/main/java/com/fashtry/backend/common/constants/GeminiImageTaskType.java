package com.fashtry.backend.common.constants;

/**
 * {@link com.fashtry.backend.bean.db.GeminiImageTask#taskType}
 */
public final class GeminiImageTaskType {

    /** 历史值；虚拟试穿已使用 {@code tryon_tasks}，不再写入本表 */
    public static final String TRY_ON = "TRY_ON";
    public static final String TEXT_TO_IMAGE = "TEXT_TO_IMAGE";
    public static final String INTERLEAVED = "INTERLEAVED";
    public static final String EDIT = "EDIT";
    /** 平铺服装图 → 模特上身 → 多场景 → 白底主图 */
    public static final String CLOTHING_PACKSHOT = "CLOTHING_PACKSHOT";
    /** 单张服装图 → 电商套图（白底/高级主图/场景/细节/卖点/长图），固定多步同源编辑 */
    public static final String CLOTHING_PRODUCT_SUITE = "CLOTHING_PRODUCT_SUITE";
    /** 服装提取：多图批量，每图出一张提取图 */
    public static final String PRODUCT_RETOUCH = "PRODUCT_RETOUCH";
    /** AI 写真：单参考图 + 风格预设，按张数串行出图 */
    public static final String PORTRAIT_STUDIO = "PORTRAIT_STUDIO";

    private GeminiImageTaskType() {}
}
