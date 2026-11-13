package com.fashtry.backend.service.business.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fashtry.backend.bean.db.ModelVideoTask;
import com.fashtry.backend.bean.db.TryonTask;
import com.fashtry.backend.bean.db.UserWallet;
import com.fashtry.backend.bean.dto.ModelVideoSubmitRequest;
import com.fashtry.backend.bean.vo.ModelVideoPricingVO;
import com.fashtry.backend.bean.vo.ModelVideoStatusVO;
import com.fashtry.backend.bean.vo.ModelVideoSubmitVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.common.config.GeminiApiProperties;
import com.fashtry.backend.common.constants.ClientSafeMessages;
import com.fashtry.backend.common.constants.ModelVideoSourceType;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.ModelVideoTaskMapper;
import com.fashtry.backend.mapper.TryonTaskMapper;
import com.fashtry.backend.mapper.UserWalletMapper;
import com.fashtry.backend.security.SecurityUtils;
import com.fashtry.backend.service.ai.GeminiImageTaskService;
import com.fashtry.backend.service.ai.GeminiVeoVideoService;
import com.fashtry.backend.service.business.ModelVideoService;
import com.fashtry.backend.service.business.WalletDeductionService;
import com.fashtry.backend.service.storage.CosService;
import com.fashtry.backend.service.storage.UserUploadRecordService;
import com.fashtry.backend.utils.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ModelVideoServiceImpl implements ModelVideoService {

    private static final int STATUS_QUEUED = 0;
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;
    private static final int TRYON_COMPLETED = 2;

    private final TryonTaskMapper tryonTaskMapper;
    private final ModelVideoTaskMapper modelVideoTaskMapper;
    private final UserWalletMapper userWalletMapper;
    private final GeminiApiProperties geminiApiProperties;
    private final SnowflakeIdGenerator idGenerator;
    private final GeminiVeoVideoService veoVideoService;
    private final GeminiImageTaskService geminiImageTaskService;
    private final CosService cosService;
    private final WalletDeductionService walletDeductionService;
    private final UserUploadRecordService userUploadRecordService;

    public ModelVideoServiceImpl(
            TryonTaskMapper tryonTaskMapper,
            ModelVideoTaskMapper modelVideoTaskMapper,
            UserWalletMapper userWalletMapper,
            GeminiApiProperties geminiApiProperties,
            SnowflakeIdGenerator idGenerator,
            GeminiVeoVideoService veoVideoService,
            GeminiImageTaskService geminiImageTaskService,
            CosService cosService,
            WalletDeductionService walletDeductionService,
            UserUploadRecordService userUploadRecordService) {
        this.tryonTaskMapper = tryonTaskMapper;
        this.modelVideoTaskMapper = modelVideoTaskMapper;
        this.userWalletMapper = userWalletMapper;
        this.geminiApiProperties = geminiApiProperties;
        this.idGenerator = idGenerator;
        this.veoVideoService = veoVideoService;
        this.geminiImageTaskService = geminiImageTaskService;
        this.cosService = cosService;
        this.walletDeductionService = walletDeductionService;
        this.userUploadRecordService = userUploadRecordService;
    }

    @Override
    public ModelVideoPricingVO getPricing() {
        ensureModelVideoEnabled();
        return geminiApiProperties.buildModelVideoPricingVo();
    }

    @Override
    public ModelVideoSubmitVO submit(ModelVideoSubmitRequest request) {
        ensureModelVideoEnabled();
        if (!veoVideoService.isReady()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, ClientSafeMessages.AI_IMAGE_NOT_CONFIGURED);
        }
        Long userId = SecurityUtils.getCurrentUserId();
        String mode = ModelVideoSourceType.normalize(request.getSourceType());
        Long sourceTryonId = null;
        Long sourceEditId = null;
        String frameUrl;

        if (ModelVideoSourceType.IMAGE_EDIT.equals(mode)) {
            if (!StringUtils.hasText(request.getImageEditTaskId())) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "请选择改款任务");
            }
            long editId;
            try {
                editId = Long.parseLong(request.getImageEditTaskId().trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "改款任务 ID 无效");
            }
            frameUrl = geminiImageTaskService.resolveFirstOutputCosUrlForLocalEdit(editId, userId);
            sourceEditId = editId;
        } else if (ModelVideoSourceType.UPLOAD.equals(mode)) {
            if (!StringUtils.hasText(request.getSourceImageUrl())) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "请上传首帧图片或填写图片地址");
            }
            frameUrl = request.getSourceImageUrl().trim();
            if (!cosService.isAllowedModelVideoFrameUrl(frameUrl)) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "首帧须为本站业务图地址，请先通过上传接口上传");
            }
        } else {
            if (!StringUtils.hasText(request.getTryonTaskId())) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "请选择试穿任务");
            }
            long tryonId;
            try {
                tryonId = Long.parseLong(request.getTryonTaskId().trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "试穿任务 ID 无效");
            }
            TryonTask tr = tryonTaskMapper.selectById(tryonId);
            if (tr == null || !userId.equals(tr.getUserId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权限或任务不存在");
            }
            if (tr.getStatus() == null || tr.getStatus() != TRYON_COMPLETED) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "请选择已完成的试穿任务");
            }
            if (!StringUtils.hasText(tr.getResultCosUrl())) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "该试穿结果暂无可用成片");
            }
            sourceTryonId = tryonId;
            frameUrl = tr.getResultCosUrl().trim();
        }

        int durationSec = resolveDurationSeconds(request.getDurationSeconds());
        String resolutionNorm = resolveResolution(request.getResolution());
        if ("1080p".equals(resolutionNorm) && durationSec != 8) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "1080p 仅支持 8 秒");
        }
        String aspectNorm = resolveAspectRatio(request.getAspectRatio());
        int need = geminiApiProperties.computeModelVideoCredits(durationSec, resolutionNorm);
        UserWallet wallet = userWalletMapper.selectById(userId);
        int total = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
        int frozen = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
        if (total - frozen < need) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
        }
        long id = idGenerator.nextId();
        ModelVideoTask t = new ModelVideoTask();
        t.setId(id);
        t.setUserId(userId);
        t.setSourceTryonTaskId(sourceTryonId);
        t.setSourceImageEditTaskId(sourceEditId);
        t.setPrompt(request.getPrompt().trim());
        t.setSourceImageUrl(frameUrl);
        t.setDurationSeconds(durationSec);
        t.setResolution(resolutionNorm);
        t.setAspectRatio(aspectNorm);
        t.setStatus(STATUS_QUEUED);
        t.setConsumeCredits(need);
        t.setCreatedAt(LocalDateTime.now());
        modelVideoTaskMapper.insert(t);
        if (ModelVideoSourceType.UPLOAD.equals(mode)) {
            userUploadRecordService.registerIfAbsent(userId, frameUrl, "business", "视频-起始帧图");
        }
        return ModelVideoSubmitVO.builder().taskId(String.valueOf(id)).consumeCredits(need).build();
    }

    @Override
    public ModelVideoStatusVO getStatus(Long taskId) {
        ensureModelVideoEnabled();
        if (taskId == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务 ID 不能为空");
        }
        Long uid = SecurityUtils.getCurrentUserId();
        ModelVideoTask t = modelVideoTaskMapper.selectById(taskId);
        if (t == null || !uid.equals(t.getUserId())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        return toVo(t);
    }

    @Override
    public PageResult<ModelVideoStatusVO> listMyTasks(int page, int size) {
        ensureModelVideoEnabled();
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 50);
        Long userId = SecurityUtils.getCurrentUserId();
        LambdaQueryWrapper<ModelVideoTask> w = new LambdaQueryWrapper<>();
        w.eq(ModelVideoTask::getUserId, userId).orderByDesc(ModelVideoTask::getCreatedAt);
        IPage<ModelVideoTask> pageResult = modelVideoTaskMapper.selectPage(new Page<>(p, s), w);
        List<ModelVideoStatusVO> list =
                pageResult.getRecords().stream().map(this::toVo).collect(Collectors.toList());
        return PageResult.of(list, pageResult.getTotal(), p, s);
    }

    @Override
    public void deductCreditsOnSuccess(ModelVideoTask task) {
        int credits = task.getConsumeCredits() != null ? task.getConsumeCredits() : 0;
        if (credits <= 0) {
            return;
        }
        Long uid = task.getUserId();
        if (uid == null) {
            return;
        }
        walletDeductionService.deductIfEnough(uid, credits, "model_video", task.getId());
    }

    private ModelVideoStatusVO toVo(ModelVideoTask t) {
        long createdMs =
                t.getCreatedAt() != null
                        ? t.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : System.currentTimeMillis();
        Long procMs = null;
        if (t.getProcessingStartedAt() != null) {
            procMs =
                    t.getProcessingStartedAt()
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli();
        }
        Integer st = t.getStatus();
        String err = st != null && st == STATUS_FAILED ? ClientSafeMessages.MODEL_VIDEO_GENERATION_FAILED : null;
        String srcType = inferSourceType(t);
        return ModelVideoStatusVO.builder()
                .taskId(t.getId())
                .sourceTryonTaskId(t.getSourceTryonTaskId())
                .sourceImageEditTaskId(t.getSourceImageEditTaskId())
                .sourceType(srcType)
                .status(st)
                .statusDesc(statusDesc(st == null ? 0 : st))
                .prompt(t.getPrompt())
                .sourceImageUrl(t.getSourceImageUrl())
                .resultVideoUrl(t.getResultVideoUrl())
                .errorMsg(err)
                .consumeCredits(t.getConsumeCredits())
                .submittedAt(createdMs)
                .processingStartedAt(procMs)
                .generationDurationMs(t.getGenerationDurationMs())
                .progressPercent(computeProgressPercent(t))
                .durationSeconds(t.getDurationSeconds())
                .resolution(t.getResolution())
                .aspectRatio(t.getAspectRatio())
                .build();
    }

    /**
     * 排队：3%–15% 随等待时间缓升；生成中：16%–99% 按经验总时长估算（与局部改款类似，非精确）。
     */
    private Integer computeProgressPercent(ModelVideoTask t) {
        Integer st = t.getStatus();
        if (st == null) {
            return null;
        }
        if (st == STATUS_COMPLETED || st == STATUS_FAILED) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (st == STATUS_QUEUED) {
            LocalDateTime created = t.getCreatedAt();
            if (created == null) {
                return 3;
            }
            long waitSec = Math.max(0, Duration.between(created, now).getSeconds());
            return (int) Math.min(15, 3 + waitSec / 4);
        }
        if (st == STATUS_PROCESSING) {
            LocalDateTime proc = t.getProcessingStartedAt();
            if (proc == null) {
                return 16;
            }
            long elapsed = Duration.between(proc, now).toMillis();
            long est =
                    Math.max(
                            60_000L,
                            geminiApiProperties.effectiveModelVideoProgressEstimateMsForTask(
                                    t.getDurationSeconds()));
            int rise = (int) ((elapsed * 84L) / est);
            return Math.min(99, Math.max(16, 15 + rise));
        }
        return null;
    }

    private static String statusDesc(int status) {
        return switch (status) {
            case STATUS_QUEUED -> "排队中";
            case STATUS_PROCESSING -> "生成中";
            case STATUS_COMPLETED -> "已完成";
            case STATUS_FAILED -> "失败";
            default -> "未知";
        };
    }

    private static String inferSourceType(ModelVideoTask t) {
        if (t.getSourceTryonTaskId() != null) {
            return ModelVideoSourceType.TRYON;
        }
        if (t.getSourceImageEditTaskId() != null) {
            return ModelVideoSourceType.IMAGE_EDIT;
        }
        return ModelVideoSourceType.UPLOAD;
    }

    private void ensureModelVideoEnabled() {
        if (!geminiApiProperties.isModelVideoEnabled()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "穿搭视频功能已关闭");
        }
    }

    private int resolveDurationSeconds(Integer raw) {
        List<Integer> allowed = geminiApiProperties.parsedAllowedDurations();
        if (allowed.isEmpty()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "视频时长未配置");
        }
        if (raw == null) {
            int def = geminiApiProperties.getModelVideoDefaultDurationSeconds();
            if (allowed.contains(def)) {
                return def;
            }
            return allowed.get(0);
        }
        if (!allowed.contains(raw)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不支持的视频时长，请在可选范围内选择");
        }
        return raw;
    }

    private String resolveResolution(String raw) {
        List<String> allowed = geminiApiProperties.parsedAllowedResolutions();
        if (allowed.isEmpty()) {
            return geminiApiProperties.normalizeResolutionToken(null);
        }
        if (!StringUtils.hasText(raw)) {
            String defNorm =
                    geminiApiProperties.normalizeResolutionToken(
                            geminiApiProperties.getModelVideoDefaultResolution());
            if (allowed.contains(defNorm)) {
                return defNorm;
            }
            return allowed.get(0);
        }
        String norm = geminiApiProperties.normalizeResolutionToken(raw);
        if (!allowed.contains(norm)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不支持的分辨率，请在可选范围内选择");
        }
        return norm;
    }

    private String resolveAspectRatio(String raw) {
        List<String> allowed = geminiApiProperties.parsedAllowedAspectRatios();
        if (allowed.isEmpty()) {
            return geminiApiProperties.normalizeAspectRatioToken(null);
        }
        if (!StringUtils.hasText(raw)) {
            String defNorm =
                    geminiApiProperties.normalizeAspectRatioToken(
                            geminiApiProperties.getModelVideoDefaultAspectRatio());
            if (allowed.contains(defNorm)) {
                return defNorm;
            }
            return allowed.get(0);
        }
        String norm = geminiApiProperties.normalizeAspectRatioToken(raw);
        if (!allowed.contains(norm)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不支持的画幅比例，请在可选范围内选择");
        }
        return norm;
    }
}
