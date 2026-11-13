package com.fashtry.backend.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fashtry.backend.bean.db.ProductToModelTask;
import com.fashtry.backend.service.business.ProductToModelService;
import com.fashtry.backend.service.business.WalletDeductionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashtry.backend.bean.dto.fashn.FashnStatusResponse;
import com.fashtry.backend.mapper.ProductToModelTaskMapper;
import com.fashtry.backend.service.ai.ProductToModelClient;
import com.fashtry.backend.service.storage.CosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 定时同步 Product to Model 任务状态
 * <p>
 * 每秒执行一次，查询 status=1（生成中）的任务，
 * 调用 FASHN pollStatus 获取最新状态，将 completed/failed 结果同步到数据库和 Redis
 * </p>
 */
@Component
public class ProductToModelStatusSyncTask {

    private static final Logger log = LoggerFactory.getLogger(ProductToModelStatusSyncTask.class);

    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;
    private static final int BATCH_LIMIT = 20;

    private final ProductToModelTaskMapper taskMapper;
    private final ProductToModelClient productToModelClient;
    private final ProductToModelService productToModelService;
    private final CosService cosService;
    private final WalletDeductionService walletDeductionService;
    private final ObjectMapper objectMapper;

    public ProductToModelStatusSyncTask(ProductToModelTaskMapper taskMapper,
                                        ProductToModelClient productToModelClient,
                                        ProductToModelService productToModelService,
                                        CosService cosService,
                                        WalletDeductionService walletDeductionService,
                                        ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.productToModelClient = productToModelClient;
        this.productToModelService = productToModelService;
        this.cosService = cosService;
        this.walletDeductionService = walletDeductionService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedRate = 1000)
    public void syncStatus() {
        LambdaQueryWrapper<ProductToModelTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductToModelTask::getStatus, STATUS_PROCESSING)
                .isNotNull(ProductToModelTask::getPredictionId)
                .last("LIMIT " + BATCH_LIMIT);

        List<ProductToModelTask> tasks = taskMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(tasks)) {
            return;
        }

        for (ProductToModelTask task : tasks) {
            try {
                FashnStatusResponse status = productToModelClient.pollStatus(task.getPredictionId());

                if ("completed".equals(status.getStatus())) {
                    List<String> outputUrls = status.getOutput();
                    if (outputUrls == null || outputUrls.isEmpty()) {
                        task.setStatus(STATUS_FAILED);
                        task.setErrorMsg("API 返回无结果");
                        task.setCompletedAt(LocalDateTime.now());
                        taskMapper.updateById(task);
                        productToModelService.updateRedis(task.getId(), task);
                        continue;
                    }

                    String firstUrl = outputUrls.get(0);
                    task.setResultUrl(firstUrl);

                    if (outputUrls.size() > 1) {
                        try {
                            task.setResultUrls(objectMapper.writeValueAsString(outputUrls));
                        } catch (JsonProcessingException e) {
                            log.warn("序列化 result_urls 失败: taskId={}", task.getId());
                        }
                    }

                    List<String> cosUrls = new ArrayList<>();
                    String resultCosUrl = null;
                    for (String url : outputUrls) {
                        try {
                            String cosUrl = cosService.uploadProductToModelResultFromUrl(url);
                            cosUrls.add(cosUrl);
                            if (resultCosUrl == null) {
                                resultCosUrl = cosUrl;
                            }
                        } catch (Exception ex) {
                            log.warn("ProductToModel 结果上传 COS 失败: taskId={}, url={}, {}", task.getId(), url, ex.getMessage());
                        }
                    }
                    task.setResultCosUrl(resultCosUrl);
                    if (!cosUrls.isEmpty()) {
                        try {
                            task.setResultCosUrls(objectMapper.writeValueAsString(cosUrls));
                        } catch (JsonProcessingException e) {
                            log.warn("序列化 result_cos_urls 失败: taskId={}", task.getId());
                        }
                    }

                    String srcUrl = resultCosUrl != null ? resultCosUrl : firstUrl;
                    if (srcUrl != null) {
                        try {
                            String thumbUrl = cosService.uploadProductToModelThumbnailFromUrl(srcUrl);
                            task.setThumbUrl(thumbUrl);
                        } catch (Exception ex) {
                            log.warn("ProductToModel 缩略图生成失败: taskId={}, {}", task.getId(), ex.getMessage());
                        }
                    }

                    task.setStatus(STATUS_COMPLETED);
                    task.setErrorMsg(null);
                    task.setCompletedAt(LocalDateTime.now());
                    taskMapper.updateById(task);
                    productToModelService.updateRedis(task.getId(), task);
                    deductCredits(task);
                    log.info("ProductToModel 任务完成: taskId={}, cosUrl={}", task.getId(), resultCosUrl);
                } else if ("failed".equals(status.getStatus())) {
                    task.setStatus(STATUS_FAILED);
                    task.setErrorMsg(status.getError());
                    task.setCompletedAt(LocalDateTime.now());
                    taskMapper.updateById(task);
                    productToModelService.updateRedis(task.getId(), task);
                    log.warn("ProductToModel 任务失败: taskId={}, error={}", task.getId(), status.getError());
                }
            } catch (Exception e) {
                log.error("同步 ProductToModel 任务状态异常: taskId={}, {}", task.getId(), e.getMessage());
            }
        }
    }

    private void deductCredits(ProductToModelTask task) {
        int credits = task.getConsumeCredits() != null ? task.getConsumeCredits() : 1;
        if (credits <= 0) return;
        Long userId = task.getUserId();
        if (userId == null) return;
        walletDeductionService.deductIfEnough(userId, credits, "product_to_model", task.getId());
    }
}
