package com.fashtry.backend.bean.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量试穿提交响应：与单条 {@link TryonSubmitVO} 一致，taskId 使用字符串避免 JS 大整数精度问题
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TryonSubmitBatchVO {

    /** 与 garmentUrls 顺序一一对应的任务 ID，用于轮询 GET /api/tryon/task */
    private List<String> taskIds;
}
