package com.fashtry.backend.common.config;

import com.fashtry.backend.bean.vo.ModelVideoPricingCellVO;
import com.fashtry.backend.bean.vo.ModelVideoPricingVO;
import com.fashtry.backend.common.constants.GeminiImageTaskType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Gemini Developer API（<a href="https://ai.google.dev">ai.google.dev</a> / AI Studio），使用 API Key，不走 Vertex / GCP 项目凭证。
 */
@Data
@ConfigurationProperties(prefix = "app.gemini")
public class GeminiApiProperties {

    /** 为 false 时不创建 {@link com.google.genai.Client} */
    private boolean enabled = false;

    /**
     * API Key，在 <a href="https://aistudio.google.com/apikey">Google AI Studio</a> 创建
     */
    private String apiKey = "";

    /**
     * 图片生成模型 ID（试衣、模特出图、套图、局部改款、写真等）。服装精修不读此项。
     */
    private String modelId = "gemini-nano-banana-2.1";

    /**
     * 多模态「只出文本」分析等（非服装精修出图）。例如 {@code gemini-2.5-flash}。
     */
    private String textModelId = "gemini-2.5-flash";

    /**
     * 服装套图「AI 帮写」卖点文案：固定用此文本模型（看图出文，不走生图模型）。
     */
    private String suiteBriefModelId = "gemini-3.8-flash";

    /**
     * 服装精修 {@code /api/product-retouch/submit} 出图固定用此模型（不读 {@link #modelId}、不接受客户端覆写）。
     * 须为<strong>可输出图片</strong>的模型；{@code gemini-2.5-flash} 仅文本输出，会报 only supports text output。
     */
    private String productRetouchModelId = "gemini-2.5-flash-image";

    /**
     * PC「局部改款」{@code /api/image-edit}：单次任务成功出图后从钱包扣除的点数；提交时校验可用余额（含冻结抵扣后的可用值），失败不扣。
     */
    private int localEditConsumeCredits = 1;

    /**
     * 小红书图文卡片 {@code /api/xhs-card/generate}：一次文案生成成功后扣点；提交前校验可用余额，失败不扣。
     */
    private int xhsCardConsumeCredits = 1;

    /**
     * 模特出图 {@code /api/clothing-packshot}：按生成步数扣点。步数 = 1（上身）+ 场景变体数 + 1（白底），
     * 总扣点 = 步数 × {@link #packshotCreditsPerStep}；成功完成后一次扣除，失败不扣。
     */
    private int packshotCreditsPerStep = 1;

    /**
     * @param sceneVariantCount 场景变体张数，有效范围 2～8（与提交参数一致）
     */
    public int computePackshotConsumeCredits(int sceneVariantCount) {
        int scenes = Math.max(2, Math.min(8, sceneVariantCount));
        int steps = 1 + scenes + 1;
        return steps * Math.max(0, packshotCreditsPerStep);
    }

    /**
     * 服装电商套图 {@code /api/clothing-product-suite}：固定 10 步同源编辑；扣点 = 10 × {@link
     * #packshotCreditsPerStep}；成功完成后一次扣除。
     */
    public int computeClothingSuiteConsumeCredits() {
        return 10 * Math.max(0, packshotCreditsPerStep);
    }

    /** 服装套图单张重生成：1 步 × {@link #packshotCreditsPerStep} */
    public int computeClothingSuiteSlotRegenerateCredits() {
        return Math.max(0, packshotCreditsPerStep);
    }

    /**
     * 服装精修 {@code /api/product-retouch/submit}：每张出图成功扣点一次；总扣 = 张数 × 本值（与
     * {@link #packshotCreditsPerStep} 默认一致，可单独调低/调高）。
     */
    private int productRetouchCreditsPerImage = 1;

    /** 单任务最多上传张数，与调度器、前端一致 */
    public static final int PRODUCT_RETOUCH_MAX_FILES = 10;

