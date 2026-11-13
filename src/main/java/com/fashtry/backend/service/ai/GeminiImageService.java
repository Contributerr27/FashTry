package com.fashtry.backend.service.ai;

import com.fashtry.backend.bean.dto.GeminiGenerateRequest;
import com.fashtry.backend.bean.dto.GeminiInterleavedRequest;
import com.fashtry.backend.bean.vo.GeminiImageResultVO;
import com.fashtry.backend.common.config.GeminiApiProperties;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.ImageConfig;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Gemini 原生图片能力（Nano Banana 等），经 <a href="https://ai.google.dev">Gemini Developer API</a> 调用。
 * <p>
 * 对外接口仅异步任务入库，COS 上传由 {@link com.fashtry.backend.task.GeminiImageTaskScheduler} 完成。
 * </p>
 */
@Service
public class GeminiImageService {

    private static final Logger log = LoggerFactory.getLogger(GeminiImageService.class);

    /** 局部重绘蒙版说明：与前端 MaskEditor 导出格式一致（黑=保留，白=可编辑）。 */
    private static final String MASK_INPAINT_INSTRUCTION =
            "CRITICAL INPAINTING RULES:\n"
                    + "- Image 1 is the source photo. Image 2 is a binary edit mask with the SAME dimensions.\n"
                    + "- WHITE pixels = ONLY the brush-painted regions the user marked; BLACK = untouched.\n"
                    + "- Apply the user instruction ONLY inside white mask regions (e.g. if they say paint area turns red, "
                    + "only those brushed strokes become red).\n"
                    + "- BLACK pixels MUST stay identical to image 1 (color, texture, embroidery, lighting).\n"
                    + "- Do NOT expand edits beyond white mask regions or recolor the entire garment.";

    private final GeminiApiProperties props;
    private final GeminiKeyPool keyPool;

    public GeminiImageService(GeminiApiProperties props, GeminiKeyPool keyPool) {
        this.props = props;
        this.keyPool = keyPool;
    }

    public boolean isReady() {
        return keyPool.isReady();
    }

    public GeminiImageResultVO generateFromText(GeminiGenerateRequest req) {
        assertReady();
        String modelId = StringUtils.hasText(req.getModelId()) ? req.getModelId() : props.getModelId();
        GenerateContentConfig config = buildNanoContentConfig(req.getAspectRatio(), req.getImageSize(), modelId);
        try {
            GenerateContentResponse response =
                    keyPool.call(c -> c.models.generateContent(modelId, req.getPrompt(), config));
            return toResult(response, false);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Gemini 生成失败: " + e.getMessage());
        }
    }

    public GeminiImageResultVO generateInterleaved(GeminiInterleavedRequest req) {
        assertReady();
        String modelId = StringUtils.hasText(req.getModelId()) ? req.getModelId() : props.getModelId();
        GenerateContentConfig config = buildNanoContentConfig(req.getAspectRatio(), req.getImageSize(), modelId);
        Content content = Content.fromParts(
                Part.fromText(req.getPrompt()),
                Part.fromText(secondPromptOrDefault(req.getSecondPrompt())));
        return doInterleaved(modelId, config, content);
    }

