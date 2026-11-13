package com.fashtry.backend.service.business.impl;

import com.fashtry.backend.bean.db.TryonTask;
import com.fashtry.backend.bean.db.UserWallet;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.UserWalletMapper;
import com.fashtry.backend.service.business.TryonService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fashtry.backend.bean.dto.GeminiTryOnRequest;
import com.fashtry.backend.bean.dto.TryonSubmitBatchRequest;
import com.fashtry.backend.bean.dto.TryonSubmitRequest;
import com.fashtry.backend.common.constants.ClientSafeMessages;
import com.fashtry.backend.common.constants.TryonTaskProvider;
import com.fashtry.backend.bean.vo.TryonStatusVO;
import com.fashtry.backend.bean.vo.TryonSubmitBatchVO;
import com.fashtry.backend.bean.vo.TryonSubmitVO;
import com.fashtry.backend.common.config.TryonProperties;
import com.fashtry.backend.mapper.TryonTaskMapper;
import com.fashtry.backend.security.SecurityUtils;
import com.fashtry.backend.service.ai.GeminiImageService;
import com.fashtry.backend.service.business.WalletDeductionService;
import com.fashtry.backend.service.storage.UserUploadRecordService;
import com.fashtry.backend.utils.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 虚拟试穿服务实现
 * <p>
 * 提交流程：/api/tryon 写入 Gemini 排队任务并写 Redis
 * 查询流程：先查 Redis，没有再查数据库，校验任务归属后返回
 * </p>
 */
@Service
public class TryonServiceImpl implements TryonService {

    private static final Logger log = LoggerFactory.getLogger(TryonServiceImpl.class);

    /** 任务状态常量：0-排队, 1-生成中, 2-完成, 3-失败 */
    private static final int STATUS_QUEUED = 0;
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;

    private final SnowflakeIdGenerator idGenerator;
    private final TryonProperties tryonProperties;
    private final TryonTaskMapper tryonTaskMapper;
    private final UserWalletMapper userWalletMapper;
    private final WalletDeductionService walletDeductionService;
    private final GeminiImageService geminiImageService;
    private final ObjectMapper objectMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final UserUploadRecordService userUploadRecordService;

    public TryonServiceImpl(SnowflakeIdGenerator idGenerator,
                             TryonProperties tryonProperties,
                             TryonTaskMapper tryonTaskMapper,
                             UserWalletMapper userWalletMapper,
                             WalletDeductionService walletDeductionService,
                             GeminiImageService geminiImageService,
                             ObjectMapper objectMapper,
                             org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
                             UserUploadRecordService userUploadRecordService) {
        this.idGenerator = idGenerator;
        this.tryonProperties = tryonProperties;
        this.tryonTaskMapper = tryonTaskMapper;
        this.userWalletMapper = userWalletMapper;
        this.walletDeductionService = walletDeductionService;
        this.geminiImageService = geminiImageService;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.userUploadRecordService = userUploadRecordService;
    }

