package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量查询试穿任务状态：与 {@link com.fashtry.backend.bean.vo.TryonSubmitBatchVO} 返回的 taskIds 对应
 */
@Data
public class TryonBatchStatusRequest {

    /** 任务 ID 列表（字符串避免 JS 大整数精度问题），1～20 条 */
    @NotEmpty(message = "taskIds 不能为空")
    @Size(min = 1, max = 20, message = "单次最多查询 20 个任务")
    private List<@NotBlank(message = "任务 ID 不能为空") String> taskIds;
}
