package com.fashtry.backend.bean.dto.fashn;

import lombok.Data;

import java.util.List;

/**
 * FASHN API GET /v1/status/{id} 轮询响应
 * <p>
 * status: in_queue | starting | processing | completed | failed
 * completed 时 output 有值，failed 时 error 有值
 * </p>
 */
@Data
public class FashnStatusResponse {

    /** 预测任务 ID */
    private String id;

    /** 状态: in_queue | starting | processing | completed | failed */
    private String status;

    /** 生成完成时的输出图片 URL 列表 */
    private List<String> output;

    /** 失败时的错误信息 */
    private String error;
}