    public int computeProductRetouchConsumeCredits(int fileCount) {
        int n = Math.max(1, Math.min(PRODUCT_RETOUCH_MAX_FILES, fileCount));
        return n * Math.max(0, productRetouchCreditsPerImage);
    }

    /**
     * AI 写真 {@code /api/portrait-studio/submit}：总扣 = 张数 × 本值（默认同 {@link #localEditConsumeCredits} 量级，可单独配置）。
     */
    private int portraitStudioCreditsPerImage = 1;

    /** 单次任务最多生成张数，与调度器、前端一致 */
    public static final int PORTRAIT_STUDIO_MAX_OUTPUTS = 6;

    /** AI 写真参考自拍最多张数 */
    public static final int PORTRAIT_STUDIO_MAX_SOURCE_IMAGES = 3;

    private static final int PORTRAIT_STUDIO_MAX_GEMINI_CALLS = GeminiApiProperties.PORTRAIT_STUDIO_MAX_OUTPUTS;

    public int computePortraitStudioConsumeCredits(int outputCount) {
        int n = Math.max(1, Math.min(PORTRAIT_STUDIO_MAX_OUTPUTS, outputCount));
        return n * Math.max(0, portraitStudioCreditsPerImage);
    }

    /**
     * Veo 图生视频模型 ID（<a href="https://ai.google.dev">Gemini API</a>），默认 veo-3.1-lite-generate-preview
     */
    private String veoModelId = "veo-3.1-lite-generate-preview";

    /**
     * 从已完成试穿结果生成展示视频：历史兼容；动态计费见 {@link #computeModelVideoCredits(int, String)}。
     */
    private int modelVideoConsumeCredits = 12;

    /** 可选视频时长（秒），逗号分隔，须与 Veo 支持的档位一致。 */
    private String modelVideoAllowedDurationSeconds = "4,6,8";

    /** 可选分辨率，逗号分隔，如 720p,1080p。 */
    private String modelVideoAllowedResolutions = "720p,1080p";

    /** 未传 {@code durationSeconds} 时的默认时长（秒）。 */
    private int modelVideoDefaultDurationSeconds = 8;

    /** 未传 {@code resolution} 时的默认分辨率。 */
    private String modelVideoDefaultResolution = "720p";

    /** 可选画幅，逗号分隔：16:9（横屏）,9:16（竖屏）。 */
    private String modelVideoAllowedAspectRatios = "16:9,9:16";

    /** 未传 {@code aspectRatio} 时的默认画幅。 */
    private String modelVideoDefaultAspectRatio = "9:16";

    /**
     * 计费步长（秒），按 {@code ceil(时长/步长)} 计块。
     * 2 秒一块：720p 每块 3 点（约 1.5 点/秒），1080p 每块 5 点（约 2.5 点/秒），
     * 使最便宜套餐上的视频毛利接近 1K 出图。
     */
    private int modelVideoCreditBlockSeconds = 2;

    private int modelVideoCreditsPerBlock720p = 3;

    private int modelVideoCreditsPerBlock1080p = 5;

    /** 单次任务扣点下限（与块计费取较大值）。 */
    private int modelVideoMinCredits = 6;

    /** 在块计费结果上再加的点数。 */
    private int modelVideoExtraCredits = 0;

    /**
     * 为 false 时不开放穿搭视频 API、不跑排队调度。关闭后需改回 true 并重启。
     */
    private boolean modelVideoEnabled = true;

    /**
     * Veo 长轮询间隔（毫秒），{@link com.fashtry.backend.service.ai.GeminiVeoVideoService} 使用。
     */
    private int modelVideoPollIntervalMs = 10_000;

    /**
     * 前端展示「生成中」进度条的经验总时长（毫秒），非精确；Veo 实际可能更长或更短。
     */
    private int modelVideoProgressEstimateMs = 480_000;