    @Override
    public TryonSubmitVO submitGeminiTryOn(GeminiTryOnRequest req) {
        String modelUrl = req.getModelUrl() != null ? req.getModelUrl().trim() : "";
        String garmentUrl = req.getGarmentUrl() != null ? req.getGarmentUrl().trim() : "";
        if (!StringUtils.hasText(modelUrl) || !StringUtils.hasText(garmentUrl)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "modelUrl 与 garmentUrl 不能为空");
        }
        Long userId = Optional.ofNullable(SecurityUtils.getCurrentUserId()).orElse(0L);
        Map<String, Object> payload = new HashMap<>();
        payload.put("instruction", req.getInstruction());
        payload.put("modelId", req.getModelId());
        payload.put("aspectRatio", req.getAspectRatio());
        payload.put("imageSize", req.getImageSize());
        int needCredits = 0;
        if (userId != null && userId > 0) {
            String res = normalizeTryonOutputResolution(req.getImageSize());
            payload.put("outputResolution", res);
            payload.put("imageSize", res);
            needCredits = creditsForTryonOutputResolution(res);
            UserWallet wallet = userWalletMapper.selectById(userId);
            int totalCredits = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
            int frozenCredits = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
            if (totalCredits - frozenCredits < needCredits) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }
        return insertQueuedGeminiTryon(
                userId, modelUrl, garmentUrl, categoryForDb(mapCategory(null)), needCredits, payload);
    }

    /**
     * 写入 Gemini 排队任务（{@code tryon_tasks}），并缓存 Redis。
     */
    private TryonSubmitVO insertQueuedGeminiTryon(
            Long userId,
            String modelUrl,
            String garmentUrl,
            String dbCategory,
            int consumeCredits,
            Map<String, Object> geminiPayloadFields) {
        if (!geminiImageService.isReady()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, ClientSafeMessages.AI_IMAGE_NOT_CONFIGURED);
        }
        assertHttpImageUrl(modelUrl);
        assertHttpImageUrl(garmentUrl);
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(geminiPayloadFields != null ? geminiPayloadFields : Map.of());
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "任务参数序列化失败");
        }
        long taskId = idGenerator.nextId();
        int billCredits = Math.max(0, consumeCredits);
        if (userId != null && userId > 0 && billCredits > 0) {
            if (!walletDeductionService.freezeCredits(userId, billCredits, "tryon", taskId)) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }
        TryonTask t = new TryonTask();
        t.setId(taskId);
        t.setUserId(userId);
        t.setPredictionId("gemini-" + taskId);
        t.setProvider(TryonTaskProvider.GEMINI);
        t.setCategory(dbCategory);
        t.setModelUrl(modelUrl);
        t.setGarmentUrl(garmentUrl);
        t.setStatus(STATUS_QUEUED);
        t.setConsumeCredits(billCredits);
        t.setGeminiPayloadJson(payloadJson);
        t.setCreatedAt(LocalDateTime.now());
        try {
            tryonTaskMapper.insert(t);
        } catch (RuntimeException ex) {
            if (userId != null && userId > 0 && billCredits > 0) {
                walletDeductionService.releaseFrozenCredits(userId, billCredits, "tryon", taskId);
            }
            throw ex;
        }
        userUploadRecordService.registerTryonInputs(userId, modelUrl, garmentUrl);
        TryonStatusVO vo = toStatusVO(t);
        saveToRedis(taskId, vo);
        log.info("Gemini 试穿任务已入队 tryon_tasks: taskId={}, userId={}, credits={}", taskId, userId, consumeCredits);
        return TryonSubmitVO.builder().taskId(taskId).build();
    }

    private static void assertHttpImageUrl(String url) {
        String u = url.trim().toLowerCase();
        if (!(u.startsWith("http://") || u.startsWith("https://"))) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "图片地址必须为 http(s) URL");
        }
    }

    @Override
    public TryonSubmitVO submit(TryonSubmitRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        UserWallet wallet = userWalletMapper.selectById(userId);
        int totalCredits = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
        int frozenCredits = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
        int availableCredits = totalCredits - frozenCredits;
        String res = normalizeTryonOutputResolution(request.getOutputResolution());
        int needCredits = creditsForTryonOutputResolution(res);
        if (availableCredits < needCredits) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("outputResolution", res);
        payload.put("imageSize", res);
        if (StringUtils.hasText(request.getInstruction())) {
            String instruction = request.getInstruction().trim();
            if (instruction.length() > 4000) {
                instruction = instruction.substring(0, 4000);
            }
            payload.put("instruction", instruction);
        }
        String dbCategory = categoryForDb(mapCategory(request.getCategory()));
        return insertQueuedGeminiTryon(
                userId,
                request.getModelUrl().trim(),
                request.getGarmentUrl().trim(),
                dbCategory,
                needCredits,
                payload);
    }

    @Override
    public TryonSubmitBatchVO submitBatch(TryonSubmitBatchRequest request) {
        List<String> urls = request.getGarmentUrls();
        if (urls == null || urls.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请至少添加一件服装");
        }
        if (urls.size() > 6) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "单次最多 6 件服装");
        }
        for (String u : urls) {
            if (!StringUtils.hasText(u)) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "服装图片不能为空");
            }
        }
        Long userId = SecurityUtils.getCurrentUserId();
        UserWallet wallet = userWalletMapper.selectById(userId);
        int totalCredits = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
        int frozenCredits = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
        int availableCredits = totalCredits - frozenCredits;
        String res = normalizeTryonOutputResolution(request.getOutputResolution());
        int needPer = creditsForTryonOutputResolution(res);
        int totalNeed = needPer * urls.size();
        if (availableCredits < totalNeed) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
        }

        String dbCategory = categoryForDb(mapCategory(request.getCategory()));
        Map<String, Object> payload = new HashMap<>();
        payload.put("outputResolution", res);
        payload.put("imageSize", res);
        List<String> taskIdStrs = new ArrayList<>();
        String modelUrl = request.getModelUrl().trim();
        for (String garmentUrl : urls) {
            TryonSubmitVO vo =
                    insertQueuedGeminiTryon(
                            userId, modelUrl, garmentUrl.trim(), dbCategory, needPer, payload);
            taskIdStrs.add(String.valueOf(vo.getTaskId()));
        }
        log.info("批量 Gemini 试穿已提交: userId={}, count={}, taskIds={}", userId, taskIdStrs.size(), taskIdStrs);
        return TryonSubmitBatchVO.builder().taskIds(taskIdStrs).build();
    }

    @Override
    public void deductTryonCreditsOnSuccess(TryonTask task) {
        if (task == null || task.getId() == null) {
            return;
        }
        int credits = task.getConsumeCredits() != null ? task.getConsumeCredits() : 0;
        if (credits <= 0) {
            return;
        }
        Long uid = task.getUserId();
        if (uid == null || uid <= 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<TryonTask> mark =
                new LambdaUpdateWrapper<TryonTask>()
                        .eq(TryonTask::getId, task.getId())
                        .eq(TryonTask::getStatus, STATUS_COMPLETED)
                        .isNull(TryonTask::getCreditsDeductedAt)
                        .set(TryonTask::getCreditsDeductedAt, now);
        if (tryonTaskMapper.update(null, mark) != 1) {
            return;
        }
        boolean settled =
                walletDeductionService.settleFrozenCredits(uid, credits, "tryon", task.getId())
                        || walletDeductionService.deductIfEnough(uid, credits, "tryon", task.getId());
        if (!settled) {
            LambdaUpdateWrapper<TryonTask> rollback =
                    new LambdaUpdateWrapper<TryonTask>()
                            .eq(TryonTask::getId, task.getId())
                            .eq(TryonTask::getStatus, STATUS_COMPLETED)
                            .set(TryonTask::getCreditsDeductedAt, null);
            tryonTaskMapper.update(null, rollback);
        }
    }

    @Override
    public void releaseTryonCreditsOnFailure(TryonTask task) {
        if (task == null || task.getId() == null) {
            return;
        }
        int credits = task.getConsumeCredits() != null ? task.getConsumeCredits() : 0;
        if (credits <= 0) {
            return;
        }
        Long uid = task.getUserId();
        if (uid == null || uid <= 0) {
            return;
        }
        if (task.getCreditsDeductedAt() != null) {
            return;
        }
        walletDeductionService.releaseFrozenCredits(uid, credits, "tryon", task.getId());
    }

    @Override
    public TryonStatusVO getStatus(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务 ID 不能为空");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        // 以数据库为准：Redis 可能比调度回写滞后，导致详情与列表 status/progress 不一致
        TryonTask task = tryonTaskMapper.selectById(taskId);
        if (task != null) {
            if (!canViewTryonTask(task.getUserId(), task.getProvider(), currentUserId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权限查看该任务");
            }
            TryonStatusVO vo = toStatusVO(task);
            saveToRedis(taskId, vo);
            attachProgress(vo);
            return vo;
        }

        TryonStatusVO fromRedis = getFromRedis(taskId);
        if (fromRedis != null && fromRedis.getUserId() == null) {
            fromRedis = null;
        }
        if (fromRedis != null) {
            if (!canViewTryonTask(fromRedis.getUserId(), fromRedis.getProvider(), currentUserId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权限查看该任务");
            }
            applyClientSafeTryonFields(fromRedis);
            attachProgress(fromRedis);
            return fromRedis;
        }

        throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
    }

    @Override
    public List<TryonStatusVO> getStatuses(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "taskIds 不能为空");
        }
        if (taskIds.size() > 20) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "单次最多查询 20 个任务");
        }
        for (Long id : taskIds) {
            if (id == null) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "任务 ID 无效");
            }
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<Long> distinctIds = taskIds.stream().distinct().collect(Collectors.toList());
        LambdaQueryWrapper<TryonTask> w = new LambdaQueryWrapper<>();
        w.in(TryonTask::getId, distinctIds).eq(TryonTask::getUserId, currentUserId);
        List<TryonTask> tasks = tryonTaskMapper.selectList(w);
        Map<Long, TryonTask> byId =
                tasks.stream().collect(Collectors.toMap(TryonTask::getId, t -> t, (a, b) -> a));
        TryonStatusVO[] slots = new TryonStatusVO[taskIds.size()];
        for (int i = 0; i < taskIds.size(); i++) {
            Long tid = taskIds.get(i);
            TryonTask t = byId.get(tid);
            if (t == null) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
            }
            TryonStatusVO vo = toStatusVO(t);
            saveToRedis(tid, vo);
            attachProgress(vo);
            slots[i] = vo;
        }
        return Arrays.asList(slots);
    }

    @Override
    public PageResult<TryonStatusVO> listMyTasks(int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 50);
        Long userId = SecurityUtils.getCurrentUserId();
        LambdaQueryWrapper<TryonTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TryonTask::getUserId, userId)
                .orderByDesc(TryonTask::getCreatedAt);
        // 列表仅需必要字段，避免 select * 拉取大字段
        wrapper.select(
                TryonTask::getId,
                TryonTask::getUserId,
                TryonTask::getProvider,
                TryonTask::getStatus,
                TryonTask::getModelUrl,
                TryonTask::getGarmentUrl,
                TryonTask::getResultUrl,
                TryonTask::getResultCosUrl,
                TryonTask::getThumbUrl,
                TryonTask::getErrorMsg,
                TryonTask::getConsumeCredits,
                TryonTask::getProcessingStartedAt,
                TryonTask::getGenerationDurationMs,
                TryonTask::getCreatedAt
        );
        IPage<TryonTask> pageResult = tryonTaskMapper.selectPage(new Page<>(p, s), wrapper);
        List<TryonStatusVO> list = pageResult.getRecords().stream()
                .map(task -> {
                    TryonStatusVO vo = toStatusVO(task);
                    attachProgress(vo);
                    return vo;
                })
                .collect(Collectors.toList());
        return PageResult.of(list, pageResult.getTotal(), p, s);
    }

    /**
     * 将任务状态写入 Redis 缓存
     *
     * @param taskId 任务 ID
     * @param vo     状态 VO
     */
    public void saveToRedis(Long taskId, TryonStatusVO vo) {
        String key = tryonProperties.getRedisKeyPrefix() + taskId;
        try {
            // userId 带 @JsonIgnore，直接 writeValueAsString 不会写入 Redis，getStatus 读回后无法做本人校验
            ObjectNode tree = objectMapper.valueToTree(vo);
            if (vo.getUserId() != null) {
                tree.put("userId", vo.getUserId());
            } else {
                tree.putNull("userId");
            }
            String json = objectMapper.writeValueAsString(tree);
            redisTemplate.opsForValue().set(key, json, java.time.Duration.ofSeconds(tryonProperties.getRedisTtlSeconds()));
        } catch (JsonProcessingException e) {
            log.warn("Redis 写入失败: taskId={}, {}", taskId, e.getMessage());
        }
    }

    /**
     * 从 Redis 缓存读取任务状态
     *
     * @param taskId 任务 ID
     * @return 状态 VO，不存在或解析失败返回 null
     */
    public TryonStatusVO getFromRedis(Long taskId) {
        String key = tryonProperties.getRedisKeyPrefix() + taskId;
        String json = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            TryonStatusVO vo = objectMapper.treeToValue(root, TryonStatusVO.class);
            JsonNode uid = root.get("userId");
            if (uid != null && !uid.isNull()) {
                vo.setUserId(uid.asLong());
            }
            return vo;
        } catch (JsonProcessingException e) {
            log.warn("Redis 解析失败: taskId={}, {}", taskId, e.getMessage());
            return null;
        }
    }

    /**
     * 根据数据库任务更新 Redis 缓存（供定时任务调用）
     *
     * @param taskId 任务 ID
     * @param task   数据库任务实体
     */
    @Override
    public void updateRedis(Long taskId, TryonTask task) {
        saveToRedis(taskId, toStatusVO(task));
    }

    /**
     * 将数据库任务实体转换为状态 VO
     */
    private TryonStatusVO toStatusVO(TryonTask task) {
        // 试穿结果图对外只暴露腾讯云 COS（result_cos_url）；FASHN 原始 CDN 仅存库不返回
        String resultUrl = StringUtils.hasText(task.getResultCosUrl()) ? task.getResultCosUrl() : null;
        long createdMs = task.getCreatedAt() != null
                ? task.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : System.currentTimeMillis();
        Integer st = task.getStatus();
        Long procStart = null;
        if (task.getProcessingStartedAt() != null) {
            procStart = task.getProcessingStartedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } else if (st != null && st == STATUS_PROCESSING) {
            procStart = createdMs;
        }
        String prov =
                StringUtils.hasText(task.getProvider()) ? task.getProvider() : TryonTaskProvider.FASHN;
        String safeError =
                st != null && st == STATUS_FAILED ? ClientSafeMessages.TRYON_GENERATION_FAILED : null;
        return TryonStatusVO.builder()
                .taskId(task.getId())
                .userId(task.getUserId())
                .status(task.getStatus())
                .statusDesc(TryonStatusVO.statusDesc(Optional.ofNullable(task.getStatus()).orElse(0)))
                .modelUrl(task.getModelUrl())
                .garmentUrl(task.getGarmentUrl())
                .resultUrl(resultUrl)
                .thumbUrl(task.getThumbUrl())
                .errorMsg(safeError)
                .consumeCredits(task.getConsumeCredits())
                .submittedAt(createdMs)
                .processingStartedAt(procStart)
                .generationDurationMs(task.getGenerationDurationMs())
                .provider(ClientSafeMessages.mapTryonProviderForApi(prov))
                .build();
    }

    /** 兼容旧 Redis 缓存中可能存在的上游原始错误文案 */
    private static void applyClientSafeTryonFields(TryonStatusVO vo) {
        if (vo == null) {
            return;
        }
        vo.setProvider(ClientSafeMessages.mapTryonProviderForApi(vo.getProvider()));
        if (vo.getStatus() != null && vo.getStatus() == STATUS_FAILED) {
            vo.setErrorMsg(ClientSafeMessages.TRYON_GENERATION_FAILED);
        }
    }

    /**
     * 已登录用户仅可看本人任务；公开 Gemini 任务（userId=0 + provider=GEMINI）允许未登录轮询。
     */
    private static boolean canViewTryonTask(Long taskUserId, String provider, Long currentUserId) {
        String p = StringUtils.hasText(provider) ? provider : TryonTaskProvider.FASHN;
        if (TryonTaskProvider.GEMINI.equals(p) && taskUserId != null && taskUserId.longValue() == 0L) {
            return true;
        }
        return taskUserId != null && currentUserId != null && taskUserId.equals(currentUserId);
    }

    /**
     * 根据排队/生成耗时估算进度（时长由 tryon.tryon-progress-estimate-seconds 配置，随分辨率可调）
     */
    private void attachProgress(TryonStatusVO vo) {
        if (vo == null || vo.getStatus() == null) {
            return;
        }
        int s = vo.getStatus();
        if (s == STATUS_COMPLETED) {
            vo.setProgress(100);
            return;
        }
        if (s == STATUS_FAILED) {
            vo.setProgress(null);
            return;
        }
        long now = System.currentTimeMillis();
        int estSec = Math.max(30, tryonProperties.getTryonProgressEstimateSeconds());
        long estMs = estSec * 1000L;
        if (s == STATUS_QUEUED) {
            long sub = vo.getSubmittedAt() != null ? vo.getSubmittedAt() : now;
            long elapsed = Math.max(0, now - sub);
            vo.setProgress((int) Math.min(20, elapsed * 20 / 45000));
            return;
        }
        if (s == STATUS_PROCESSING) {
            long start = vo.getProcessingStartedAt() != null ? vo.getProcessingStartedAt()
                    : (vo.getSubmittedAt() != null ? vo.getSubmittedAt() : now);
            long elapsed = Math.max(0, now - start);
            int base = 20;
            int span = 79;
            int p = base + (int) Math.min(span, elapsed * span / estMs);
            vo.setProgress(Math.min(99, Math.max(base, p)));
        }
    }

    /** 1K=1 点，2K=2 点，4K=4 点 */
    private static int creditsForTryonOutputResolution(String res) {
        if ("2K".equals(res)) {
            return 2;
        }
        if ("4K".equals(res)) {
            return 4;
        }
        return 1;
    }

    /** 仅允许 1K | 2K | 4K，默认 1K */
    private static String normalizeTryonOutputResolution(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "1K";
        }
        String u = raw.trim().toUpperCase();
        if ("1K".equals(u) || "2K".equals(u) || "4K".equals(u)) {
            return u;
        }
        throw new BusinessException(ResultCode.PARAM_INVALID, "outputResolution 仅支持 1K、2K、4K");
    }

    /**
     * 将前端 category 映射为 FASHN API 支持的值：auto | tops | bottoms | one-pieces
     */
    private String mapCategory(String category) {
        if (!StringUtils.hasText(category) || "auto".equalsIgnoreCase(category)) {
            return "auto";
        }
        String lower = category.toLowerCase();
        if ("tops".equals(lower) || "bottoms".equals(lower) || "one-pieces".equals(lower)) {
            return lower;
        }
        return "auto";
    }

    /**
     * 将 category 映射为 DB / Gemini 侧使用的值。
     * <p>「自动」对电商平铺/套装图更常见：默认按 {@code one-pieces}（整身/多件套）处理，避免误当成仅上装而把裙装理解成裤装。
     * 若用户只换上装，请在前端显式选「上装」。</p>
     */
    private String categoryForDb(String category) {
        if (!StringUtils.hasText(category) || "auto".equalsIgnoreCase(category)) {
            return "one-pieces";
        }
        String lower = category.toLowerCase();
        return ("tops".equals(lower) || "bottoms".equals(lower) || "one-pieces".equals(lower)) ? lower : "one-pieces";
    }
}
