package com.fashtry.backend.service.ai;

import com.fashtry.backend.bean.dto.gemini.GeminiPackshotTaskPayload;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 模特出图固定英文模板，降低风格漂移；与 Flash 图模配合使用。
 */
public final class ClothingPackshotPrompts {

    public static final String FACE_MODE_PRESET = "PRESET";
    public static final String FACE_MODE_UPLOAD = "UPLOAD";

    /** 全程全身（默认，电商主图常见） */
    public static final String FRAMING_MODE_FULL = "FULL_LENGTH";
    /** 全程半身 */
    public static final String FRAMING_MODE_HALF = "HALF_LENGTH";
    /** 每张由模型在全身/半身中择一 */
    public static final String FRAMING_MODE_AUTO = "AUTO";

    private ClothingPackshotPrompts() {}

    /**
     * 归一构图模式；缺省为 {@link #FRAMING_MODE_FULL}。兼容前端与旧任务 null。
     */
    public static String normalizeFramingMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return FRAMING_MODE_FULL;
        }
        String t = raw.trim();
        String u = t.toUpperCase(Locale.ROOT);
        if (u.contains("HALF")
                || t.contains("半身")
                || t.contains("半長")
                || t.contains("腰上")
                || t.contains("胸上")) {
            return FRAMING_MODE_HALF;
        }
        if (u.equals(FRAMING_MODE_AUTO)
                || u.contains("AUTO")
                || t.contains("自选")
                || t.contains("自动")
                || t.contains("自動")) {
            return FRAMING_MODE_AUTO;
        }
        if (u.equals(FRAMING_MODE_FULL)
                || u.contains("FULL")
                || t.contains("全身")
                || t.contains("从头到脚")) {
            return FRAMING_MODE_FULL;
        }
        return FRAMING_MODE_FULL;
    }

    public static final String AGE_CHILD = "CHILD";
    public static final String AGE_ADULT = "ADULT";
    public static final String AGE_SENIOR = "SENIOR";

    public static final String BODY_SLIM = "SLIM";
    public static final String BODY_AVERAGE = "AVERAGE";
    public static final String BODY_FULL = "FULL";

    /** 儿童 / 成人 / 老人；缺省成人。兼容中英文与常见别名。 */
    public static String normalizeAgeGroup(String raw) {
        if (!StringUtils.hasText(raw)) {
            return AGE_ADULT;
        }
        String t = raw.trim();
        String u = t.toUpperCase(Locale.ROOT);
        if (u.contains("CHILD")
                || u.contains("KID")
                || u.contains("MINOR")
                || t.contains("小孩")
                || t.contains("儿童")
                || t.contains("少儿")
                || t.contains("未成年")
                || t.contains("童子")) {
            return AGE_CHILD;
        }
        if (u.contains("SENIOR")
                || u.contains("ELDER")
                || t.contains("老人")
                || t.contains("老年")
                || t.contains("长者")) {
            return AGE_SENIOR;
        }
        if (u.contains("ADULT") || t.contains("成人") || t.contains("成年")) {
            return AGE_ADULT;
        }
        return AGE_ADULT;
    }

    /** 偏瘦 / 标准 / 偏胖；缺省标准。 */
    public static String normalizeBodyType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return BODY_AVERAGE;
        }
        String t = raw.trim();
        String u = t.toUpperCase(Locale.ROOT);
        if (u.contains("SLIM")
                || u.contains("THIN")
                || t.contains("瘦")
                || t.contains("纤细")
                || t.contains("苗条")) {
            return BODY_SLIM;
        }
        if (u.contains("FULL")
                || u.contains("PLUS")
                || u.contains("CURVY")
                || t.contains("胖")
                || t.contains("丰满")
                || t.contains("大码")
                || t.contains("微胖")) {
            return BODY_FULL;
        }
        if (u.contains("AVERAGE") || t.contains("标准") || t.contains("中等")) {
            return BODY_AVERAGE;
        }
        return BODY_AVERAGE;
    }

    private static String ageBandEnglish(String normalizedAge) {
        return switch (normalizeAgeGroup(normalizedAge)) {
            case AGE_CHILD ->
                    "CHILD model — clearly juvenile face and body proportions appropriate for youth fashion catalog; "
                            + "never render as adult or elderly; keep styling age-appropriate and family-safe";
            case AGE_SENIOR ->
                    "SENIOR / elderly model — clearly mature-aged face and body; never render as child or young adult";
            default ->
                    "ADULT model — post-puberty mature proportions; never render as child or as elderly (use SENIOR "
                            + "selection for elderly)";
        };
    }

    private static String bodyTypeEnglish(String normalizedBody) {
        return switch (normalizeBodyType(normalizedBody)) {
            case BODY_SLIM ->
                    "SLIM / lean build — believable slender framing; do not add visible extra weight or bulk";
            case BODY_FULL ->
                    "FULLER / soft or plus-appropriate figure — do not slim to sample-size default; keep proportions "
                            + "coherent with the selection";
            default ->
                    "AVERAGE / mid-range healthy build — avoid extreme thinness or heaviness relative to the gender brief";
        };
    }

    /**
     * 服装套图等场景：将用户选择的性别规范化为英文提示短语（female/male/neutral）。
     */
    public static String modelGenderPresentationPhrase(String rawOrNormalized) {
        return switch (normalizeModelGender(rawOrNormalized)) {
            case "male" -> "male-presenting";
            case "female" -> "female-presenting";
            default -> "neutral / androgynous presentation";
        };
    }

    private static String modelGenderPresentationEnglish(String modelGender) {
        return modelGenderPresentationPhrase(modelGender);
    }

    /** 第一步与 second prompt 使用的「本任务构图锁定」说明 */
    private static String framingJobLockEnglish(String framingMode) {
        String m = normalizeFramingMode(framingMode);
        if (FRAMING_MODE_HALF.equals(m)) {
            return "[FRAMING LOCK — THIS ENTIRE JOB]\n"
                    + "- Every image in this job MUST be HALF-LENGTH only (waist-up, chest-up, or mid-thigh at most). "
                    + "Feet and footwear must NEVER appear. Do not switch to full-body for any shot.\n\n";
        }
        if (FRAMING_MODE_AUTO.equals(m)) {
            return "[FRAMING MODE — AUTO]\n"
                    + "- Per image, choose either FULL-LENGTH or HALF-LENGTH (never ambiguous in-between). Pick what "
                    + "best suits the garment in that shot, but obey all FRAMING & POSE RULES and the job's pose-diversity "
                    + "requirements.\n\n";
        }
        return "[FRAMING LOCK — THIS ENTIRE JOB]\n"
                + "- Every image in this job MUST be FULL-LENGTH only (head to toe, both feet fully visible, small margin "
                + "below shoes). Do not use HALF-LENGTH for any shot unless the garment is strictly a top-only SKU and "
                + "full-length would be illogical — default to full-length for dresses, pants, and full outfits.\n\n";
    }

    /**
     * 场景变体 / 白底用：压缩版规则 + 任务构图锁定一行，减少重复 token；完整版仅在 {@link #step1UserPrompt} 使用。
     */
    private static String framingAndPoseRulesCompact(String framingMode) {
        String m = normalizeFramingMode(framingMode);
        String lockLine =
                switch (m) {
                    case FRAMING_MODE_HALF -> "- Job framing lock: HALF-LENGTH only on this image; feet must not appear.\n";
                    case FRAMING_MODE_AUTO ->
                            "- Job framing (AUTO): each image FULL- or HALF-LENGTH only (no ambiguous crops); pick per shot.\n";
                    default -> "- Job framing lock: FULL-LENGTH on this image (head to toe, feet fully in frame).\n";
                };
        return "[FRAMING & POSE — COMPACT — MUST OBEY]\n"
                + "- Exactly FULL-LENGTH or HALF-LENGTH; no in-between leg crops; no \"missing feet\" full-body illusions.\n"
                + "- Pose must differ clearly from THIS EDIT's source image (stance, arms, head, weight); no duplicate "
                + "silhouettes across outputs.\n"
                + lockLine;
    }

    private static String compactTailReminder() {
        return "\n[FINAL REMINDER]\n"
                + "- Obey the FRAMING lock line above and the NEW POSE. One photorealistic PDP-quality image. No text, no "
                + "watermark.\n"
                + "- Avoid plastic skin, HDR halos, oversharpening, or synthetic \"AI gloss\"; keep pores and fabric "
                + "texture believable with natural light behavior.\n";
    }

    /**
     * 电商主图：构图（全身/半身二选一）+ 多图间姿势必须区分（与 {@link #step1UserPrompt}、场景变体、白底共用）。
     */
    private static String framingAndPoseRulesEcommercePdp() {
        return "[FRAMING & POSE RULES — STRICTLY ENFORCED]\n\n"
                + "- Each generated image must follow EXACTLY ONE framing type: FULL-LENGTH or HALF-LENGTH.\n"
                + "- Mixed, ambiguous, or intermediate framing is NOT allowed.\n\n"
                + "- Each image MUST have a DIFFERENT body pose.\n"
                + "- No repetition of identical standing, arm position, or silhouette across images.\n"
                + "- Poses should vary naturally (e.g., standing relaxed, walking, turning, looking aside, hand in "
                + "pocket, etc.), while still respecting the framing rules below.\n\n"
                + "FULL-LENGTH:\n"
                + "- The subject must be fully visible from head to toe.\n"
                + "- Both feet must be fully visible inside the frame and must NOT be cropped.\n"
                + "- The pose must clearly read as a full-body standing or full-body action shot.\n"
                + "- A small margin below the shoes is preferred.\n\n"
                + "HALF-LENGTH:\n"
                + "- The subject must be framed as waist-up, chest-up, or mid-thigh at most.\n"
                + "- Legs below mid-thigh must NOT be visible.\n"
                + "- Feet and footwear must NEVER appear in the frame.\n"
                + "- The pose must clearly read as an intentional editorial or lookbook upper-body composition.\n\n"
                + "INVALID COMPOSITIONS (STRICTLY FORBIDDEN):\n"
                + "- Any full-body-looking standing pose where feet are missing or cropped.\n"
                + "- Any partial leg cropping that is not clearly intentional half-body framing.\n"
                + "- Any ambiguous in-between crop that does not clearly belong to FULL-LENGTH or HALF-LENGTH.\n"
                + "- Repeated identical poses across images.\n\n"
                + "All compositions must be intentional, visually clean, and diverse in pose.\n";
    }

    public static String normalizeFaceMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return FACE_MODE_PRESET;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (FACE_MODE_UPLOAD.equals(s)) {
            return FACE_MODE_UPLOAD;
        }
        return FACE_MODE_PRESET;
    }

    /**
     * 四套电商面孔；空或未知默认亚洲电商脸。历史任务中的旧键会映射到新四类。
     */
    public static String normalizeFacePreset(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "ASIAN_ECOM";
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "ASIAN_ECOM",
                    "WESTERN_FASHION",
                    "MIXED_LUXE",
                    "EDITORIAL_SUPERMODEL" -> u;
            case "ANY", "EAST_ASIAN", "SOUTHEAST_ASIAN", "SOUTH_ASIAN" -> "ASIAN_ECOM";
            case "WHITE_EURO", "LATIN" -> "WESTERN_FASHION";
            case "BLACK", "MIDDLE_EASTERN" -> "WESTERN_FASHION";
            default -> "ASIAN_ECOM";
        };
    }

    /**
     * 画面风格：室内休闲 / 城市街拍 / 简约棚拍 / 动态抓拍。兼容旧值 minimal、luxury、street 与中英文关键词。
     */
    public static String normalizeArtDirection(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "minimal_studio";
        }
        String t = raw.trim();
        String s = t.toLowerCase(Locale.ROOT);
        String u = t.toUpperCase(Locale.ROOT).replace('-', '_');
        if (u.equals("INDOOR_LEISURE")
                || u.equals("URBAN_STREET")
                || u.equals("MINIMAL_STUDIO")
                || u.equals("DYNAMIC_CANDID")) {
            return u.toLowerCase(Locale.ROOT);
        }
        if ("indoor_leisure".equals(s)
                || "urban_street".equals(s)
                || "minimal_studio".equals(s)
                || "dynamic_candid".equals(s)) {
            return s;
        }
        // 旧版三档 → 新四档
        if ("minimal".equals(s)) {
            return "minimal_studio";
        }
        if ("luxury".equals(s)) {
            return "indoor_leisure";
        }
        if ("street".equals(s)) {
            return "urban_street";
        }
        // 中文与语义关键词（先匹配更具体的，避免误归类）
        if (t.contains("动态") || t.contains("抓拍") || s.contains("candid")) {
            return "dynamic_candid";
        }
        if (t.contains("街拍") || (t.contains("城市") && t.contains("街"))) {
            return "urban_street";
        }
        if (t.contains("棚拍") || (t.contains("简约") && !t.contains("街头"))) {
            return "minimal_studio";
        }
        if (t.contains("室内")
                && (t.contains("休闲") || t.contains("咖啡") || t.contains("居家") || t.contains("咖啡馆"))) {
            return "indoor_leisure";
        }
        if (s.contains("lux") || t.contains("高端") || t.contains("奢")) {
            return "indoor_leisure";
        }
        if (s.contains("street") || (t.contains("街") && !t.contains("棚"))) {
            return "urban_street";
        }
        if (s.contains("studio") && s.contains("minimal")) {
            return "minimal_studio";
        }
        if (s.contains("cafe") || s.contains("coffee") || s.contains("cozy")) {
            return "indoor_leisure";
        }
        return "minimal_studio";
    }

    public static String normalizeModelGender(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "neutral";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.contains("male") && !s.contains("female")) {
            return "male";
        }
        if (s.contains("female") || s.contains("女")) {
            return "female";
        }
        if (s.contains("男")) {
            return "male";
        }
        return "neutral";
    }

    /** 仅旧任务 payload 含 {@code ecomScene}、无 {@code artDirection} 时使用 */
    private static String normalizeLegacyEcomSceneKey(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "HOME_INDOOR";
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (u) {
            case "CAFE", "COFFEE", "COFFEE_SHOP" -> "CAFE";
            case "GYM", "FITNESS", "FITNESS_GYM" -> "GYM";
            case "URBAN_STREET", "STREET", "CITY_STREET" -> "URBAN_STREET";
            case "HOME_INDOOR", "HOME", "INDOOR", "INTERIOR" -> "HOME_INDOOR";
            case "OUTDOOR_NATURE", "NATURE", "OUTDOOR" -> "OUTDOOR_NATURE";
            default -> legacyEcomFromChinese(raw.trim());
        };
    }

    private static String legacyEcomFromChinese(String s) {
        if (s.contains("咖啡")) {
            return "CAFE";
        }
        if (s.contains("健身")) {
            return "GYM";
        }
        if (s.contains("街拍") || (s.contains("城市") && s.contains("街"))) {
            return "URBAN_STREET";
        }
        if (s.contains("家居") || s.contains("室内")) {
            return "HOME_INDOOR";
        }
        if (s.contains("自然") || s.contains("户外")) {
            return "OUTDOOR_NATURE";
        }
        return "HOME_INDOOR";
    }

    private static String legacyArtDirectionFromEcomKey(String k) {
        return switch (k) {
            case "CAFE", "GYM", "HOME_INDOOR" -> "indoor_leisure";
            case "URBAN_STREET" -> "urban_street";
            case "OUTDOOR_NATURE" -> "dynamic_candid";
            default -> "minimal_studio";
        };
    }

    public static String effectiveArtDirection(GeminiPackshotTaskPayload p) {
        if (p == null) {
            return "minimal_studio";
        }
        if (StringUtils.hasText(p.getArtDirection())) {
            return normalizeArtDirection(p.getArtDirection());
        }
        if (StringUtils.hasText(p.getEcomScene())) {
            return legacyArtDirectionFromEcomKey(normalizeLegacyEcomSceneKey(p.getEcomScene()));
        }
        return "minimal_studio";
    }

    /**
     * @param hasFaceReferenceImage 为 true 时，多模态输入顺序为：Image 1 平铺服装，Image 2 参考脸图
     * @param ageGroup {@link #normalizeAgeGroup}
     * @param bodyType {@link #normalizeBodyType}
     * @param framingMode {@link #normalizeFramingMode}
     */
    public static String step1UserPrompt(
            String artDirection,
            String modelGender,
            String ageGroup,
            String bodyType,
            String faceMode,
            String facePreset,
            String modelStyleNote,
            boolean hasFaceReferenceImage,
            String framingMode) {
        String ad = bundleForDirection(normalizeArtDirection(artDirection));
        String gp = modelGenderPresentationEnglish(modelGender);
        String resolvedLook = resolveModelLookNote(faceMode, facePreset, modelStyleNote);
        String lookAnchors = englishHardAnchorsFromUserLook(modelStyleNote);
        String imageIntro =
                hasFaceReferenceImage
                        ? "Image 1 is a FLAT-LAY / lay-flat product photo of ONE garment (may include minor wrinkles; "
                                + "no person).\n"
                                + "Image 2 is a REFERENCE PORTRAIT for FACE and HAIR IDENTITY ONLY. Reproduce this "
                                + "person's facial structure, skin tone, age impression, and visible hair (style and "
                                + "texture). Completely IGNORE clothing, accessories, jewelry, and background in Image "
                                + "2 — they must NOT appear in the output. Do not copy any outfit from Image 2.\n\n"
                        : "Image 1 is a FLAT-LAY / lay-flat product photo of ONE garment (may include minor wrinkles; "
                                + "no person).\n\n";
        String demoLock =
                hasFaceReferenceImage
                        ? "- Identity is LOCKED to Image 2 for face shape, skin tone, and hair as visible there. The "
                                + "written Look below refines expression, grooming, or makeup only and must NOT "
                                + "override Image 2's ethnicity or facial identity.\n"
                        : "- You MUST NOT substitute a different face type or ethnicity than the Look line describes. "
                                + "Match the preset facial character exactly.\n";
        return imageIntro
                + "TASK: Generate exactly ONE photorealistic e-commerce / online retail catalog hero photograph: a "
                + "single model wearing this EXACT garment, suitable as the primary image on a product detail page (PDP). "
                + "Use professional fashion photography vocabulary: clean exposure, realistic fabric drape, believable "
                + "studio or environmental light, sharp garment edges, no amateur snapshot look.\n\n"
                + "[ART DIRECTION]\n"
                + ad
                + "\n\n"
                + "[MODEL BRIEF — DEMOGRAPHICS ARE BINDING]\n"
                + "- Gender presentation: "
                + gp
                + ".\n"
                + "- Age band (binding): "
                + ageBandEnglish(ageGroup)
                + ".\n"
                + "- Body type (binding): "
                + bodyTypeEnglish(bodyType)
                + ".\n"
                + "- Face / model look (binding): "
                + resolvedLook
                + ".\n"
                + demoLock
                + (StringUtils.hasText(lookAnchors) ? lookAnchors + "\n" : "")
                + framingAndPoseRulesEcommercePdp()
                + "\n"
                + framingJobLockEnglish(framingMode)
                + "[POSE — FIRST IMAGE IN SET]\n"
                + "- This job outputs multiple images; each must use a clearly different pose across the set. For this "
                + "first hero image, choose ONE natural, distinctive pose (not a stiff stock duplicate). Later steps will "
                + "vary pose further — establish a readable stance and silhouette.\n\n"
                + "- Apply FULL-LENGTH or HALF-LENGTH only (see above). For FULL-LENGTH, show the full garment head-to-toe "
                + "when the product is full-body wear; for HALF-LENGTH, keep torso, sleeves, and neckline clear — no "
                + "forbidden partial legs or feet.\n\n"
                + "[GARMENT FIDELITY — HARD CONSTRAINTS]\n"
                + "- Copy the garment from Image 1 with the SAME colors, pattern, silhouette, neckline, sleeve length, "
                + "hem length, logos, prints, buttons, texture, and stitching.\n"
                + "- This is a wear-the-flatlay task: the sold item must match Image 1; do NOT invent a different "
                + "dress/shirt/pants, do NOT change design lines, and do NOT replace the product with a generic similar "
                + "outfit.\n"
                + "- Do NOT redesign, recolor, simplify, or \"improve\" the garment.\n"
                + "- If Image 1 shows front of the item, show the same visible design; do not invent unseen back details "
                + "unless they are logically implied and minimal.\n\n"
                + "[OUTPUT]\n"
                + "- Single image only. No text, no watermark, no collage, no mannequin.\n"
                + "- Neutral or softly styled background suitable for the art direction (simple, not busy).\n";
    }

    /** 合并预设面孔与可选备注，供第一步提示使用 */
    public static String resolveModelLookNote(String faceMode, String facePreset, String modelStyleNote) {
        String fm = normalizeFaceMode(faceMode);
        StringBuilder sb = new StringBuilder();
        if (FACE_MODE_UPLOAD.equals(fm)) {
            sb.append(
                    "Facial identity, skin tone, age impression, and visible hair from the photo must match Image 2 "
                            + "(the reference portrait). Do not invent a different person. Ignore clothing and "
                            + "background in Image 2.");
        } else {
            String fp = facePresetToEnglish(normalizeFacePreset(facePreset));
            if (StringUtils.hasText(fp)) {
                sb.append(fp);
            }
            if (sb.isEmpty()) {
                sb.append(facePresetToEnglish("ASIAN_ECOM"));
            }
        }
        if (StringUtils.hasText(modelStyleNote)) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("Additional notes: ").append(modelStyleNote.trim()).append('.');
        }
        return sb.toString().trim();
    }

    private static String facePresetToEnglish(String preset) {
        return switch (normalizeFacePreset(preset)) {
            case "ASIAN_ECOM" ->
                    "East Asian ecommerce face: softer, harmonious features and gentle proportions typical of China, "
                            + "Japan, and Korea online fashion listings; natural, approachable beauty.";
            case "WESTERN_FASHION" ->
                    "Western fashion face: strong bone structure, defined jaw and cheekbones, editorial confidence; "
                            + "high-fashion energy common in many international brand campaigns.";
            case "MIXED_LUXE" ->
                    "Mixed / Eurasian face between Asian softness and Western structure; refined, premium \"luxury\" "
                            + "look favored in high-end ecommerce.";
            case "EDITORIAL_SUPERMODEL" ->
                    "Distinctive editorial supermodel face: prominent cheekbones, memorable character; suited to "
                            + "premium and design-forward brands.";
            default -> "";
        };
    }

    public static String step1SecondPrompt(String framingMode) {
        String m = normalizeFramingMode(framingMode);
        String lock =
                FRAMING_MODE_HALF.equals(m)
                        ? "This job is HALF-LENGTH only — feet must never appear."
                        : FRAMING_MODE_AUTO.equals(m)
                                ? "Choose FULL- or HALF-LENGTH per rules; pose must be distinctive for later variants."
                                : "This job is FULL-LENGTH only — both feet fully in frame with margin below shoes.";
        return "Generate the single PDP-ready catalog photograph now, obeying every MODEL BRIEF, FRAMING & POSE RULES, "
                + "the FRAMING LOCK for this job, and GARMENT FIDELITY. "
                + lock
                + " One natural, distinctive pose for this first image — later images will require different poses. "
                + "Output only one image.";
    }

    /**
     * 备注中的族裔、发型、肤色等补充锚点。年龄与体型由 {@link GeminiPackshotTaskPayload#getAgeGroup()} /
     * {@link GeminiPackshotTaskPayload#getBodyType()} 控制，勿在此重复推断。
     */
    private static String englishHardAnchorsFromUserLook(String modelStyleNote) {
        if (!StringUtils.hasText(modelStyleNote)) {
            return "";
        }
        String raw = modelStyleNote.trim();
        String n = raw.toLowerCase(Locale.ROOT);
        List<String> lines = new ArrayList<>();
        if (raw.contains("亚洲")
                || raw.contains("亞洲")
                || raw.contains("东亚")
                || raw.contains("東亞")
                || raw.contains("华裔")
                || raw.contains("華裔")
                || raw.contains("日韩")
                || raw.contains("日韓")
                || n.contains("east asian")
                || n.contains("asian woman")
                || n.contains("asian man")
                || n.contains("asian model")) {
            lines.add(
                    "- ENFORCED: East Asian facial features and skin undertone consistent with the Look line; not "
                            + "African, not European, not South Asian unless the Look explicitly says otherwise.");
        }
        if (raw.contains("自然卷")
                || raw.contains("捲")
                || raw.contains("卷发")
                || raw.contains("卷髮")
                || n.contains("natural curl")
                || n.contains("curly hair")) {
            lines.add(
                    "- ENFORCED: hair must be naturally curly / wavy as described; do not switch to straight hair or "
                            + "a tight afro unless the Look says so.");
        }
        if (raw.contains("白皮")
                || raw.contains("白皙")
                || n.contains("fair skin")
                || n.contains("pale skin")) {
            lines.add(
                    "- ENFORCED: skin tone must read as fair / light as described; do not darken arbitrarily.");
        }
        if (raw.contains("黄皮")
                || raw.contains("暖皮")
                || raw.contains("自然肤色")
                || n.contains("warm undertone")
                || n.contains("tan skin")) {
            lines.add(
                    "- ENFORCED: skin undertone and complexion must match warm / tan / natural description in the Look; "
                            + "do not switch to cool pink or very pale unless the Look says so.");
        }
        if (raw.contains("黑皮")
                || raw.contains("深肤")
                || raw.contains("美黑")
                || n.contains("dark skin")
                || n.contains("deep skin")) {
            lines.add(
                    "- ENFORCED: skin tone must read as deeper / brown / dark as described; do not lighten to default "
                            + "fair.");
        }
        if (lines.isEmpty()) {
            return "";
        }
        return "[LOOK — ENGLISH ANCHORS — MUST OBEY]\n" + String.join("\n", lines);
    }

    private static String bundleForDirection(String artDirection) {
        return switch (normalizeArtDirection(artDirection)) {
            case "indoor_leisure" ->
                    "Indoor lifestyle / café mood: cozy believable interior, warm natural window light, soft shadows, "
                            + "candid lifestyle photography — grounded and human, not CGI-perfect.";
            case "urban_street" ->
                    "Urban street fashion: real sidewalk, buildings, candid street-style energy, natural daylight, "
                            + "realistic shadows, documentary snap timing — avoid synthetic HDR.";
            case "dynamic_candid" ->
                    "Dynamic candid / slice-of-life: believable mid-motion moments, subtle natural timing, "
                            + "photojournalistic fashion — energetic but still marketplace-appropriate.";
            default ->
                    "Minimal clean studio ecommerce: seamless light backdrop, soft studio lighting, simple readable "
                            + "pose, focus on garment accuracy — professional PDP, no busy props.";
        };
    }

    /** 室内休闲：氛围 + 光线（姿势由 {@link #INDOOR_LEISURE_POSES} 单独给出） */
    private static final String[] INDOOR_LEISURE_SCENES = {
        "Cozy cafe interior near a tall window; warm natural light; soft shadows; shallow depth of field.",
        "Quiet apartment reading nook; pale walls; soft daylight; a houseplant softly blurred in back.",
        "Bright bakery or cafe counter; daylight from storefront glass; casual domestic ambiance.",
        "Morning kitchen or dining corner; gentle natural light; calm everyday interior.",
        "Small cafe lounge with booth seating; pendant lamps; warm wood tones; intimate scale.",
        "Indoor botanical corner with filtered daylight; relaxed domestic backdrop.",
        "Window-side cafe table; wood textures; cozy atmosphere; believable patron spacing.",
        "Scandinavian-style living corner; light wood; calm daylight; uncluttered composition."
    };

    private static final String[] INDOOR_LEISURE_POSES = {
        "Sitting naturally on a chair; holding a coffee cup with relaxed hands; shoulders down; candid seated posture.",
        "Seated sideways on sofa edge; legs relaxed; one hand on cushion — natural resting pose, not stiff modeling.",
        "Standing relaxed near counter or queue; arms loose; weight on one leg; everyday patience pose.",
        "Leaning lightly against wall or pillar; subtle weight shift; hands relaxed — casual cafe energy.",
        "Seated at small table; elbows relaxed; mid-conversation posture — believable lifestyle moment.",
        "Walking slowly through seating area with short natural stride; subtle arm swing; unhurried.",
        "Standing by window in soft profile; relaxed stance; gaze toward daylight — quiet mood.",
        "Half-turn from seat toward camera; unforced gesture; like a friend snapped the photo."
    };

    private static final String[] URBAN_STREET_SCENES = {
        "City sidewalk with buildings and storefronts; urban background; candid street style; natural lighting.",
        "Crosswalk or curb context; glass towers softly defocused; realistic daylight shadows.",
        "Tree-lined boulevard edge; dappled sunlight; everyday city rhythm.",
        "Brick facade and metal railings; soft overcast or gentle sun; documentary fashion.",
        "Weekend market aisle background; lively but shallow depth of field; not chaotic clutter.",
        "Plaza near transit or bike racks; neutral urban materials; crisp daylight.",
        "Side street with soft pedestrian blur in distance; believable metropolitan texture.",
        "Contemporary storefront reflections; sidewalk foreground; street photography authenticity."
    };

    private static final String[] URBAN_STREET_POSES = {
        "Walking naturally on pavement; short stride; natural arm swing — believable street stride.",
        "Standing relaxed at curb or crossing; weight shifted; optional hands in pockets — waiting pose.",
        "Turning slightly mid-step toward camera; subtle pivot — candid snap, not runway exaggeration.",
        "Paused glance toward shop window; torso angled; feet planted casually.",
        "Three-quarter stance with one foot slightly forward; relaxed shoulders — street editorial.",
        "Mid-crosswalk stride with natural balance; everyday commuter timing.",
        "Standing with everyday bag on shoulder; neutral stance; real-life accessorizing only if garment allows.",
        "Light lean on railing or lamppost; casual posture; grounded urban portrait."
    };

    private static final String[] MINIMAL_STUDIO_SCENES = {
        "Minimal studio; clean light-gray seamless; soft even studio lighting; no distracting props.",
        "White cyclorama with gentle floor gradient shadow; softbox key; high-end ecommerce clarity.",
        "Pale backdrop with soft side fill; controlled highlights; catalog-ready simplicity.",
        "Bright neutral seamless; soft frontal light; fashion PDP discipline.",
        "Light tonal backdrop with subtle vignette; soft studio wrap light.",
        "Clean infinity curve; soft shadows under feet; professional retail hero framing.",
        "Neutral gray gradient seamless; balanced exposure; garment-first composition.",
        "High-key soft studio; minimal edge shadow; crisp fabric read."
    };

    private static final String[] MINIMAL_STUDIO_POSES = {
        "Standing relaxed facing camera; arms at sides; simple symmetrical catalog stance.",
        "Subtle three-quarter turn; soft contrapposto; hands relaxed — clean PDP silhouette.",
        "Near-frontal; slight weight on back foot; approachable minimal pose.",
        "Gentle S-curve; minimal gesture; emphasize garment drape and hem lines.",
        "Hands loosely at lower torso or lightly touching side seam — do not cover prints or logos.",
        "One hand lightly at collar or placket; delicate adjustment; keep garment identity visible.",
        "Quiet forward step with softened front knee; still composed, not runway stride.",
        "Standing with chin slightly lowered; modest editorial; shoulders relaxed."
    };

    private static final String[] DYNAMIC_CANDID_SCENES = {
        "Sunny sidewalk with motion-friendly depth; believable shutter timing; natural daylight.",
        "Doorway transition with directional daylight; slice-of-life framing.",
        "Outdoor stair or ramp with geometric light; spontaneous documentary mood.",
        "Busy corner with very subtle distant pedestrian motion blur; photojournalistic restraint.",
        "Cafe terrace edge outdoors; breeze-friendly environment; candid lifestyle.",
        "Crosswalk approach; kinetic urban rhythm without fake CGI motion trails.",
        "Tree-filtered hard-soft sunlight; walking moment.",
        "Open plaza with natural spill light; transitional everyday scene."
    };

    private static final String[] DYNAMIC_CANDID_POSES = {
        "Mid-stride walking; believable arm swing; hem or hair may show only subtle motion — no cartoon blur.",
        "Turning head back over shoulder while body continues forward — candid glance.",
        "Soft laugh or speaking gesture caught mid-moment; relaxed authenticity.",
        "Adjusting sleeve, strap, or hem while moving — everyday motion.",
        "Quick pivot on ball of foot; torso follows slightly late — snapshot timing.",
        "Stepping off curb with natural balance; ordinary urban motion.",
        "Brushing hair from face with one hand; breezy believable gesture.",
        "Reaching toward door handle or railing; transitional slice-of-life pose."
    };

    private static String[] scenePool(String artDirection) {
        return switch (normalizeArtDirection(artDirection)) {
            case "indoor_leisure" -> INDOOR_LEISURE_SCENES;
            case "urban_street" -> URBAN_STREET_SCENES;
            case "dynamic_candid" -> DYNAMIC_CANDID_SCENES;
            default -> MINIMAL_STUDIO_SCENES;
        };
    }

    private static String[] posePool(String artDirection) {
        return switch (normalizeArtDirection(artDirection)) {
            case "indoor_leisure" -> INDOOR_LEISURE_POSES;
            case "urban_street" -> URBAN_STREET_POSES;
            case "dynamic_candid" -> DYNAMIC_CANDID_POSES;
            default -> MINIMAL_STUDIO_POSES;
        };
    }

    /**
     * 每条对应一次 edit：更换为新的环境描述，并采用与索引对应的明显不同姿势（场景池与姿势池按风格拆分，减少 AI 套图感）。
     */
    public static List<String> sceneAndPoseVariantInstructions(
            String artDirection, int count, String framingMode) {
        int n = Math.max(2, Math.min(8, count));
        String[] scenes = scenePool(artDirection);
        String[] poses = posePool(artDirection);
        String fm = normalizeFramingMode(framingMode);
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String scene = scenes[i % scenes.length];
            int poseIdx = (i * 5 + i / 2 + 1) % poses.length;
            String pose = poses[poseIdx];
            out.add(sceneAndPoseEditWrapper(scene, pose, fm));
        }
        return out;
    }

    private static String sceneAndPoseEditWrapper(String sceneSentence, String poseSentence, String framingMode) {
        return "[CRITICAL — GARMENT & PERSON LOCK]\n"
                + "- The source image is the ONLY identity anchor: preserve the exact same face, facial proportions, "
                + "eyes, nose, mouth, skin tone, hairstyle, and body as in that image — do NOT synthesize a different "
                + "person or drift identity across edits.\n"
                + "- Keep the SAME person: face, identity, ethnicity, body, hair texture, skin tone; keep the garment "
                + "identical in color, cut, seams, logos, prints, texture, and fit.\n"
                + "- Do NOT change the clothing design. Do NOT swap the outfit.\n"
                + "- You MUST change BOTH the environment/background (with believable matching light and shadows) AND "
                + "the model's pose, gesture, and camera relationship. This variant must be clearly different from the "
                + "source image in scene AND stance — not a tiny adjustment.\n"
                + "- Pose diversity (MANDATORY): fully adopt the [NEW POSE] below. Do NOT reuse the source image's "
                + "stance, arm positions, weight distribution, or overall silhouette; no duplicate \"same standing\" "
                + "geometry. Obey compact FRAMING & POSE rules below.\n\n"
                + "[NEW ENVIRONMENT]\n"
                + sceneSentence
                + "\n\n"
                + "[NEW POSE — ECOMMERCE]\n"
                + poseSentence
                + "\n\n"
                + framingAndPoseRulesCompact(framingMode)
                + compactTailReminder();
    }

    public static String whiteEcomInstruction(String framingMode) {
        String m = normalizeFramingMode(framingMode);
        String whiteFraming =
                FRAMING_MODE_HALF.equals(m)
                        ? "[WHITE PDP FRAMING]\n"
                                + "- HALF-LENGTH job: upper-body / mid-thigh framing only on white; feet must never appear. "
                                + "Garment hero (top/dress upper) must read clearly.\n\n"
                        : FRAMING_MODE_AUTO.equals(m)
                                ? "[WHITE PDP FRAMING]\n"
                                        + "- Prefer FULL-LENGTH for dresses, pants, and full outfits (feet visible, small "
                                        + "margin below shoes). For top-only SKUs, HALF-LENGTH is acceptable if intentional "
                                        + "and clean. Resolve conflicts in favor of clear marketplace readability.\n\n"
                                : "[WHITE PDP FRAMING]\n"
                                        + "- This job is FULL-LENGTH locked: on white seamless, show head-to-toe with both "
                                        + "feet fully in frame and a little margin below shoes when the garment is full-body "
                                        + "wear.\n\n";
        return "[CRITICAL — GARMENT & PERSON LOCK]\n"
                + "- The source image is the ONLY identity anchor: preserve the exact same face and body as that "
                + "image; do NOT change the model's identity.\n"
                + "- Keep the SAME person (face, ethnicity, hair, body) and the SAME garment with identical design, "
                + "colors, and fit.\n\n"
                + "[POSE DIVERSITY — REQUIRED]\n"
                + "- The body pose MUST differ clearly from the source image: change stance, arm placement, head angle, "
                + "or weight shift — not a duplicate silhouette or identical arm geometry. Natural catalog poses only.\n\n"
                + whiteFraming
                + "[ECOMMERCE WHITE STUDIO — PRIMARY PDP IMAGE]\n"
                + "- Replace the background with a seamless pure white (#FFFFFF) catalog backdrop.\n"
                + "- Soft, even commercial product lighting; gentle floor shadow acceptable; no props.\n"
                + "- Crisp edges, no yellow cast; garment fully visible like a major fashion marketplace listing.\n"
                + framingAndPoseRulesCompact(framingMode)
                + "\n"
                + "Output: single photorealistic image. No text, no watermark.";
    }
}