    public GeminiImageResultVO generateInterleavedWithImages(
            List<byte[]> imageBytes,
            List<String> mimeTypes,
            String prompt,
            String secondPrompt,
            String modelId,
            String aspectRatio,
            String imageSize) {
        assertReady();
        if (imageBytes == null || mimeTypes == null || imageBytes.size() != mimeTypes.size()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "图片字节与 MIME 数量不一致");
        }
        String mid = StringUtils.hasText(modelId) ? modelId : props.getModelId();
        GenerateContentConfig config = buildNanoContentConfig(aspectRatio, imageSize, mid);
        List<Part> plist = new ArrayList<>();
        for (int i = 0; i < imageBytes.size(); i++) {
            String mime = StringUtils.hasText(mimeTypes.get(i)) ? mimeTypes.get(i) : "image/png";
            plist.add(Part.fromBytes(imageBytes.get(i), mime));
        }
        plist.add(Part.fromText(prompt));
        plist.add(Part.fromText(secondPromptOrDefault(secondPrompt)));
        Content content = Content.builder().role("user").parts(plist).build();
        return doInterleaved(mid, config, content);
    }

    private static String secondPromptOrDefault(String second) {
        return StringUtils.hasText(second)
                ? second
                : "Create images to go alongside the text as you generate the content.";
    }

    /**
     * 部分模型（如 {@code gemini-2.5-flash} 文本/多模态 Flash）未启用 {@link ImageConfig} 画幅/尺寸，传了会报
     * {@code Aspect ratio is not enabled for models/gemini-2.5-flash}。
     */
    private static boolean isGemini25FlashNoImageConfig(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return false;
        }
        String m = modelId.trim();
        if (m.startsWith("models/")) {
            m = m.substring("models/".length());
        }
        // 与 gemini-2.5-flash-image 等生图变体区分：后者通常支持 imageConfig
        return "gemini-2.5-flash".equalsIgnoreCase(m);
    }

    private static GenerateContentConfig buildNanoContentConfig(
            String aspectRatio, String imageSize, String modelId) {
        GenerateContentConfig.Builder b = GenerateContentConfig.builder()
                .responseModalities("TEXT", "IMAGE")
                .candidateCount(1);
        if (isGemini25FlashNoImageConfig(modelId)) {
            return b.build();
        }
        if (StringUtils.hasText(aspectRatio) || StringUtils.hasText(imageSize)) {
            ImageConfig.Builder ic = ImageConfig.builder();
            if (StringUtils.hasText(aspectRatio)) {
                ic.aspectRatio(aspectRatio);
            }
            if (StringUtils.hasText(imageSize)) {
                ic.imageSize(imageSize);
            }
            b.imageConfig(ic.build());
        }
        return b.build();
    }

    /**
     * 纯文本生成（不使用生图模型）。使用 {@link GeminiApiProperties#getTextModelId()}。
     *
     * @param jsonMode true 时要求 {@code responseMimeType=application/json}
     */
    public GeminiImageResultVO generateText(String prompt, boolean jsonMode) {
        assertReady();
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "提示词为空");
        }
        String mid =
                StringUtils.hasText(props.getTextModelId()) ? props.getTextModelId() : "gemini-2.5-flash";
        GenerateContentConfig.Builder cfg =
                GenerateContentConfig.builder().responseModalities("TEXT").candidateCount(1);
        if (jsonMode) {
            cfg.responseMimeType("application/json");
        }
        Content content = Content.fromParts(Part.fromText(prompt.trim()));
        try {
            GenerateContentResponse response =
                    generateContentWithHttpRetries(mid, content, cfg.build(), "textOnly");
            return toResult(response, false);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Gemini 文本生成失败: " + e.getMessage());
        }
    }

    /**
     * 多模态读单图、只返回文本（不使用 {@code app.gemini.model-id} 生图模型；用于商拍/修图预检等）。
     * 使用 {@link GeminiApiProperties#getTextModelId()}，如 {@code gemini-2.5-flash}。
     */
    public GeminiImageResultVO generateTextFromImage(
            byte[] imageBytes, String mimeType, String userInstruction) {
        String mid =
                StringUtils.hasText(props.getTextModelId()) ? props.getTextModelId() : "gemini-2.5-flash";
        return generateTextFromImage(imageBytes, mimeType, userInstruction, mid);
    }

    /**
     * 多模态读单图、只返回文本。{@code modelId} 为空时回退 {@link GeminiApiProperties#getTextModelId()}。
     * Gemini 3 系列不支持 {@code candidateCount}，配置会自动省略。
     */
    public GeminiImageResultVO generateTextFromImage(
            byte[] imageBytes, String mimeType, String userInstruction, String modelId) {
        assertReady();
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "图片为空");
        }
        if (!StringUtils.hasText(userInstruction)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "分析指令为空");
        }
        String mid = StringUtils.hasText(modelId)
                ? modelId.trim()
                : (StringUtils.hasText(props.getTextModelId()) ? props.getTextModelId() : "gemini-2.5-flash");
        String mt = StringUtils.hasText(mimeType) ? mimeType.trim() : "image/jpeg";
        GenerateContentConfig config = buildTextOnlyConfig(mid);
        Content content = Content.fromParts(Part.fromBytes(imageBytes, mt), Part.fromText(userInstruction));
        try {
            GenerateContentResponse response =
                    generateContentWithHttpRetries(mid, content, config, "textFromImage");
            return toResult(response, false);
        } catch (Exception e) {
            throw new BusinessException(
                    ResultCode.SYSTEM_ERROR, "Gemini 图像分析失败: " + e.getMessage());
        }
    }

    private static GenerateContentConfig buildTextOnlyConfig() {
        return buildTextOnlyConfig(null);
    }

    private static GenerateContentConfig buildTextOnlyConfig(String modelId) {
        GenerateContentConfig.Builder b = GenerateContentConfig.builder().responseModalities("TEXT");
        if (!isGemini3Family(modelId)) {
            b.candidateCount(1);
        }
        return b.build();
    }

    private static boolean isGemini3Family(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        return modelId.trim().toLowerCase(Locale.ROOT).startsWith("gemini-3");
    }

    private GeminiImageResultVO doInterleaved(String modelId, GenerateContentConfig config, Content content) {
        try {
            GenerateContentResponse response =
                    keyPool.call(c -> c.models.generateContent(modelId, content, config));
            return toResult(response, true);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Gemini 图文生成失败: " + e.getMessage());
        }
    }

    /**
     * 单图编辑；可选蒙版（第二张图，白=可改）；可选参考图（无蒙版时为第二张图，有蒙版时为第三张图）。
     */
    public GeminiImageResultVO editImage(
            byte[] imageBytes,
            String mimeType,
            byte[] maskBytes,
            String maskMime,
            byte[] referenceBytes,
            String referenceMime,
            String instruction,
            String modelId,
            String aspectRatio,
            String imageSize) {
        assertReady();
        String mid = StringUtils.hasText(modelId) ? modelId : props.getModelId();
        if (!StringUtils.hasText(mimeType)) {
            mimeType = "image/png";
        }
        boolean hasMask = maskBytes != null && maskBytes.length > 0;
        boolean hasRef = referenceBytes != null && referenceBytes.length > 0;
        String refMimeSafe = StringUtils.hasText(referenceMime) ? referenceMime : "image/png";
        GenerateContentConfig config = buildNanoContentConfig(aspectRatio, imageSize, mid);
        Content content;
        if (hasMask && hasRef) {
            String mm = StringUtils.hasText(maskMime) ? maskMime : "image/png";
            String text =
                    instruction
                            + "\n\n"
                            + MASK_INPAINT_INSTRUCTION
                            + "\n\nThe third image is a visual reference for the desired look (colors, materials, "
                            + "patterns, or style) within the editable (white) regions only.";
            content =
                    Content.fromParts(
                            Part.fromBytes(imageBytes, mimeType),
                            Part.fromBytes(maskBytes, mm),
                            Part.fromBytes(referenceBytes, refMimeSafe),
                            Part.fromText(text));
        } else if (hasMask) {
            String mm = StringUtils.hasText(maskMime) ? maskMime : "image/png";
            String text = instruction + "\n\n" + MASK_INPAINT_INSTRUCTION;
            content = Content.fromParts(
                    Part.fromBytes(imageBytes, mimeType),
                    Part.fromBytes(maskBytes, mm),
                    Part.fromText(text));
        } else if (hasRef) {
            String text =
                    instruction
                            + "\n\n"
                            + "The second image is a visual reference for the desired appearance (colors, materials, "
                            + "patterns, or style cues). Apply the instruction to the first image; use the reference "
                            + "to guide the edited regions.";
            content =
                    Content.fromParts(
                            Part.fromBytes(imageBytes, mimeType),
                            Part.fromBytes(referenceBytes, refMimeSafe),
                            Part.fromText(text));
        } else {
            content =
                    Content.fromParts(Part.fromBytes(imageBytes, mimeType), Part.fromText(instruction));
        }
        try {
            GenerateContentResponse response = generateContentWithHttpRetries(mid, content, config, "editImage");
            return toResult(response, false);
        } catch (Exception e) {
            throw new BusinessException(
                    ResultCode.SYSTEM_ERROR,
                    "Gemini 修图失败: "
                            + e.getMessage()
                            + "（多为本机↔Google API 的 HTTP 瞬断；已对 Failed to execute HTTP 自动重试；仍失败请查网络/代理与 app.gemini.http-timeout-ms）");
        }
    }

    /**
     * 多图同序输入（无蒙版）：图 1…图 N 均为参考，最后一段为用户指令；用于 AI 写真多自拍锁脸等。
     */
    public GeminiImageResultVO editImageWithOrderedReferenceImages(
            List<byte[]> imageBytesList,
            List<String> mimeTypes,
            String instruction,
            String modelId,
            String aspectRatio,
            String imageSize) {
        assertReady();
        if (imageBytesList == null || imageBytesList.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "至少需要一张参考图");
        }
        if (mimeTypes == null || mimeTypes.size() != imageBytesList.size()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "图片字节与 MIME 数量不一致");
        }
        String mid = StringUtils.hasText(modelId) ? modelId : props.getModelId();
        GenerateContentConfig config = buildNanoContentConfig(aspectRatio, imageSize, mid);
        int n = imageBytesList.size();
        String intro =
                String.format(
                        "You are given %d reference image(s) in order (Figure 1 … Figure %d). "
                                + "They are selfie reference(s) of the same person—use all of them together to lock identity, "
                                + "face shape, and skin tone. Figure 1 is the primary pose/framing reference unless later figures "
                                + "clearly improve likeness.\n\n",
                        n, n);
        String text = intro + instruction;
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            byte[] b = imageBytesList.get(i);
            String mime = StringUtils.hasText(mimeTypes.get(i)) ? mimeTypes.get(i) : "image/png";
            parts.add(Part.fromBytes(b, mime));
        }
        parts.add(Part.fromText(text));
        Content content = Content.builder().role("user").parts(parts).build();
        try {
            GenerateContentResponse response =
                    generateContentWithHttpRetries(mid, content, config, "editImageMultiRef");
            return toResult(response, false);
        } catch (Exception e) {
            throw new BusinessException(
                    ResultCode.SYSTEM_ERROR,
                    "Gemini 修图失败: "
                            + e.getMessage()
                            + "（多为本机↔Google API 的 HTTP 瞬断；已对 Failed to execute HTTP 自动重试；仍失败请查网络/代理与 app.gemini.http-timeout-ms）");
        }
    }

    /**
     * 虚拟试穿：图1 模特，图2 服装，生成换装后的单张图（Nano Banana / Gemini 多模态图生图）。
     */
    public GeminiImageResultVO tryOnVirtualDressing(
            byte[] modelBytes,
            String modelMime,
            byte[] garmentBytes,
            String garmentMime,
            String instruction,
            String modelId,
            String aspectRatio,
            String imageSize,
            String garmentCategory,
            Long tryonTaskId) {
        assertReady();
        String mid = StringUtils.hasText(modelId) ? modelId : props.getModelId();
        if (!StringUtils.hasText(modelMime)) {
            modelMime = "image/jpeg";
        }
        if (!StringUtils.hasText(garmentMime)) {
            garmentMime = "image/png";
        }
        GenerateContentConfig config = buildNanoContentConfig(aspectRatio, imageSize, mid);
        String text = StringUtils.hasText(instruction) ? instruction : DEFAULT_TRY_ON_PROMPT;
        text = augmentTryonPromptForGarmentCategory(text, garmentCategory);
        // 角色标注夹在图旁：服装图也是真人自拍时，模型容易把 Image1/Image2 对调（穿反）。
        Content content = Content.fromParts(
                Part.fromText(TRYON_IMAGE1_MODEL_LABEL),
                Part.fromBytes(modelBytes, modelMime),
                Part.fromText(TRYON_IMAGE2_GARMENT_LABEL),
                Part.fromBytes(garmentBytes, garmentMime),
                Part.fromText(text));
        String ctx = tryonTaskId != null ? ("tryonTaskId=" + tryonTaskId) : "tryon";
        log.info(
                "Try-on Gemini HTTP begin {} modelId={} imageSize={} aspectRatio={} modelBytes={} garmentBytes={} "
                        + "instructionLen={} app.gemini.http-timeout-ms={}",
                ctx,
                mid,
                imageSize != null ? imageSize : "?",
                aspectRatio != null ? aspectRatio : "?",
                modelBytes != null ? modelBytes.length : 0,
                garmentBytes != null ? garmentBytes.length : 0,
                text.length(),
                props.getHttpTimeoutMs());
        long t0 = System.currentTimeMillis();
        try {
            GenerateContentResponse response =
                    generateContentWithHttpRetries(mid, content, config, ctx);
            long ms = System.currentTimeMillis() - t0;
            log.info("Try-on Gemini HTTP end {} modelId={} geminiHttpMs={}", ctx, mid, ms);
            return toResult(response, false);
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - t0;
            log.warn(
                    "Try-on Gemini HTTP fail {} modelId={} geminiHttpMs={} err={}",
                    ctx,
                    mid,
                    ms,
                    shortThrowableMessage(e));
            throw new BusinessException(
                    ResultCode.SYSTEM_ERROR,
                    "虚拟试穿生成失败: "
                            + e.getMessage()
                            + "（多为本机↔Google API 的 HTTP 瞬断，与控制台展示无关；已对 Failed to execute HTTP 自动重试；仍失败请查网络/代理与 app.gemini.http-timeout-ms）");
        }
    }

    /** 与 {@link #editImage}、试穿等多图/长请求共用：降低 OkHttp 瞬断导致的失败率。 */
    private static final int GEMINI_HTTP_GENERATE_RETRIES = 4;
    private static final long GEMINI_HTTP_RETRY_BASE_MS = 800L;

    /**
     * SDK 偶发 {@code Failed to execute HTTP request}（OkHttp 层），与模型是否“已算完”无关，短重试可显著降低失败率。
     *
     * @param logContext 日记关联（如 {@code tryonTaskId=123}），便于与试穿调度器日志对齐
     */
    private GenerateContentResponse generateContentWithHttpRetries(
            String modelId, Content content, GenerateContentConfig config, String logContext) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= GEMINI_HTTP_GENERATE_RETRIES; attempt++) {
            try {
                return keyPool.call(c -> c.models.generateContent(modelId, content, config));
            } catch (Exception e) {
                last = e;
                if (attempt < GEMINI_HTTP_GENERATE_RETRIES && isRetryableGeminiSdkHttp(e)) {
                    long backoff = GEMINI_HTTP_RETRY_BASE_MS * attempt;
                    log.warn(
                            "Gemini generateContent will retry ({}/{}) modelId={} ctx={} backoffMs={} err={}",
                            attempt,
                            GEMINI_HTTP_GENERATE_RETRIES,
                            modelId,
                            logContext != null ? logContext : "-",
                            backoff,
                            shortThrowableMessage(e));
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                    continue;
                }
                throw e;
            }
        }
        throw last != null ? last : new IllegalStateException("generateContent returned no response");
    }

    private static String shortThrowableMessage(Throwable e) {
        if (e == null) {
            return "";
        }
        String m = e.getMessage();
        if (m == null) {
            m = e.toString();
        }
        return m.length() > 320 ? m.substring(0, 320) + "…" : m;
    }

    private static boolean isRetryableGeminiSdkHttp(Throwable e) {
        String msg = throwableMessageChain(e);
        if (msg.contains("Failed to execute HTTP request")) {
            return true;
        }
        if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("Timed out")) {
            return true;
        }
        if (msg.contains("Connection reset") || msg.contains("connection reset") || msg.contains("ECONNRESET")) {
            return true;
        }
        if (msg.contains("unexpected end of stream") || msg.contains("Broken pipe")) {
            return true;
        }
        return false;
    }

    private static String throwableMessageChain(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = e;
        while (cur != null) {
            if (cur.getMessage() != null) {
                sb.append(cur.getMessage()).append(' ');
            }
            cur = cur.getCause();
        }
        return sb.toString();
    }

    /** 紧挨模特图，避免把「含人的服装自拍」当成输出主体。 */
    private static final String TRYON_IMAGE1_MODEL_LABEL =
            "[IMAGE 1 — PERSON]\n"
                    + "Keep this identity, face, body, pose, camera angle, and background. "
                    + "This is the only person allowed in the output.";

    private static final String TRYON_IMAGE2_GARMENT_LABEL =
            "[IMAGE 2 — CLOTHES]\n"
                    + "Clothing reference only. If this is a selfie or contains a person/mannequin, ignore that human. "
                    + "Extract the garment (color, cut, fabric, neckline, sleeves, length) and put it on the Image 1 person.";

    /**
     * 默认英文虚拟试穿指令；调用方可在请求里传 {@code instruction} 覆盖。
     * 主文只保留方向/身份/抽衣；裙裤判别放在品类后缀，避免「百褶裙」权重压过真实服装。
     */
    private static final String DEFAULT_TRY_ON_PROMPT =
            "Virtual try-on. Output ONE photorealistic photo.\n\n"
                    + "Goal: Image 1's person, in Image 1's pose and scene, wearing Image 2's clothes.\n"
                    + "Wrong: Image 2's person wearing Image 1's clothes, or copying Image 2's selfie pose/background.\n\n"
                    + "Identity\n"
                    + "- Same face, hair, skin, age, body shape, pose, and background as Image 1.\n"
                    + "- If Image 2 shows a person, mannequin, or selfie (phone, indoor close-up), ignore that human "
                    + "completely — do not use their face, hair, body, pose, or scene.\n\n"
                    + "Clothes\n"
                    + "- Remove Image 1's original outfit completely. No leftover hem, layered skirt, mixed colors, "
                    + "or Image 1 garments peeking through.\n"
                    + "- Copy Image 2's apparel faithfully: color, fabric, pattern, neckline, sleeves, length, and silhouette.\n"
                    + "- Transfer only what is actually visible in Image 2. Do not invent extra pieces "
                    + "(jacket, belt, bow, socks, shoes) that are not in Image 2.\n"
                    + "- Fit and drape the garment naturally on Image 1's body, with lighting consistent with Image 1's scene.\n\n"
                    + "Anatomy\n"
                    + "- Only the hands/arms that belong to the Image 1 person. No extra fingers, ghost hands, "
                    + "or hands copied from Image 2. If a hand is hidden in Image 1, keep it hidden.\n\n"
                    + "Output\n"
                    + "- One image, photorealistic, no text, no collage, no side-by-side.";

    private static final String TRYON_LOWER_BODY_CLASS =
            "Lower-body class — read Image 2 only, never Image 1's original clothes:\n"
                    + "- Dress / one-piece: one continuous garment from bodice through hem; keep it as that dress.\n"
                    + "- Skirt: single hem, no inseam/crotch, not two ankle openings.\n"
                    + "- Trousers: inseam + crotch + two distinct leg hems. Do not turn a skirt/dress into pants, "
                    + "or pants into a skirt.";

    private static final String TRYON_RECAP =
            "Recap: Image 1 person + Image 1 scene + Image 2 clothes. Never the reverse.";

    /**
     * 将「上装 / 下装 / 整身」并入指令。裙裤判别只在下装/整身启用，且只看图 2。
     */
    private static String augmentTryonPromptForGarmentCategory(String text, String garmentCategory) {
        String base = StringUtils.hasText(text) ? text : DEFAULT_TRY_ON_PROMPT;
        String c =
                StringUtils.hasText(garmentCategory)
                        ? garmentCategory.trim().toLowerCase(Locale.ROOT)
                        : "one-pieces";
        if (!"tops".equals(c) && !"bottoms".equals(c) && !"one-pieces".equals(c)) {
            c = "one-pieces";
        }
        String suffix =
                switch (c) {
                    case "tops" ->
                            "\n\n[GARMENT TYPE: top]\n"
                                    + "Change only the upper garment (shirt, jacket, vest, knit) from Image 2. "
                                    + "Keep Image 1's lower body unless Image 2 also clearly shows bottoms. "
                                    + "Do not invent a skirt or pants.\n\n"
                                    + TRYON_RECAP;
                    case "bottoms" ->
                            "\n\n[GARMENT TYPE: bottom]\n"
                                    + "Replace only Image 1's lower garment with the bottom from Image 2. "
                                    + "Keep Image 1's top unless Image 2 also shows a top.\n"
                                    + TRYON_LOWER_BODY_CLASS
                                    + "\n\n"
                                    + TRYON_RECAP;
                    default ->
                            "\n\n[GARMENT TYPE: full garment]\n"
                                    + "Put the complete visible outfit from Image 2 onto Image 1.\n"
                                    + "- If Image 2 is a dress, jumpsuit, or one-piece, transfer it as one piece "
                                    + "(same color, length, neckline, sleeves). That includes a mini dress — "
                                    + "do not keep Image 1's skirt or top.\n"
                                    + "- If Image 2 is a coordinated set, transfer only pieces actually visible there. "
                                    + "Do not add a jacket, belt, or extra layer that is not in Image 2.\n"
                                    + TRYON_LOWER_BODY_CLASS
                                    + "\n\n"
                                    + TRYON_RECAP;
                };
        return base + suffix;
    }

    private void assertReady() {
        if (!isReady()) {
            throw new BusinessException(
                    ResultCode.PARAM_INVALID,
                    "Gemini 图片未启用：请在后台配置 API Key，或设置 app.gemini.enabled=true 与 app.gemini.api-key");
        }
    }

    private static GeminiImageResultVO toResult(GenerateContentResponse response, boolean markdown) {
        return toResultParts(partsFromResponse(response), markdown);
    }

    private static List<Part> partsFromResponse(GenerateContentResponse response) {
        try {
            var pl = response.parts();
            if (pl != null && !pl.isEmpty()) {
                return new ArrayList<>(pl);
            }
        } catch (RuntimeException ignored) {
        }
        return extractPartsFallback(response);
    }

    private static List<Part> extractPartsFallback(GenerateContentResponse response) {
        Optional<List<Candidate>> cands = response.candidates();
        if (cands.isEmpty() || cands.get().isEmpty()) {
            return List.of();
        }
        return cands.get().get(0)
                .content()
                .map(c -> c.parts().orElse(List.of()))
                .orElse(List.of());
    }

    private static GeminiImageResultVO toResultParts(List<Part> parts, boolean markdown) {
        List<String> texts = new ArrayList<>();
        List<String> images = new ArrayList<>();
        StringBuilder md = new StringBuilder();
        int imgIdx = 1;
        for (Part part : parts) {
            if (part.thought().orElse(false)) {
                continue;
            }
            part.text().ifPresent(t -> {
                texts.add(t);
                if (markdown) {
                    md.append(t);
                }
            });
            if (part.inlineData().isPresent()) {
                var blob = part.inlineData().get();
                if (blob.data().isPresent()) {
                    byte[] bytes = blob.data().get();
                    images.add(Base64.getEncoder().encodeToString(bytes));
                    if (markdown) {
                        md.append("\n\n![img-").append(imgIdx++).append("](image/png)\n");
                    }
                }
            }
        }
        GeminiImageResultVO.GeminiImageResultVOBuilder b =
                GeminiImageResultVO.builder().textParts(texts).imagesBase64(images);
        if (markdown) {
            b.markdown(md.toString());
        }
        return b.build();
    }
}
