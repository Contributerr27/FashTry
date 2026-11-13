package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 试穿提交响应
 * <p>
 * 提交成功后返回，前端使用 taskId 轮询 GET /api/tryon/{taskId} 获取生成进度
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TryonSubmitVO {

    /** 任务 ID，用于轮询查询状态（序列化为字符串，避免前端 JS 大整数精度丢失） */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long taskId;
}
