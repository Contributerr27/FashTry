package com.fashtry.backend.service.ai;

import java.util.Locale;

/**
 * 服装电商套图：每步均为「从同源图编辑」。指令为英文，便于模型理解；卖点/长图文案语言由 {@link
 * #normalizeOutputLanguage(String)} 控制。
 */
public final class ClothingProductSuitePrompts {

    private ClothingProductSuitePrompts() {}

    public static final int STEP_COUNT = 10;

    /** 与生成顺序一致，用于 {@link com.fashtry.backend.bean.vo.GeminiImageResultJson#imageLabels} */
    public static final String[] LABELS = {
        "ECOM_WHITE_MAIN",
        "ECOM_PREMIUM_MAIN",
        "SCENE_1",
        "SCENE_2",
        "DETAIL_1",
        "DETAIL_2",
        "DETAIL_3",
        "SELL_1",
        "SELL_2",
        "DETAIL_LONG_STRIP"
    };

    /**
     * 用户选择的文案语言代码：zh, en, ja, ko 等；非法或空默认为 zh。
     */
    public static String normalizeOutputLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return "zh";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "zh", "en", "ja", "ko", "es", "fr", "de", "pt", "ru", "ar", "th", "vi", "id", "it" -> s;
            default -> "zh";
        };
    }

    /**
     * 按步骤选用画幅：主图/场景跟用户默认；细节图固定 3:4（常见 896×1200 类竖图）；长图 9:16 竖条。
     */
    public static String aspectRatioForStep(int stepIndex, String payloadAspect) {
        String def = (payloadAspect != null && !payloadAspect.isBlank()) ? payloadAspect.trim() : "3:4";
        if (stepIndex >= 4 && stepIndex <= 8) {
            return "3:4";
        }
        if (stepIndex == 9) {
            return "9:16";
        }
        return def;
    }

    public static String instruction(int stepIndex, String outputLanguage, String modelGender) {
        return instruction(stepIndex, outputLanguage, modelGender, null, null);
    }

    public static String instruction(
            int stepIndex, String outputLanguage, String modelGender, String productBrief, String marketplace) {
        if (stepIndex < 0 || stepIndex >= STEP_COUNT) {
            throw new IllegalArgumentException("stepIndex out of range: " + stepIndex);
        }
        String lang = normalizeOutputLanguage(outputLanguage);
        String genderNorm = ClothingPackshotPrompts.normalizeModelGender(modelGender);
        String genderSuffix = suiteModelGenderSuffix(genderNorm);
        String nt =
                " Do NOT add any text, letters, watermarks, or UI chrome unless this step explicitly asks for"
                    + " marketing copy.";
        String commercial = commercialRetailSuffix();
        String extra = briefAndMarketSuffix(productBrief, marketplace, stepIndex);
        return switch (stepIndex) {
            case 0 -> stepWhiteMain() + nt + commercial + genderSuffix + extra;
            case 1 -> stepPremiumMain() + nt + commercial + genderSuffix + extra;
            case 2 -> stepScene1() + nt + commercial + genderSuffix + extra;
            case 3 -> stepScene2() + nt + commercial + genderSuffix + extra;
            case 4 -> stepDetail1() + nt + commercial + extra;
            case 5 -> stepDetail2() + nt + commercial + extra;
            case 6 -> stepDetail3() + nt + commercial + extra;
            case 7 -> stepSell1(lang) + commercial + genderSuffix + extra;
            case 8 -> stepSell2(lang) + commercial + genderSuffix + extra;
            case 9 -> stepLongStrip(lang) + commercial + genderSuffix + extra;
            default -> throw new IllegalStateException("step " + stepIndex);
        };
    }

    public static String normalizeProductBrief(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replaceAll("\\s+", " ").trim();
        if (s.length() > 800) {
            s = s.substring(0, 800);
        }
        return s;
    }

    /**
     * 「AI 帮写」表单回填：保留换行，去掉 markdown 围栏。
     */
    public static String sanitizeAiBriefForForm(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) {
                s = s.substring(nl + 1);
            }
            int end = s.lastIndexOf("```");
            if (end >= 0) {
                s = s.substring(0, end);
            }
            s = s.trim();
        }
        s = s.replaceAll("\n{3,}", "\n\n");
        if (s.length() > 800) {
            s = s.substring(0, 800);
        }
        return s;
    }

    /**
     * 看图写卖点：输出与前端占位相同的 5 行结构，语言跟 {@code outputLanguage}。
     */
    public static String aiBriefInstruction(String outputLanguage, String marketplace, String modelGender) {
        String lang = normalizeOutputLanguage(outputLanguage);
        String market = marketplacePhrase(normalizeMarketplace(marketplace));
        String genderNorm = ClothingPackshotPrompts.normalizeModelGender(modelGender);
        String genderPhrase = ClothingPackshotPrompts.modelGenderPresentationPhrase(genderNorm);
        return "You are a fashion ecommerce merchandiser. Look at this garment photo and draft a listing brief for "
                + market
                + ". Output language: "
                + outputLanguageName(lang)
                + " ONLY. If later shots include a model, they should present as "
                + genderPhrase
                + ". Do NOT invent fiber percentages, measurements, certifications, brand names, or prices. "
                + "Do NOT wrap in markdown. No preamble or closing remark. Exactly 5 numbered lines:\n"
                + aiBriefHeadings(lang)
                + "\nKeep the whole reply under 350 characters if possible, 800 maximum. "
                + "For specs, only write what is visually obvious (color, category, silhouette); otherwise say to follow the physical hangtag.";
    }

    private static String outputLanguageName(String lang) {
        return switch (normalizeOutputLanguage(lang)) {
            case "en" -> "English";
            case "ja" -> "Japanese";
            case "ko" -> "Korean";
            case "es" -> "Spanish";
            case "fr" -> "French";
            case "de" -> "German";
            case "pt" -> "Portuguese";
            case "ru" -> "Russian";
            case "ar" -> "Arabic";
            case "th" -> "Thai";
            case "vi" -> "Vietnamese";
            case "id" -> "Indonesian";
            case "it" -> "Italian";
            default -> "Simplified Chinese";
        };
    }

    private static String aiBriefHeadings(String lang) {
        return switch (normalizeOutputLanguage(lang)) {
            case "en" ->
                    "1. Product name: ...\n2. Key selling points: ... (2-4 short phrases)\n3. Audience: ...\n4. Desired scenes: ...\n5. Specs: ...";
            case "ja" ->
                    "1.商品名：...\n2.主な訴求：...（短いフレーズ2～4個）\n3.想定顧客：...\n4.想定シーン：...\n5.仕様：...";
            case "ko" ->
                    "1.제품명: ...\n2.핵심 셀링 포인트: ... (짧은 문구 2~4개)\n3.타깃: ...\n4.희망 장면: ...\n5.스펙: ...";
            default ->
                    "1.产品名称：...\n2.核心卖点：...（2～4个短词）\n3.适用人群：...\n4.期望场景：...\n5.具体参数：...";
        };
    }

    public static String normalizeMarketplace(String raw) {
        if (raw == null || raw.isBlank()) {
            return "taobao";
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "amazon" -> "amazon";
            case "tiktok", "tiktokshop" -> "tiktok";
            case "dtc", "shopify", "independent" -> "dtc";
            default -> "taobao";
        };
    }

    private static String briefAndMarketSuffix(String productBrief, String marketplace, int stepIndex) {
        StringBuilder sb = new StringBuilder();
        String market = normalizeMarketplace(marketplace);
        sb.append(" TARGET LISTING CHANNEL: ").append(marketplacePhrase(market)).append('.');
        String brief = normalizeProductBrief(productBrief);
        boolean copyStep = stepIndex == 2 || stepIndex == 3 || stepIndex >= 7;
        if (copyStep && !brief.isEmpty()) {
            sb.append(" MERCHANT BRIEF (use for selling copy and scene styling only; do not invent measurements or fiber %): ")
                    .append(brief);
        }
        return sb.toString();
    }

    private static String marketplacePhrase(String market) {
        return switch (market) {
            case "amazon" -> "Amazon main + A+ style listing images";
            case "tiktok" -> "TikTok Shop / short-video commerce product cards";
            case "dtc" -> "DTC brand site PDP, campaign-ready";
            default -> "Taobao / Tmall / 1688 Chinese marketplace listing";
        };
    }

    /** 主图/场景/卖点/长图：可能出现模特时在英文指令中锁定呈现倾向。 */
    private static String suiteModelGenderSuffix(String normalizedGender) {
        String phrase = ClothingPackshotPrompts.modelGenderPresentationPhrase(normalizedGender);
        return " [MODEL GENDER — RETAIL BRIEF] If a human model, visible partial body, or human-read mannequin appears:"
            + " presentation must be " + phrase + " and consistent with typical catalog demographics for this SKU."
            + " If the shot is strictly product-only with no figure, omit this gender styling constraint.";
    }

    /**
     * 每一步统一要求：可上架、可商用授权的电商成片质量（非随手拍、非概念稿）。
     */
    private static String commercialRetailSuffix() {
        return " COMMERCIAL RETAIL STANDARD (paid listings / brand use): output must be sharp, clean, and"
            + " marketplace-ready—comparable to commissioned studio work for Tmall, Amazon, or JD. Accurate"
            + " garment color and material read, neutral professional color grading, no plastic oversharpening, no"
            + " muddy noise, no warped seams or hardware, no melted logos or lace, no amateur snapshot or stock"
            + " collage look. Suitable for licensed commercial promotion.";
    }

    private static String stepWhiteMain() {
        return "Edit into a TOP-TIER marketplace MAIN image (white-background hero): seamless #FFFFFF sweep,"
            + " softbox-style even lighting, subtle natural floor/contact shadow, perfect centering, sRGB-neutral"
            + " whites. Garment must stay TRUE to the input—same SKU, cut, trims, prints, and logos. No props,"
            + " no mannequin head unless input implies it. Looks like a professional packshot retouched for"
            + " Tmall/Amazon primary slot.";
    }

    private static String stepPremiumMain() {
        return "Edit into a TOP-TIER alternate MAIN image (premium studio): subtle luxury backdrop—cool gradient,"
            + " marble, or matte stone—controlled studio lighting, gentle vignette, single hero garment, generous"
            + " negative space. Campaign-ready luxury ecommerce look. Garment identical to input; no redesign;"
            + " no extra model face unless already in source.";
    }

    private static String stepScene1() {
        return "COMMERCIAL lifestyle scene #1 (fashion retail / outdoor editorial—NOT food or dining). Choose ONE"
            + " coherent setting: clean urban sidewalk or plaza, tree-lined park path, coastal boardwalk, modern"
            + " building facade in soft daylight, boutique storefront window, OR minimalist outdoor concrete plinth"
            + " / matte backdrop roll. DEFAULT and PREFERRED: (1) model wearing the SAME outfit with correct human"
            + " scale and natural pose, OR (2) full outfit on a portable rack / stand with believable shadow, OR (3)"
            + " TOP-DOWN flat lay on ONE continuous neutral surface—stone bench top, linen sweep, or matte riser—NOT"
            + " a dining or café table. FORBIDDEN (hard reject): café tables, coffee cups, saucers, plates, cutlery,"
            + " restaurant patios, picnic spreads with food, bar counters, kitchen islands used as styling surfaces."
            + " No doll/mini scale; no floating garments; soft natural light, realistic shadows, shallow depth, no"
            + " pasted collage look.";
    }

    private static String stepScene2() {
        return "COMMERCIAL lifestyle scene #2, MUST read clearly different from scene #1 (different location type)."
            + " Indoor options: bright minimalist showroom, airy loft with large windows, hotel lobby with vertical"
            + " lines, fitting-room mirror corner, gallery white cube—avoid kitchen/dining as hero. Outdoor options if"
            + " scene #1 was urban: rooftop open sky (minimal furniture), forest trail, riverside walk. DEFAULT: model"
            + " in same outfit OR rack/hanger OR flat lay on ONE believable non-food surface (full wood platform,"
            + " linen sweep, marble plinth without tableware). FORBIDDEN: dining tables, coffee/tea service, plates,"
            + " café seating as main set, food props. Soft window or natural light, graded shadows on fabric, adult"
            + " scale, one SKU, minimal non-food props.";
    }

    private static String stepDetail1() {
        return "Brand lookbook MACRO fabric shot: ONE frame, tight crop on texture/weave/sheen; true material"
            + " fidelity. High-end product page detail slot—razor focus, creamy bokeh, neutral grading. No collage,"
            + " no grid, no contact sheet.";
    }

    private static String stepDetail2() {
        return "Brand lookbook CONSTRUCTION shot: ONE frame—seams, stitching, collar/cuff/hem, placket, or zipper."
            + " Crisp inspection quality for PDP 'craftsmanship' module. NO collage, NO 2x2 grid, NO montage.";
    }

    private static String stepDetail3() {
        return "Brand lookbook EXTRA detail: ONE frame—label/tag, pocket, hem, or hardware; must match input"
            + " garment. Premium PDP supplementary image. No split panels.";
    }

    private static String stepSell1(String lang) {
        return "COMMERCIAL selling-point poster (fashion PDP / paid ads). LAYOUT RULES (critical): (1) The garment"
            + " must stay fully visible—NO text overlapping the clothes, straps, neckline, waist seam, or skirt;"
            + " text ONLY in dedicated safe zones: LEFT or RIGHT vertical margin column, OR a clean TOP or BOTTOM"
            + " caption band (20–28% frame height) with padding, OR split layout with product on one side and copy on"
            + " the other. (2) Typography: refined modern sans-serif (premium brand look), MEDIUM size—NOT oversized"
            + " billboard letters; balanced line spacing; subtle hierarchy, not three giant lines stacked over the"
            + " product. (3) 2–3 short lines, max ~6 Chinese characters OR 4 English words per line. "
            + marketingCopyRule(lang)
            + " (4) Soft natural shadow under the garment, gentle separation from background; neutral studio backdrop."
            + " No crowded centered text stack crushing the outfit.";
    }

    private static String stepSell2(String lang) {
        return "Second COMMERCIAL selling-point graphic. Same layout discipline: copy and icons ONLY in margins or"
            + " bottom/third strip—NEVER on top of the garment body. Prefer left rail: small icon + short label"
            + " (max 4 words or ~6 Chinese chars per line), refined sans, medium weight. OR horizontal icon row in"
            + " the LOWER 18–22% of frame with clear padding above the hem. "
            + marketingCopyRule(lang)
            + " No mixed languages. Product hero sharp with soft shadow; breathable layout, flagship-banner polish,"
            + " not template text slammed center.";
    }

    private static String stepLongStrip(String lang) {
        return "COMMERCIAL long-scroll PDP (Taobao/Tmall/JD). Hero garment MUST match the input. "
            + languageDescription(lang)
            + " (1) FABRIC / COMPOSITION (面料说明·成份): include EXACTLY ONE such section—never duplicate two"
            + " identical '面料说明' blocks. Do NOT use only solid gray bars for body copy: use 2–4 SHORT LEGIBLE"
            + " lines of real text in the target language with SAFE retail wording—e.g. Simplified Chinese may use"
            + " '具体成分及含量以实物吊牌为准' '手感舒适亲肤' '洗护方式请见水洗标'—do NOT invent fiber percentages or millimeter"
            + " specs. NO repeated English phrases like 'Brand Philosophy' twice, NO random mixed-language filler, NO"
            + " obvious typos. (2) Selling bullets: 3–5 lines max, no duplicate copy-pasted lists. (3) Size table:"
            + " headers + dashes or simple numbers if readable; optional one short note like '手工测量存在误差' in"
            + " Chinese context—NO long fake footnotes. (4) Care row: pictograms preferred; if text, max 3 tiny words."
            + " (5) Overall: clean flagship layout, not empty template with gray bars everywhere.";
    }

    private static String marketingCopyRule(String lang) {
        return "IMPORTANT — on-image copy language: " + languageDescription(lang);
    }

    private static String languageDescription(String lang) {
        return switch (normalizeOutputLanguage(lang)) {
            case "zh" -> "All headings, bullets, and captions must be in Simplified Chinese (简体中文).";
            case "en" -> "All headings, bullets, and captions must be in English.";
            case "ja" -> "すべての見出し・箇条書き・キャプションは日本語で書くこと。";
            case "ko" -> "모든 제목·불릿·캡션은 한국어로 작성할 것.";
            case "es" -> "Todos los títulos, viñetas y leyendas deben estar en español.";
            case "fr" -> "Tous les titres, puces et légendes doivent être en français.";
            case "de" -> "Alle Überschriften, Aufzählungen und Bildunterschriften müssen auf Deutsch sein.";
            case "pt" -> "Todos os títulos, marcadores e legendas devem estar em português.";
            case "ru" -> "Все заголовки, маркеры и подписи должны быть на русском языке.";
            case "ar" -> "يجب أن تكون جميع العناوين والنقاط والتعليقات باللغة العربية.";
            case "th" -> "หัวข้อ บูลเล็ต และคำบรรยายทั้งหมดต้องเป็นภาษาไทย";
            case "vi" -> "Mọi tiêu đề, gạch đầu dòng và chú thích phải bằng tiếng Việt.";
            case "id" -> "Semua judul, poin, dan keterangan harus dalam Bahasa Indonesia.";
            case "it" -> "Tutti i titoli, elenchi e didascalie devono essere in italiano.";
            default -> "All headings, bullets, and captions must be in Simplified Chinese (简体中文).";
        };
    }
}
