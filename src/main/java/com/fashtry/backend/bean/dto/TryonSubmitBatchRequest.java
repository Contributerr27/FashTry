package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量虚拟试穿提交：同一模特 + 多件服装，每件对应独立任务
 */
@Data
public class TryonSubmitBatchRequest {

    @NotBlank(message = "人物图片不能为空")
    private String modelUrl;

    /** 服装图 URL 列表，1～6 条 */
    @NotEmpty(message = "请至少添加一件服装")
    @Size(min = 1, max = 6, message = "单次最多 6 件服装")
    private List<@NotBlank(message = "服装图片不能为空") String> garmentUrls;

    private String category = "auto";

    /** 1K | 2K | 4K（默认 1K）；每件服装独立任务，扣点同单图试穿 */
    private String outputResolution = "1K";
}
