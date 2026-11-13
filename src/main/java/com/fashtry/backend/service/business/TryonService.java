package com.fashtry.backend.service.business;

import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.dto.GeminiTryOnRequest;
import com.fashtry.backend.bean.dto.TryonSubmitBatchRequest;
import com.fashtry.backend.bean.dto.TryonSubmitRequest;
import com.fashtry.backend.bean.db.TryonTask;
import com.fashtry.backend.bean.vo.TryonStatusVO;
import com.fashtry.backend.bean.vo.TryonSubmitBatchVO;
import com.fashtry.backend.bean.vo.TryonSubmitVO;

import java.util.List;

/**
 * 虚拟试穿服务
 * <p>
 * 负责试穿任务的提交与状态查询；{@code /api/tryon} 提交走 Gemini，由定时任务生成并扣点。
 * </p>
 */
public interface TryonService {

    /**
     * 提交试穿任务：写入 {@code tryon_tasks}（Gemini），校验点数，成功后由定时任务生成并扣点。
     */
    TryonSubmitVO submit(TryonSubmitRequest request);

    /**
     * Gemini 虚拟试穿：写入 {@code tryon_tasks}（provider=GEMINI），由定时任务拉取执行；返回与 FASHN 一致的 taskId。
     */
    TryonSubmitVO submitGeminiTryOn(GeminiTryOnRequest request);

    /**
     * 批量提交试穿：同一模特、多件服装，每件生成独立任务；一次性校验总点数后依次入队
     *
     * @param request modelUrl + garmentUrls（1～6）+ 共用 category / outputResolution
     * @return 与 garmentUrls 顺序一致的 taskId 列表
     */
    TryonSubmitBatchVO submitBatch(TryonSubmitBatchRequest request);

    /**
     * 查询任务状态：先查 Redis，没有再查数据库
     *
     * @param taskId 任务 ID
     * @return 任务状态
     */
    TryonStatusVO getStatus(Long taskId);

    /**
     * 批量查询任务状态：顺序与 taskIds 一致；缺失时合并查库，避免前端逐条轮询
     *
     * @param taskIds 任务 ID 列表（非空，建议与批量提交返回顺序一致）
     * @return 与 taskIds 一一对应的状态列表
     */
    List<TryonStatusVO> getStatuses(List<Long> taskIds);

    /**
     * 分页查询我的任务列表，按创建时间倒序
     *
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 分页结果
     */
    PageResult<TryonStatusVO> listMyTasks(int page, int size);

    void updateRedis(Long taskId, TryonTask task);

    /** 任务标记完成并落库后，按 {@link TryonTask#getConsumeCredits()} 扣点 */
    void deductTryonCreditsOnSuccess(TryonTask task);

    /** 任务失败时释放提交阶段预占的点数 */
    void releaseTryonCreditsOnFailure(TryonTask task);
}
