package com.fashtry.backend.bean.dto.fashn;

import lombok.Data;

/**
 * FASHN API POST /v1/run 响应
 * <p>
 * 成功时 id 为 prediction_id，用于后续 GET /v1/status/{id} 轮询
 * 失败时 error 不为 null
 * </p>
 */
@Data
public class FashnRunResponse {

    /** 预测任务 ID，用于轮询 GET /v1/status/{id} */
    private String id;

    /** 错误信息，成功时为 null */
    private String error;
}
