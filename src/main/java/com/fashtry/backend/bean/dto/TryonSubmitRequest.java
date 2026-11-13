package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 虚拟试穿提交请求
 * <p>
 * 用户提交试穿任务时传入，需先通过 /api/upload/business 上传人物图和服装图获取 URL
 * </p>
 */
@Data
public class TryonSubmitRequest {

    /** 人物/模特图 URL（需先通过 /api/upload/business 上传） */
    @NotBlank(message = "人物图片不能为空")
    private String modelUrl;

    /** 服装图 URL */
    @NotBlank(message = "服装图片不能为空")
    private String garmentUrl;

    /** 服装类型：auto（自动，后端按整身/套装处理） | tops（上装） | bottoms（下装） | one-pieces（连体/套装/多件一套），默认 auto */
    private String category = "auto";

    /**
     * 输出分辨率：1K | 2K | 4K（默认 1K）；对应 Gemini {@code imageSize}，扣点：1K=1 点、2K=2 点、4K=4 点
     */
    private String outputResolution = "1K";

    /**
     * 可选。传入则替换默认试穿正文；仍会按 category 追加上装/下装/整身后缀。
     * 不传时走通用虚拟试穿提示词。
     */
    private String instruction;
}
