package com.fashtry.backend.controller.business;

import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.bean.vo.TryonStatusVO;
import com.fashtry.backend.bean.vo.TryonSubmitBatchVO;
import com.fashtry.backend.bean.vo.TryonSubmitVO;
import com.fashtry.backend.service.business.TryonService;
import com.fashtry.backend.bean.dto.TryonBatchStatusRequest;
import com.fashtry.backend.bean.dto.TryonSubmitBatchRequest;
import com.fashtry.backend.bean.dto.TryonSubmitRequest;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 虚拟试穿接口
 * <p>
 * 提供试穿任务提交与状态轮询，需 JWT 认证。提交后写入 {@code tryon_tasks}（provider=GEMINI），由调度器异步调用 Gemini 完成。
 * </p>
 */
@RestController
@RequestMapping("/api/tryon")
public class TryonController {

    private final TryonService tryonService;

    public TryonController(TryonService tryonService) {
        this.tryonService = tryonService;
    }

    /**
     * 提交试穿任务
     * <p>
     * 流程：生成任务 ID → 落库（排队）并写入 Redis（status=0）→ 立即返回 taskId，由 Gemini 调度消费
     * 前端使用 taskId 轮询 getStatus 获取生成进度
     * </p>
     *
     * @param request 包含 modelUrl、garmentUrl、category、outputResolution（1K|2K|4K，默认 1K；扣点 1/2/4）
     * @return 任务 ID
     */
    @PostMapping("/submit")
    public Result<TryonSubmitVO> submit(@Valid @RequestBody TryonSubmitRequest request) {
        TryonSubmitVO vo = tryonService.submit(request);
        return Result.success(vo);
    }

    /**
     * 批量提交试穿任务
     * <p>
     * 同一模特、多件服装（1～6），每件生成独立 taskId，顺序与 garmentUrls 一致；
     * 提交前按「件数 × 单件扣点」校验余额（单件扣点由 outputResolution 决定）。前端可继续用 GET /api/tryon/task 轮询各 taskId
     * </p>
     */
    @PostMapping("/submit-batch")
    public Result<TryonSubmitBatchVO> submitBatch(@Valid @RequestBody TryonSubmitBatchRequest request) {
        TryonSubmitBatchVO vo = tryonService.submitBatch(request);
        return Result.success(vo);
    }

    /**
     * 查询任务状态
     * <p>
     * 流程：先查 Redis，没有再查数据库。仅允许查询本人任务
     * </p>
     *
     * @param taskId 任务 ID
     * @return 任务状态（含 resultUrl/errorMsg）
     */
    @GetMapping("/task")
    public Result<TryonStatusVO> getStatus(@RequestParam Long taskId) {
        TryonStatusVO vo = tryonService.getStatus(taskId);
        return Result.success(vo);
    }

    /**
     * 批量查询任务状态（顺序与 taskIds 一致），供批量试衣一次轮询全部任务
     */
    @PostMapping("/tasks-status")
    public Result<List<TryonStatusVO>> getStatuses(@Valid @RequestBody TryonBatchStatusRequest request) {
        List<Long> ids = new ArrayList<>(request.getTaskIds().size());
        for (String s : request.getTaskIds()) {
            try {
                ids.add(Long.parseLong(s.trim()));
            } catch (NumberFormatException e) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "任务 ID 无效");
            }
        }
        return Result.success(tryonService.getStatuses(ids));
    }

    /**
     * 查询我的任务列表（分页）
     *
     * @param page 页码，从 1 开始，默认 1
     * @param size 每页条数，默认 10，最大 50
     */
    @GetMapping("/myTasks")
    public Result<PageResult<TryonStatusVO>> listMyTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(tryonService.listMyTasks(page, size));
    }
}
