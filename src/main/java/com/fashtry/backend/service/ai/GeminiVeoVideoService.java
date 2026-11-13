package com.fashtry.backend.service.ai;

import com.fashtry.backend.common.config.GeminiApiProperties;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.google.genai.Client;
import com.google.genai.types.GenerateVideosConfig;
import com.google.genai.types.GenerateVideosOperation;
import com.google.genai.types.GenerateVideosResponse;
import com.google.genai.types.GeneratedVideo;
import com.google.genai.types.Image;
import com.google.genai.types.Video;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gemini API：Veo 图生视频（起始帧 + 文本提示），使用与 {@link GeminiImageService} 相同的 {@link Client}。
 *
 * <p>视频生成是长时间运行的异步作业：{@link Client#models()} 的 {@code generateVideos} 会立即返回
 * {@link GenerateVideosOperation}，必须通过 {@link Client#operations()} 的 {@code getVideosOperation}
 * 周期性刷新 operation，直到 {@code done} 为 true 后再读取 {@code response} 并取回视频（与官方文档中的
 * 轮询循环一致）。
 */
@Service
public class GeminiVeoVideoService {

    /** 与 {@code modelVideoPollIntervalMs} 配合的上限次数，避免无限阻塞（默认 10s 间隔约 20 分钟）。 */
    private static final int MAX_POLLS = 120;

    private final GeminiApiProperties props;
    private final GeminiKeyPool keyPool;

    public GeminiVeoVideoService(GeminiApiProperties props, GeminiKeyPool keyPool) {
        this.props = props;
        this.keyPool = keyPool;
    }

    public boolean isReady() {
        return keyPool.isReady();
    }

    /**
     * 阻塞直至 Veo 完成或超时（轮询次数用尽），返回 MP4 字节。
     *
     * @param durationSeconds 生成时长（秒），传 {@link GenerateVideosConfig#durationSeconds()}
     * @param resolution 720p / 1080p，传 {@link GenerateVideosConfig#resolution()}
     * @param aspectRatio 16:9 / 9:16，传 {@link GenerateVideosConfig#aspectRatio()}
     */
    /**
     * 图生视频容易改脸、换背景。用户提示词里若还没有这条约束，调用前补上。
     */
    static String withFrameLock(String prompt) {
        String base = prompt == null ? "" : prompt.trim();
        if (base.contains("不要切换场景") && base.contains("不要改变人物")) {
            return base;
        }
        return base
                + "\n不要改变人物的脸、五官、发型、肤色、体型和身份，不要改变服装款式与颜色，不要改变背景、地点和光线。不要切换场景，不要剪到新环境，整段是同一个连续镜头。";
    }

    public byte[] generateVideoFromImageBytes(
            String prompt,
            byte[] imageBytes,
            String imageMimeType,
            int durationSeconds,
            String resolution,
            String aspectRatio) {
        if (!isReady()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Gemini / Veo 未配置");
        }
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "视频描述不能为空");
        }
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "起始帧图片为空");
        }
        String mime = StringUtils.hasText(imageMimeType) ? imageMimeType.trim() : "image/png";
        if (!mime.startsWith("image/")) {
            mime = "image/png";
        }
        String modelId =
                StringUtils.hasText(props.getVeoModelId())
                        ? props.getVeoModelId().trim()
                        : "veo-3.1-lite-generate-preview";
        Image image = Image.builder().imageBytes(imageBytes).mimeType(mime).build();
        String resNorm = props.normalizeResolutionToken(resolution);
        String aspectNorm = props.normalizeAspectRatioToken(aspectRatio);
        int dur = "1080p".equals(resNorm) ? 8 : Math.max(1, durationSeconds);
        GenerateVideosConfig config =
                GenerateVideosConfig.builder()
                        .numberOfVideos(1)
                        .durationSeconds(dur)
                        .resolution(resNorm)
                        .aspectRatio(aspectNorm)
                        .personGeneration("allow_adult")
                        .build();
        List<GeminiKeyPool.Slot> order = keyPool.plan();
        if (order.isEmpty()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "未配置可用的 Gemini API Key");
        }
        GeminiKeyPool.Slot slot = order.get(0);
        Client client = keyPool.client(slot);
        GenerateVideosOperation op = null;
        Exception last = null;
        for (int i = 0; i < order.size(); i++) {
            slot = order.get(i);
            client = keyPool.client(slot);
            try {
                op = client.models.generateVideos(modelId, withFrameLock(prompt), image, config);
                last = null;
                break;
            } catch (Exception e) {
                last = e;
                keyPool.markBad(slot, e);
                if (!GeminiKeyPool.failoverWorthy(e) || i == order.size() - 1) {
                    break;
                }
            }
        }
        if (op == null) {
            if (last instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "视频生成被中断");
            }
            throw new BusinessException(
                    ResultCode.SYSTEM_ERROR, "Veo 调用失败: " + (last == null ? "" : last.getMessage()));
        }
        try {
            int interval = Math.max(3000, props.getModelVideoPollIntervalMs());
            int polls = 0;
            while (polls < MAX_POLLS && operationNotDone(op)) {
                Thread.sleep(interval);
                op = client.operations.getVideosOperation(op, null);
                polls++;
            }
            if (operationNotDone(op)) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "视频生成超时，请稍后重试");
            }
            op.error()
                    .ifPresent(
                            err -> {
                                throw new BusinessException(
                                        ResultCode.SYSTEM_ERROR, "Veo 错误: " + summarizeError(err));
                            });
            Optional<GenerateVideosResponse> respOpt = op.response();
            if (respOpt.isEmpty()) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "Veo 无返回内容");
            }
            List<GeneratedVideo> list = respOpt.get().generatedVideos().orElse(List.of());
            if (list.isEmpty()) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "Veo 未生成视频");
            }
            Optional<Video> vOpt = list.get(0).video();
            if (vOpt.isEmpty()) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "Veo 视频对象为空");
            }
            Video video = vOpt.get();
            byte[] bytes;
            if (video.videoBytes().isPresent()) {
                bytes = video.videoBytes().get();
            } else {
                Path tmp = Files.createTempFile("veo-", ".mp4");
                try {
                    client.files.download(video, tmp.toString(), null);
                    bytes = Files.readAllBytes(tmp);
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
            keyPool.markOk(slot);
            return bytes;
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "视频生成被中断");
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Veo 调用失败: " + e.getMessage());
        }
    }

    private static boolean operationNotDone(GenerateVideosOperation op) {
        return !op.done().isPresent() || !op.done().get();
    }

    private static String summarizeError(Object err) {
        if (err instanceof Map<?, ?> m && !m.isEmpty()) {
            Object msg = m.get("message");
            if (msg != null) {
                return String.valueOf(msg);
            }
            return m.toString();
        }
        return err == null ? "unknown" : String.valueOf(err);
    }
}
