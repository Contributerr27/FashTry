package com.fashtry.backend.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fashtry.backend.bean.db.TryonTask;
import com.fashtry.backend.bean.dto.fashn.FashnStatusResponse;
import com.fashtry.backend.service.business.TryonService;
import com.fashtry.backend.common.constants.TryonTaskProvider;
import com.fashtry.backend.mapper.TryonTaskMapper;
import com.fashtry.backend.service.ai.FashnAiClient;
import com.fashtry.backend.service.storage.CosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时同步试穿任务状态
 * <p>
 * 每秒执行一次，查询 status=1（生成中）的任务，
 * 调用 FASHN pollStatus 获取最新状态，将 completed/failed 结果同步到数据库和 Redis。
 * 若 tryon_tasks 中已有相同模特图、服装图且已完成的任务，可把其结果链接写入当前任务，避免重复拉取与转存，节省成本。
 * </p>
 */
@Component
public class TryonStatusSyncTask {

    private static final Logger log = LoggerFactory.getLogger(TryonStatusSyncTask.class);

    /** 任务状态：1-生成中, 2-完成, 3-失败 */
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;

    private static final int BATCH_LIMIT = 20;

    private final TryonTaskMapper tryonTaskMapper;
    private final FashnAiClient fashnAiClient;
    private final TryonService tryonService;
    private final CosService cosService;

    public TryonStatusSyncTask(TryonTaskMapper tryonTaskMapper,
                               FashnAiClient fashnAiClient,
                               TryonService tryonService,
                               CosService cosService) {
        this.tryonTaskMapper = tryonTaskMapper;
        this.fashnAiClient = fashnAiClient;
        this.tryonService = tryonService;
        this.cosService = cosService;
    }

    /**
     * 同步生成中任务的状态
     * 固定频率 1 秒执行一次
     */
    @Scheduled(fixedRate = 1000)
    public void syncStatus() {
        LambdaQueryWrapper<TryonTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TryonTask::getStatus, STATUS_PROCESSING)
                .isNotNull(TryonTask::getPredictionId)
                .and(w -> w.isNull(TryonTask::getProvider)
                        .or()
                        .ne(TryonTask::getProvider, TryonTaskProvider.GEMINI))
                .last("LIMIT " + BATCH_LIMIT);

        List<TryonTask> tasks = tryonTaskMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(tasks)) {
            return;
        }

        for (TryonTask task : tasks) {
            try {
                TryonTask reuse = findReusableCompletedTask(task);
                if (reuse != null) {
                    applyReusedResult(task, reuse);
                    tryonTaskMapper.updateById(task);
                    tryonService.updateRedis(task.getId(), task);
                    tryonService.deductTryonCreditsOnSuccess(task);
                    log.info("试穿任务复用同素材已完成结果: taskId={}, reuseFromTaskId={}, resultCosUrl={}",
                            task.getId(), reuse.getId(), task.getResultCosUrl());
                    continue;
                }

                FashnStatusResponse status = fashnAiClient.pollStatus(task.getPredictionId());

                if ("completed".equals(status.getStatus())) {
                    String fashnResultUrl = status.getOutput() != null && !status.getOutput().isEmpty()
                            ? status.getOutput().get(0)
                            : null;
                    if (!StringUtils.hasText(fashnResultUrl)) {
                        task.setStatus(STATUS_FAILED);
                        task.setErrorMsg("生成结果为空");
                        task.setCompletedAt(LocalDateTime.now());
                        tryonTaskMapper.updateById(task);
                        tryonService.updateRedis(task.getId(), task);
                        log.warn("试穿任务失败: taskId={}, error=输出为空", task.getId());
                        continue;
                    }
                    task.setResultUrl(fashnResultUrl);
                    try {
                        String resultCosUrl = cosService.uploadTryonResultFromUrl(fashnResultUrl);
                        task.setResultCosUrl(resultCosUrl);
                        task.setStatus(STATUS_COMPLETED);
                        task.setErrorMsg(null);
                        task.setCompletedAt(LocalDateTime.now());
                        String thumbUrl = null;
                        try {
                            thumbUrl = cosService.uploadTryonThumbnailFromUrl(resultCosUrl);
                            task.setThumbUrl(thumbUrl);
                        } catch (Exception ex) {
                            log.warn("试穿缩略图生成失败: taskId={}, {}", task.getId(), ex.getMessage());
                        }
                        tryonTaskMapper.updateById(task);
                        tryonService.updateRedis(task.getId(), task);
                        tryonService.deductTryonCreditsOnSuccess(task);
                        log.info("试穿任务完成: taskId={}, cosUrl={}, thumbUrl={}", task.getId(), resultCosUrl, thumbUrl);
                    } catch (Exception ex) {
                        log.warn("试穿结果上传 COS 失败: taskId={}, {}", task.getId(), ex.getMessage());
                        task.setStatus(STATUS_PROCESSING);
                        task.setResultCosUrl(null);
                        task.setThumbUrl(null);
                        tryonTaskMapper.updateById(task);
                        tryonService.updateRedis(task.getId(), task);
                    }
                } else if ("failed".equals(status.getStatus())) {
                    task.setStatus(STATUS_FAILED);
                    task.setErrorMsg(status.getError());
                    task.setCompletedAt(LocalDateTime.now());
                    tryonTaskMapper.updateById(task);
                    tryonService.updateRedis(task.getId(), task);
                    log.warn("试穿任务失败: taskId={}, error={}", task.getId(), status.getError());
                }
            } catch (Exception e) {
                log.error("同步任务状态异常: taskId={}, {}", task.getId(), e.getMessage());
            }
        }
    }

    /** 在 tryon_tasks 中查找：同模特链接、同服装链接、已完成且已有结果的另一条任务（不含当前任务）。 */
    private TryonTask findReusableCompletedTask(TryonTask task) {
        String modelUrl = task.getModelUrl();
        String garmentUrl = task.getGarmentUrl();
        if (!StringUtils.hasText(modelUrl) || !StringUtils.hasText(garmentUrl)) {
            return null;
        }
        LambdaQueryWrapper<TryonTask> w = new LambdaQueryWrapper<>();
        w.eq(TryonTask::getStatus, STATUS_COMPLETED)
                .eq(TryonTask::getModelUrl, modelUrl)
                .eq(TryonTask::getGarmentUrl, garmentUrl)
                .ne(TryonTask::getId, task.getId())
                .isNotNull(TryonTask::getResultCosUrl)
                .orderByDesc(TryonTask::getCompletedAt)
                .last("LIMIT 1");
        return tryonTaskMapper.selectOne(w);
    }

    private static void applyReusedResult(TryonTask task, TryonTask source) {
        task.setResultUrl(source.getResultUrl());
        task.setResultCosUrl(source.getResultCosUrl());
        task.setThumbUrl(source.getThumbUrl());
        task.setStatus(STATUS_COMPLETED);
        task.setErrorMsg(null);
        task.setCompletedAt(LocalDateTime.now());
    }
}
