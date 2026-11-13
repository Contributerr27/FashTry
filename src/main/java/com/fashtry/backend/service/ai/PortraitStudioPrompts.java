package com.fashtry.backend.service.ai;

import com.fashtry.backend.common.config.GeminiApiProperties;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * PC「AI 写真」：英文图生图指令，分层包含身份锁定、风格预设、妆容、画面与镜头描述。
 */
public final class PortraitStudioPrompts {

    public static final String STYLE_KOREAN = "KOREAN";
    public static final String STYLE_JAPANESE = "JAPANESE";
    public static final String STYLE_EDITORIAL = "EDITORIAL";
    public static final String STYLE_TRAVEL = "TRAVEL";

    /** 妆容与上传一致，不额外改妆 */
    public static final String MAKEUP_ORIGINAL = "ORIGINAL";
    /** 淡妆 */
    public static final String MAKEUP_LIGHT = "LIGHT";
    /** 浓妆 */
    public static final String MAKEUP_HEAVY = "HEAVY";

    /** 按所选写真风格搭配服装（默认，与历史任务一致） */
    public static final String OUTFIT_STYLE = "STYLE";
    /** 尽量保留参考自拍中的衣着与配饰 */
    public static final String OUTFIT_KEEP_REFERENCE = "KEEP_REFERENCE";

    private static final String FOOTER =
            """
            Ultra realistic, high detail, natural skin texture, cinematic lighting, 8k, professional photography.
            """;

    private PortraitStudioPrompts() {}

    /** 供配置接口返回，顺序即前端展示顺序。 */
    public static List<String> listPresetKeys() {
        return List.of(STYLE_KOREAN, STYLE_JAPANESE, STYLE_EDITORIAL, STYLE_TRAVEL);
    }

    /** 妆容选项顺序。 */
    public static List<String> listMakeupKeys() {
        return List.of(MAKEUP_ORIGINAL, MAKEUP_LIGHT, MAKEUP_HEAVY);
    }

    /** 服装模式：先「按风格换装」再「跟随原图」，与前端分段顺序一致。 */
    public static List<String> listOutfitModeKeys() {
        return List.of(OUTFIT_STYLE, OUTFIT_KEEP_REFERENCE);
    }

    /** 将前端或配置中的风格键规范为大写枚举值，未知时回退韩系。 */
    public static String normalizeStylePreset(String raw) {
        if (!StringUtils.hasText(raw)) {
            return STYLE_KOREAN;
        }
        String s = raw.trim().toUpperCase();
        return switch (s) {
            case STYLE_KOREAN, STYLE_JAPANESE, STYLE_EDITORIAL, STYLE_TRAVEL -> s;
            case "ID_PHOTO" -> STYLE_KOREAN;
            default -> STYLE_KOREAN;
        };
    }

    public static String normalizeMakeupPreset(String raw) {
        if (!StringUtils.hasText(raw)) {
            return MAKEUP_ORIGINAL;
        }
        String s = raw.trim().toUpperCase();
        return switch (s) {
            case MAKEUP_ORIGINAL, MAKEUP_LIGHT, MAKEUP_HEAVY -> s;
            default -> MAKEUP_ORIGINAL;
        };
    }