    /**
     * 模特视频调度器单任务 CompletableFuture 超时（毫秒）。0 表示 {@code httpTimeoutMs + 900_000}（Veo 往往远慢于单张生图）。
     */
    private int modelVideoSchedulerTaskTimeoutMs = 0;

    public int effectiveModelVideoSchedulerTaskTimeoutMs() {
        return modelVideoSchedulerTaskTimeoutMs > 0 ? modelVideoSchedulerTaskTimeoutMs : httpTimeoutMs + 900_000;
    }

    /**
     * 单次请求 HTTP 超时（毫秒），传给 {@link com.google.genai.types.HttpOptions}。
     * 生图/多图试穿较慢，若出现 {@code RPC::DEADLINE_EXCEEDED}，可适当调大（如 300000）。
     */
    private int httpTimeoutMs = 300_000;

    /**
     * 异步调度器（试穿 tryon_tasks、生图 gemini_image_tasks）里单条任务的 CompletableFuture 超时（毫秒）。
     * 超时记失败，避免线程池任务长时间占满且前端一直停在「生成中」。
     * 0 表示自动：{@code httpTimeoutMs + 300_000}。试穿整段含并行下载两张图、Gemini（最长可达 httpTimeoutMs）、
     * Base64 解码与 COS/缩略图上传；仅 +2 分钟余量时易在高分期或慢网下误杀（Google 侧已出图但响应/COS 仍耗时）。
     */
    private int tryonSchedulerTaskTimeoutMs = 0;

    /**
     * {@code gemini_image_tasks} 调度器单任务超时（毫秒）。0 表示按任务类型自动：
     * 单步类任务同 {@link #effectiveTryonSchedulerTaskTimeoutMs()}；
     * {@link GeminiImageTaskType#CLOTHING_PACKSHOT} 为串行多步（至多 10 次 Gemini），按 {@code httpTimeoutMs × 10 + 300_000}，
     * 避免总墙钟时间被误判为超时。试穿单任务见 {@link #effectiveTryonSchedulerTaskTimeoutMs()}。
     */
    private int geminiImageSchedulerTaskTimeoutMs = 0;

    /** 模特出图：1 次上身 + 至多 8 张场景 + 1 次白底 */
    private static final int PACKSHOT_MAX_GEMINI_CALLS = 10;
    private static final int PRODUCT_RETOUCH_MAX_GEMINI_CALLS = GeminiApiProperties.PRODUCT_RETOUCH_MAX_FILES;

    /** @see #tryonSchedulerTaskTimeoutMs */
    public int effectiveTryonSchedulerTaskTimeoutMs() {
        return tryonSchedulerTaskTimeoutMs > 0 ? tryonSchedulerTaskTimeoutMs : httpTimeoutMs + 300_000;
    }

    /**
     * @param taskType {@link com.fashtry.backend.bean.db.GeminiImageTask#getTaskType()}
     */
    public long effectiveGeminiImageSchedulerTaskTimeoutMs(String taskType) {
        if (geminiImageSchedulerTaskTimeoutMs > 0) {
            return geminiImageSchedulerTaskTimeoutMs;
        }
        if (GeminiImageTaskType.CLOTHING_PACKSHOT.equals(taskType)
                || GeminiImageTaskType.CLOTHING_PRODUCT_SUITE.equals(taskType)) {
            return (long) httpTimeoutMs * PACKSHOT_MAX_GEMINI_CALLS + 300_000L;
        }
        if (GeminiImageTaskType.PRODUCT_RETOUCH.equals(taskType)) {
            return (long) httpTimeoutMs * PRODUCT_RETOUCH_MAX_GEMINI_CALLS + 300_000L;
        }
        if (GeminiImageTaskType.PORTRAIT_STUDIO.equals(taskType)) {
            return (long) httpTimeoutMs * PORTRAIT_STUDIO_MAX_GEMINI_CALLS + 300_000L;
        }
        return effectiveTryonSchedulerTaskTimeoutMs();
    }

