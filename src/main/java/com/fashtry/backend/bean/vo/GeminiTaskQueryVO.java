package com.fashtry.backend.bean.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiTaskQueryVO {

    private Long taskId;
    /** PENDING / PROCESSING / COMPLETED / FAILED */
    private String status;
    private List<String> urls;
    /** 与 urls 对齐的标签（商品套图等） */
    private List<String> imageLabels;
    private List<String> textParts;
    private String errorMsg;
    private LocalDateTime createdAt;
    /** 开始执行生成的时间（PROCESSING/COMPLETED/FAILED 时有值，可与 createdAt 算排队时长） */
    private LocalDateTime processingStartedAt;
    /** 结束时间 */
    private LocalDateTime completedAt;
    /**
     * 本次任务从 {@link #processingStartedAt} 到结束的毫秒数（仅 COMPLETED/FAILED 时有值）。
     * 可与业务侧配置的「预计总毫秒」或历史均值对比，用于统计、推荐进度分母。
     */
    private Long generationDurationMs;

    /**
     * 仅 PROCESSING 时返回：服务端据当前时间计算的已执行毫秒（从 processingStartedAt 起算），便于直接做
     * {@code min(elapsed / estimatedTotalMs, 0.99)}，完成以 COMPLETED 为准。
     */
    private Long elapsedMsSinceProcessingStart;

    /** 服装套图单步重试任务：父任务（整套）ID */
    private Long parentTaskId;
    /** 服装套图单步重试：步骤索引 0～9 */
    private Integer regenerateSlotIndex;
}