    /**
     * 服装模式：{@link #OUTFIT_STYLE} 默认；{@link #OUTFIT_KEEP_REFERENCE} 保留参考衣着。兼容旧任务空值。
     */
    public static String normalizeOutfitMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return OUTFIT_STYLE;
        }
        String s = raw.trim().toUpperCase();
        return switch (s) {
            case OUTFIT_KEEP_REFERENCE, "KEEP", "ORIGINAL", "FOLLOW" -> OUTFIT_KEEP_REFERENCE;
            case OUTFIT_STYLE, "SWAP", "CHANGE" -> OUTFIT_STYLE;
            default -> OUTFIT_STYLE;
        };
    }

    private static String identityBlock(int referenceImageCount) {
        int n = Math.max(1, Math.min(GeminiApiProperties.PORTRAIT_STUDIO_MAX_SOURCE_IMAGES, referenceImageCount));
        if (n == 1) {
            return """
                    Use the uploaded image as the primary reference.

                    Preserve the person's identity, facial features, skin tone, and overall likeness.
                    Do not change face structure.
                    Keep the face highly consistent with the reference image.
                    Do not beautify excessively.
                    Avoid changing ethnicity or facial structure.
                    Maintain natural skin texture.

                    """;
        }
        return """
                Preserve the person's identity, facial features, skin tone, and overall likeness across all reference figures.
                Do not change face structure.
                Keep the face highly consistent with the reference selfies combined.
                Do not beautify excessively.
                Avoid changing ethnicity or facial structure.
                Maintain natural skin texture.

                """;
    }

    private static String makeupBlock(String makeupPreset) {
        return switch (makeupPreset) {
            case MAKEUP_LIGHT ->
                    """
                    Makeup: apply natural light makeup only—subtle even skin, soft brows, light lip tint, no heavy contour.
                    Keep it photographic and believable for the chosen portrait style.

                    """;
            case MAKEUP_HEAVY ->
                    """
                    Makeup: apply visible glamour makeup appropriate to the style—defined eyes (liner/shadow), fuller lips,
                    gentle contour and highlight—still photographic, not plastic or cartoonish.

                    """;
            default ->
                    // ORIGINAL — follow uploads
                    """
                    Makeup: keep makeup level and style consistent with what is visible in the reference selfie image(s).
                    Do not invent heavy makeup if the references show bare/minimal makeup; do not strip existing makeup away.

                    """;
        };
    }

    private static String clothingKeepBlock() {
        return """
                Clothing: keep the visible garments, accessories, colors, and patterns in the reference selfie image(s) as consistent as possible.
                Do not replace the outfit with a different wardrobe or style-specific clothing.
                Allow only natural folds, wrinkles, and lighting on the same clothes.

                """;
    }

    /** 仅场景/光线/镜头/情绪（不含 Outfit 行），与 {@link #styleBlock} 搭配「跟随原图」使用。 */
    private static String styleSceneBlock(String preset) {
        return switch (preset) {
            case STYLE_JAPANESE ->
                    """
                    Style: Japanese lifestyle photography
                    Scene: near window, natural home environment
                    Lighting: natural sunlight, slightly overexposed
                    Camera: 50mm lens
                    Mood: calm, candid, soft
                    """;
            case STYLE_EDITORIAL ->
                    """
                    Style: high fashion editorial photography
                    Scene: modern studio or urban background
                    Lighting: dramatic lighting, strong contrast
                    Camera: 85mm lens, professional DSLR
                    Mood: confident, cool, bold
                    """;
            case STYLE_TRAVEL ->
                    """
                    Style: travel photography
                    Scene: beach / street / mountain (choose one that fits the reference)
                    Lighting: golden hour sunlight
                    Camera: mix of wide angle and portrait focal lengths
                    Mood: relaxed, happy, cinematic
                    """;
            default ->
                    // STYLE_KOREAN
                    """
                    Style: Korean portrait photography
                    Scene: minimal indoor studio background
                    Lighting: soft diffused light, bright and clean
                    Camera: 85mm lens, shallow depth of field
                    Mood: gentle, natural, fresh
                    """;
        };
    }

    private static String styleBlock(String preset) {
        return switch (preset) {
            case STYLE_JAPANESE ->
                    """
                    Style: Japanese lifestyle photography
                    Outfit: casual light clothing
                    Scene: near window, natural home environment
                    Lighting: natural sunlight, slightly overexposed
                    Camera: 50mm lens
                    Mood: calm, candid, soft
                    """;
            case STYLE_EDITORIAL ->
                    """
                    Style: high fashion editorial photography
                    Outfit: stylish designer outfit
                    Scene: modern studio or urban background
                    Lighting: dramatic lighting, strong contrast
                    Camera: 85mm lens, professional DSLR
                    Mood: confident, cool, bold
                    """;
            case STYLE_TRAVEL ->
                    """
                    Style: travel photography
                    Outfit: casual travel outfit
                    Scene: beach / street / mountain (choose one that fits the reference)
                    Lighting: golden hour sunlight
                    Camera: mix of wide angle and portrait focal lengths
                    Mood: relaxed, happy, cinematic
                    """;
            default ->
                    // STYLE_KOREAN
                    """
                    Style: Korean portrait photography
                    Outfit: soft pastel outfit
                    Scene: minimal indoor studio background
                    Lighting: soft diffused light, bright and clean
                    Camera: 85mm lens, shallow depth of field
                    Mood: gentle, natural, fresh
                    """;
        };
    }

    /**
     * @param stylePreset 规范后的 {@link #STYLE_KOREAN} 等
     * @param variationIndex 1-based，与 total 组合用于多图时轻微差异化
     * @param total 本任务总张数
     * @param makeupPreset 规范后的 {@link #MAKEUP_ORIGINAL} 等
     * @param referenceImageCount 参考自拍张数 1～3
     * @param outfitMode 规范后的 {@link #OUTFIT_STYLE} 或 {@link #OUTFIT_KEEP_REFERENCE}
     */
    public static String buildInstruction(
            String stylePreset,
            int variationIndex,
            int total,
            String makeupPreset,
            int referenceImageCount,
            String outfitMode) {
        String styleBlockResolved =
                OUTFIT_KEEP_REFERENCE.equals(outfitMode)
                        ? clothingKeepBlock() + styleSceneBlock(stylePreset)
                        : styleBlock(stylePreset);
        String mk = normalizeMakeupPreset(makeupPreset);
        String variation =
                total > 1
                        ? String.format(
                                """
                                This is variation %d of %d: you may subtly adjust pose, framing, or lighting,
                                while strictly keeping identity and facial structure locked to the reference.

                                """,
                                variationIndex, total)
                        : "";
        return identityBlock(referenceImageCount)
                + makeupBlock(mk)
                + "Generate a high-quality professional portrait photo.\n\n"
                + styleBlockResolved
                + "\n"
                + variation
                + FOOTER;
    }
}