    public List<Integer> parsedAllowedDurations() {
        if (!StringUtils.hasText(modelVideoAllowedDurationSeconds)) {
            return List.of(4, 5, 6, 8);
        }
        List<Integer> out = new ArrayList<>();
        for (String p : modelVideoAllowedDurationSeconds.split(",")) {
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            out.add(Integer.parseInt(t));
        }
        return out.isEmpty() ? List.of(4, 5, 6, 8) : List.copyOf(out);
    }

    public List<String> parsedAllowedResolutions() {
        if (!StringUtils.hasText(modelVideoAllowedResolutions)) {
            return List.of("720p", "1080p");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String p : modelVideoAllowedResolutions.split(",")) {
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            seen.add(normalizeResolutionToken(t));
        }
        return seen.isEmpty() ? List.of("720p", "1080p") : new ArrayList<>(seen);
    }

    /**
     * 规范为 API 使用的 {@code 720p} / {@code 1080p}。
     */
    public String normalizeResolutionToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "720p";
        }
        String s = raw.trim().toLowerCase();
        if ("1080p".equals(s)) {
            return "1080p";
        }
        return "720p";
    }

    public List<String> parsedAllowedAspectRatios() {
        if (!StringUtils.hasText(modelVideoAllowedAspectRatios)) {
            return List.of("16:9", "9:16");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String p : modelVideoAllowedAspectRatios.split(",")) {
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            seen.add(normalizeAspectRatioToken(t));
        }
        return seen.isEmpty() ? List.of("16:9", "9:16") : new ArrayList<>(seen);
    }

    /**
     * 规范为 Veo API 的 {@code 16:9} / {@code 9:16}。
     */
    public String normalizeAspectRatioToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "16:9";
        }
        String s = raw.trim().replace('：', ':');
        if ("9:16".equalsIgnoreCase(s)) {
            return "9:16";
        }
        return "16:9";
    }

    public int computeModelVideoCredits(int durationSeconds, String resolutionNorm) {
        int blockSec = Math.max(1, modelVideoCreditBlockSeconds);
        int blocks = (int) Math.ceil(durationSeconds / (double) blockSec);
        int per =
                "1080p".equalsIgnoreCase(resolutionNorm)
                        ? modelVideoCreditsPerBlock1080p
                        : modelVideoCreditsPerBlock720p;
        int c = blocks * per;
        return Math.max(modelVideoMinCredits, c) + Math.max(0, modelVideoExtraCredits);
    }

    /** 进度条经验总时长按任务时长相对默认 8 秒缩放。 */
    public long effectiveModelVideoProgressEstimateMsForTask(Integer durationSeconds) {
        int d = durationSeconds != null && durationSeconds > 0 ? durationSeconds : modelVideoDefaultDurationSeconds;
        double factor = Math.max(0.5, d / 8.0);
        return (long) (Math.max(60_000L, modelVideoProgressEstimateMs) * factor);
    }

    public ModelVideoPricingVO buildModelVideoPricingVo() {
        List<ModelVideoPricingCellVO> matrix = new ArrayList<>();
        for (int d : parsedAllowedDurations()) {
            for (String r : parsedAllowedResolutions()) {
                String rn = normalizeResolutionToken(r);
                matrix.add(
                        ModelVideoPricingCellVO.builder()
                                .durationSeconds(d)
                                .resolution(rn)
                                .credits(computeModelVideoCredits(d, rn))
                                .build());
            }
        }
        return ModelVideoPricingVO.builder()
                .allowedDurationSeconds(parsedAllowedDurations())
                .allowedResolutions(parsedAllowedResolutions())
                .allowedAspectRatios(parsedAllowedAspectRatios())
                .defaultDurationSeconds(modelVideoDefaultDurationSeconds)
                .defaultResolution(normalizeResolutionToken(modelVideoDefaultResolution))
                .defaultAspectRatio(normalizeAspectRatioToken(modelVideoDefaultAspectRatio))
                .matrix(matrix)
                .build();
    }
}
