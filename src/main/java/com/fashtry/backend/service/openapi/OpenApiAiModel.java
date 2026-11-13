package com.fashtry.backend.service.openapi;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 开放 API 业务类型（query 与 submit 的 model 参数一致）。
 */
public enum OpenApiAiModel {
    /** 单件虚拟换装（JSON：TryonSubmitRequest） */
    VIRTUAL_TRYON,
    /** 批量换装（JSON：TryonSubmitBatchRequest） */
    BATCH_TRYON,
    /** 服装提取（multipart files + JSON 可选参数） */
    GARMENT_EXTRACT,
    /** AI 写真（multipart files + JSON） */
    PORTRAIT_STUDIO,
    /** 模特出图 / 平铺→上身→场景（multipart file + JSON + 可选 faceReference 部件） */
    MODEL_SHOT,
    /** 服装电商套图（multipart file + JSON） */
    GARMENT_SUITE;

    private static final Map<String, OpenApiAiModel> ALIASES = new HashMap<>();

    static {
        register(VIRTUAL_TRYON, "virtual_tryon", "virtual-tryon", "tryon", "虚拟换装");
        register(BATCH_TRYON, "batch_tryon", "batch-tryon", "batch", "批量换装");
        register(GARMENT_EXTRACT, "garment_extract", "garment-extract", "product_retouch", "服装提取");
        register(PORTRAIT_STUDIO, "portrait_studio", "portrait-studio", "ai_portrait", "AI写真", "ai写真");
        register(MODEL_SHOT, "model_shot", "model-shot", "clothing_packshot", "模特出图");
        register(GARMENT_SUITE, "garment_suite", "garment-suite", "clothing_suite", "product_suite", "服装出图");
    }

    private static void register(OpenApiAiModel model, String... keys) {
        for (String k : keys) {
            ALIASES.put(k.toLowerCase(Locale.ROOT), model);
        }
    }

    public static Optional<OpenApiAiModel> tryParse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String k = raw.trim().toLowerCase(Locale.ROOT);
        if (k.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ALIASES.get(k));
    }

    public String canonicalId() {
        return switch (this) {
            case VIRTUAL_TRYON -> "virtual_tryon";
            case BATCH_TRYON -> "batch_tryon";
            case GARMENT_EXTRACT -> "garment_extract";
            case PORTRAIT_STUDIO -> "portrait_studio";
            case MODEL_SHOT -> "model_shot";
            case GARMENT_SUITE -> "garment_suite";
        };
    }
}
