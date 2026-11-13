package com.fashtry.backend.service.business.impl;

import com.fashtry.backend.bean.db.ProductToModelTask;
import com.fashtry.backend.bean.db.UserWallet;
import com.fashtry.backend.bean.dto.ProductToModelSubmitRequest;
import com.fashtry.backend.bean.dto.ProductToModelTaskMessage;
import com.fashtry.backend.bean.dto.fashn.ProductToModelRequest;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.ProductToModelStatusVO;
import com.fashtry.backend.bean.vo.ProductToModelSubmitVO;
import com.fashtry.backend.common.config.ProductToModelProperties;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.service.business.ProductToModelService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashtry.backend.bean.dto.fashn.ProductToModelInputs;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.ProductToModelTaskMapper;
import com.fashtry.backend.mapper.UserWalletMapper;
import com.fashtry.backend.security.SecurityUtils;
import com.fashtry.backend.service.ai.ProductToModelClient;
import com.fashtry.backend.service.storage.UserUploadRecordService;
import com.fashtry.backend.utils.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductToModelServiceImpl implements ProductToModelService {

    private static final Logger log = LoggerFactory.getLogger(ProductToModelServiceImpl.class);

    private static final int STATUS_QUEUED = 0;
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;

    /** 使用 face_reference 时消耗 4 积分 */
    private static final int CREDITS_WITH_FACE_REF = 4;
    private static final int CREDITS_DEFAULT = 1;

    private final SnowflakeIdGenerator idGenerator;
    private final ProductToModelProperties properties;
    private final ProductToModelTaskMapper taskMapper;
    private final UserWalletMapper userWalletMapper;
    private final ProductToModelClient productToModelClient;
    private final ObjectMapper objectMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final UserUploadRecordService userUploadRecordService;

    public ProductToModelServiceImpl(SnowflakeIdGenerator idGenerator,
                                       ProductToModelProperties properties,
                                       ProductToModelTaskMapper taskMapper,
                                       UserWalletMapper userWalletMapper,
                                       ProductToModelClient productToModelClient,
                                       ObjectMapper objectMapper,
                                       org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
                                       UserUploadRecordService userUploadRecordService) {
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.taskMapper = taskMapper;
        this.userWalletMapper = userWalletMapper;
        this.productToModelClient = productToModelClient;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.userUploadRecordService = userUploadRecordService;
    }

    @Override
    public void processTaskMessage(ProductToModelTaskMessage message) {
        Long taskId = message.getTaskId();
        int numImages = message.getNumImages() != null && message.getNumImages() >= 1 ? message.getNumImages() : 1;
        int perImage = StringUtils.hasText(message.getFaceReferenceUrl()) ? CREDITS_WITH_FACE_REF : CREDITS_DEFAULT;
        int consumeCredits = perImage * numImages;

        try {
            ProductToModelRequest request = buildRequest(message);
            String predictionId = productToModelClient.submit(request);

            ProductToModelTask task = new ProductToModelTask();
            task.setId(taskId);
            task.setUserId(message.getUserId());
            task.setPredictionId(predictionId);
            task.setProductUrl(message.getProductUrl());
            task.setModelUrl(message.getModelUrl());
            task.setImagePromptUrl(message.getImagePromptUrl());
            task.setFaceReferenceUrl(message.getFaceReferenceUrl());
            task.setBackgroundReferenceUrl(message.getBackgroundReferenceUrl());
            task.setFaceReferenceMode(message.getFaceReferenceMode());
            task.setPrompt(message.getPrompt());
            task.setAspectRatio(message.getAspectRatio());
            task.setResolution(message.getResolution());
            task.setSeed(message.getSeed());
            task.setNumImages(message.getNumImages());
            task.setOutputFormat(message.getOutputFormat());
            task.setStatus(STATUS_PROCESSING);
            task.setConsumeCredits(consumeCredits);
            task.setCreatedAt(LocalDateTime.now());

            taskMapper.insert(task);

            saveToRedis(taskId, toStatusVO(task));
            log.info("ProductToModel 任务已入库: taskId={}, predictionId={}", taskId, predictionId);
        } catch (Exception e) {
            log.error("ProductToModel 任务处理失败: taskId={}, {}", taskId, e.getMessage(), e);
            ProductToModelTask task = new ProductToModelTask();
            task.setId(taskId);
            task.setUserId(message.getUserId());
            task.setProductUrl(message.getProductUrl());
            task.setModelUrl(message.getModelUrl());
            task.setStatus(STATUS_FAILED);
            task.setErrorMsg(e.getMessage());
            task.setConsumeCredits(0);
            task.setCreatedAt(LocalDateTime.now());
            try {
                taskMapper.insert(task);
                saveToRedis(taskId, ProductToModelStatusVO.builder()
                        .taskId(taskId)
                        .userId(message.getUserId())
                        .status(STATUS_FAILED)
                        .statusDesc(ProductToModelStatusVO.statusDesc(STATUS_FAILED))
                        .errorMsg(e.getMessage())
                        .consumeCredits(0)
                        .build());
            } catch (Exception ex) {
                log.error("失败任务入库异常: taskId={}", taskId, ex);
            }
        }
    }

    @Override
    public ProductToModelSubmitVO submit(ProductToModelSubmitRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        int numImages = request.getNumImages() != null && request.getNumImages() >= 1 ? request.getNumImages() : 1;
        int perImage = StringUtils.hasText(request.getFaceReference()) ? CREDITS_WITH_FACE_REF : CREDITS_DEFAULT;
        int consumeCredits = perImage * numImages;
        UserWallet wallet = userWalletMapper.selectById(userId);
        int totalCredits = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
        int frozenCredits = wallet != null && wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
        int availableCredits = totalCredits - frozenCredits;
        if (availableCredits < consumeCredits) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
        }
        long taskId = idGenerator.nextId();

        ProductToModelTaskMessage message = ProductToModelTaskMessage.builder()
                .taskId(taskId)
                .userId(userId)
                .productUrl(request.getProductImage())
                .modelUrl(StringUtils.hasText(request.getModelImage()) ? request.getModelImage() : null)
                .imagePromptUrl(StringUtils.hasText(request.getImagePrompt()) ? request.getImagePrompt() : null)
                .faceReferenceUrl(StringUtils.hasText(request.getFaceReference()) ? request.getFaceReference() : null)
                .backgroundReferenceUrl(StringUtils.hasText(request.getBackgroundReference()) ? request.getBackgroundReference() : null)
                .faceReferenceMode(StringUtils.hasText(request.getFaceReferenceMode()) ? request.getFaceReferenceMode() : null)
                .prompt(StringUtils.hasText(request.getPrompt()) ? request.getPrompt() : null)
                .aspectRatio(StringUtils.hasText(request.getAspectRatio()) ? request.getAspectRatio() : null)
                .resolution(StringUtils.hasText(request.getResolution()) ? request.getResolution() : null)
                .seed(request.getSeed())
                .numImages(request.getNumImages() != null ? request.getNumImages() : 1)
                .outputFormat(StringUtils.hasText(request.getOutputFormat()) ? request.getOutputFormat() : "png")
                .build();

        processTaskMessage(message);
        userUploadRecordService.registerProductToModelInputs(
                userId,
                message.getProductUrl(),
                message.getModelUrl(),
                message.getImagePromptUrl(),
                message.getFaceReferenceUrl(),
                message.getBackgroundReferenceUrl());

        log.info("ProductToModel 任务已提交: taskId={}, userId={}", taskId, userId);
        return ProductToModelSubmitVO.builder().taskId(taskId).build();
    }

    @Override
    public ProductToModelStatusVO getStatus(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务 ID 不能为空");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        ProductToModelStatusVO fromRedis = getFromRedis(taskId);
        if (fromRedis != null) {
            if (fromRedis.getUserId() != null && !fromRedis.getUserId().equals(currentUserId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权限查看该任务");
            }
            return fromRedis;
        }

        ProductToModelTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        if (!task.getUserId().equals(currentUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限查看该任务");
        }

        ProductToModelStatusVO vo = toStatusVO(task);
        saveToRedis(taskId, vo);
        return vo;
    }

    @Override
    public PageResult<ProductToModelStatusVO> listMyTasks(int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 50);
        Long userId = SecurityUtils.getCurrentUserId();
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductToModelTask>();
        wrapper.eq(ProductToModelTask::getUserId, userId)
                .orderByDesc(ProductToModelTask::getCreatedAt);
        var pageResult = taskMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(p, s), wrapper);
        List<ProductToModelStatusVO> list = pageResult.getRecords().stream()
                .map(this::toStatusVO)
                .collect(Collectors.toList());
        return PageResult.of(list, pageResult.getTotal(), p, s);
    }

    @Override
    public void updateRedis(Long taskId, ProductToModelTask task) {
        saveToRedis(taskId, toStatusVO(task));
    }

    private ProductToModelRequest buildRequest(ProductToModelTaskMessage message) {
        ProductToModelInputs inputs = ProductToModelInputs.builder()
                .productImage(message.getProductUrl())
                .modelImage(message.getModelUrl())
                .imagePrompt(message.getImagePromptUrl())
                .faceReference(message.getFaceReferenceUrl())
                .backgroundReference(message.getBackgroundReferenceUrl())
                .faceReferenceMode(message.getFaceReferenceMode())
                .prompt(message.getPrompt())
                .aspectRatio(message.getAspectRatio())
                .resolution(message.getResolution())
                .seed(message.getSeed())
                .numImages(message.getNumImages())
                .outputFormat(message.getOutputFormat())
                .build();

        return ProductToModelRequest.builder()
                .modelName("product-to-model")
                .inputs(inputs)
                .build();
    }

    private void saveToRedis(Long taskId, ProductToModelStatusVO vo) {
        String key = properties.getRedisKeyPrefix() + taskId;
        try {
            String json = objectMapper.writeValueAsString(vo);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(properties.getRedisTtlSeconds()));
        } catch (JsonProcessingException e) {
            log.warn("Redis 写入失败: taskId={}, {}", taskId, e.getMessage());
        }
    }

    private ProductToModelStatusVO getFromRedis(Long taskId) {
        String key = properties.getRedisKeyPrefix() + taskId;
        String json = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ProductToModelStatusVO.class);
        } catch (JsonProcessingException e) {
            log.warn("Redis 解析失败: taskId={}, {}", taskId, e.getMessage());
            return null;
        }
    }

    private ProductToModelStatusVO toStatusVO(ProductToModelTask task) {
        String resultUrl = StringUtils.hasText(task.getResultCosUrl())
                ? task.getResultCosUrl()
                : task.getResultUrl();

        List<String> resultUrls = null;
        if (StringUtils.hasText(task.getResultCosUrls())) {
            try {
                resultUrls = objectMapper.readValue(task.getResultCosUrls(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                if (StringUtils.hasText(resultUrl)) {
                    resultUrls = Collections.singletonList(resultUrl);
                }
            }
        } else if (StringUtils.hasText(task.getResultUrls())) {
            try {
                resultUrls = objectMapper.readValue(task.getResultUrls(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                if (StringUtils.hasText(resultUrl)) {
                    resultUrls = Collections.singletonList(resultUrl);
                }
            }
        } else if (StringUtils.hasText(resultUrl)) {
            resultUrls = Collections.singletonList(resultUrl);
        }

        return ProductToModelStatusVO.builder()
                .taskId(task.getId())
                .userId(task.getUserId())
                .status(task.getStatus())
                .statusDesc(ProductToModelStatusVO.statusDesc(Optional.ofNullable(task.getStatus()).orElse(0)))
                .productUrl(task.getProductUrl())
                .modelUrl(task.getModelUrl())
                .resultUrl(resultUrl)
                .resultUrls(resultUrls)
                .thumbUrl(task.getThumbUrl())
                .errorMsg(task.getErrorMsg())
                .consumeCredits(task.getConsumeCredits())
                .build();
    }
}
