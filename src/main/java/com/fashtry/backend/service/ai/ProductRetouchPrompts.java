package com.fashtry.backend.service.ai;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 服装提取：对「单张图」的 Gemini 图生图编辑指令（英文），与异步任务
 * {@link com.fashtry.backend.common.constants.GeminiImageTaskType#PRODUCT_RETOUCH} 配套。
 */
public final class ProductRetouchPrompts {

    private ProductRetouchPrompts() {}

    public static final String SCOPE_FULL = "FULL";
    public static final String SCOPE_UPPER = "UPPER";
    public static final String SCOPE_LOWER = "LOWER";

    /**
     * 生成图编辑提示：仅提取服装单品/套装到纯色底静物图，不输出 JSON。
     *
     * @param garmentScope 已归一：{@link #SCOPE_FULL}、{@link #SCOPE_UPPER}、{@link #SCOPE_LOWER}
     * @param shootHint 后端已归一：auto | flat_lay | hanging | on_model
     */
    public static String buildImageEditInstruction(
            String backgroundColorHex,
            String aspectRatio,
            String garmentScope,
            String shootHint,
            String cropPreference) {
        String bg = StringUtils.hasText(backgroundColorHex) ? backgroundColorHex.trim() : "#D3D3D3";
        if (bg.length() > 24) {
            bg = bg.substring(0, 24);
        }
        String ar = StringUtils.hasText(aspectRatio) ? aspectRatio.trim() : "3:4";
        String scope = normalizeGarmentScope(garmentScope);
        String sh = StringUtils.hasText(shootHint) ? shootHint.trim().toLowerCase() : "auto";
        if ("mannequin".equals(sh)) {
            sh = "auto";
        }
        String crop = StringUtils.hasText(cropPreference) ? cropPreference.trim().toLowerCase() : "balanced";

        StringBuilder sb = new StringBuilder();
        sb.append("You are a garment isolation specialist for e-commerce. ")
                .append("Edit the FIRST and ONLY input image into ONE clean result image: extract ONLY the ")
                .append("sellable apparel as a catalog-style product shot on a solid background. ")
                .append("Output a single high-quality image; no collages, no before/after, no watermarks, ")
                .append("no text overlays, no logos unless they are clearly part of the garment fabric.\n\n");
        sb.append("EXTRACTION RULES\n");
        sb.append("- Remove or omit: full human bodies, faces, hands, hair, shoes (unless they are the ")
                .append("requested lower-body product), bags, hangers-as-subject, furniture, busy store scenes, ")
                .append("props, and anything that is NOT the garment piece(s) the user wants to sell.\n");
        sb.append("- If the photo is already a flat lay or garment-only shot, preserve the garment shape and ")
                .append("texture; only normalize lighting and background.\n");
        sb.append("- Do NOT invent a different product; do NOT add mannequin heads or fake limbs.\n\n");
        sb.append("SCOPE (strict)\n");
        switch (scope) {
            case SCOPE_UPPER -> sb.append("- UPPER_BODY: output must contain ONLY upper-body apparel ")
                    .append("(shirts, T-shirts, sweaters, hoodies, jackets, coats, vests, blazers, dresses ")
                    .append("as one-piece upper garments). ")
                    .append("Do not include separate pants, shorts, or skirts unless they are inseparable ")
                    .append("from the same photographed item (e.g. jumpsuit).\n");
            case SCOPE_LOWER -> sb.append("- LOWER_BODY: output must contain ONLY lower-body apparel ")
                    .append("(pants, jeans, shorts, skirts, leggings). ")
                    .append("Do not include tops, jackets, or full-body models.\n");
            default -> sb.append("- FULL_OUTFIT: extract the complete outfit the merchant would sell as one set ")
                    .append("(upper + lower when both are clearly the same look in frame). ")
                    .append("If only one garment type is visible, output that garment alone.\n");
        }
        sb.append("\nSTYLE\n");
        sb.append("- Clean studio packshot suitable for Tmall/Amazon/Shopify; even lighting; true colors; ")
                .append("readable fabric texture.\n");
        sb.append("- Final frame must respect output aspect ratio ")
                .append(ar)
                .append(" with balanced composition for that ratio.\n\n");
        sb.append("BACKGROUND\n");
        sb.append("- Use a seamless solid background color: ")
                .append(bg)
                .append(" (match closely).\n\n");
        sb.append("SHOOTING CONTEXT: ")
                .append(sh)
                .append("\n");
        switch (sh) {
            case "flat_lay" -> sb.append(
                    "- Input likely flat/table; keep garment flat and centered; no fake body.\n");
            case "hanging" -> sb.append("- Input likely on hanger; isolate garment; minimize hanger if visible.\n");
            case "on_model" -> sb.append(
                    "- Input likely on a person: strip away the person; keep ONLY the scoped garment(s); ")
                    .append("rebuild edges naturally.\n");
            default -> sb.append(
                    "- Auto: infer best isolation; if ambiguous, prefer tight garment-only packshot.\n");
        }
        sb.append("\n");
        sb.append("FRAMING: ")
                .append(crop)
                .append("\n");
        switch (crop) {
            case "tight_on_garment" -> sb.append("- Tight crop on the merchandise; minimal margin.\n");
            case "more_margin" -> sb.append("- Generous, even margin around the garment; centered.\n");
            default -> sb.append("- Balanced margin; garment centered, professional catalog composition.\n");
        }
        sb.append("\nReturn the edited result as an image.");
        return sb.toString();
    }

    public static String normalizeGarmentScope(String raw) {
        if (!StringUtils.hasText(raw)) {
            return SCOPE_FULL;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        if (SCOPE_UPPER.equals(t)) {
            return SCOPE_UPPER;
        }
        if (SCOPE_LOWER.equals(t)) {
            return SCOPE_LOWER;
        }
        if (SCOPE_FULL.equals(t)) {
            return SCOPE_FULL;
        }
        if (t.contains("UPPER") || "TOP".equals(t)) {
            return SCOPE_UPPER;
        }
        if (t.contains("LOWER") || t.contains("BOTTOM") || t.contains("PANT")) {
            return SCOPE_LOWER;
        }
        if (t.contains("FULL") || t.contains("SET") || t.contains("OUTFIT")) {
            return SCOPE_FULL;
        }
        return SCOPE_FULL;
    }

    /**
     * 归一化拍摄类型：自动 / 平铺 / 挂拍 / 模特；已去掉人台，若仍传入则按 auto 处理。
     */
    public static String normalizeShootHint(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "auto";
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (t.contains("mannequin")) {
            return "auto";
        }
        if ("flat".equals(t) || "flat_lay".equals(t)) {
            return "flat_lay";
        }
        if ("hang".equals(t) || "hanging".equals(t)) {
            return "hanging";
        }
        if ("model".equals(t) || "on_model".equals(t)) {
            return "on_model";
        }
        if ("auto".equals(t)) {
            return "auto";
        }
        return "auto";
    }
}
