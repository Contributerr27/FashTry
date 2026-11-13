package com.fashtry.backend.bean.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenApiSubmitResultVO {

    private String model;

    /** 单任务：试穿或 Gemini 单 taskId */
    private String taskId;

    /** 仅 batch_tryon：与 garmentUrls 顺序一致 */
    private List<String> taskIds;
}
