package com.fashtry.backend.bean.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GeminiTaskSubmitVO {

    /** 异步任务 ID（例：局部改款轮询 GET /api/image-edit/tasks/{taskId}） */
    private Long taskId;

    /**
     * 本任务在成功出图后将扣除的点数（失败不扣）；未计费的接口不返回或返回 0。
     * 供前端展示说明，与 {@link com.fashtry.backend.common.config.GeminiApiProperties#getLocalEditConsumeCredits()} 一致。
     */
    private Integer consumeCredits;

    /**
     * 服装套图「单张重生成」子任务时，对应父任务（整套 10 张）ID；仅该场景返回，便于前端轮询结束后同步父画布。
     */
    private Long parentTaskId;

    public GeminiTaskSubmitVO(Long taskId, Integer consumeCredits) {
        this.taskId = taskId;
        this.consumeCredits = consumeCredits;
    }

    public GeminiTaskSubmitVO(Long taskId, Integer consumeCredits, Long parentTaskId) {
        this.taskId = taskId;
        this.consumeCredits = consumeCredits;
        this.parentTaskId = parentTaskId;
    }
}
