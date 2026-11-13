package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Product to Model 提交请求（简化版，供 API 使用）
 */
@Data
public class ProductToModelSubmitRequest {

    /** 商品图片 URL，必填 */
    @NotBlank(message = "商品图片不能为空")
    private String productImage;

    /** 模特图片 URL，可选，用于 Try-on 模式 */
    private String modelImage;

    /** 参考图 URL，可选 */
    private String imagePrompt;

    /** 脸部参考图 URL，可选（使用则消耗 4 积分） */
    private String faceReference;

    /** match_reference / match_base */
    private String faceReferenceMode;

    /** 描述人物外观、穿搭或背景 */
    private String prompt;

    /** 比例：1:1 / 3:4 / 4:3 / 9:16 / 16:9 / 2:3 / 3:2 / 4:5 / 5:4 */
    private String aspectRatio;

    /** 1k / 4k */
    private String resolution;

    /** 背景参考图 URL */
    private String backgroundReference;

    /** 随机种子 */
    private Integer seed;

    /** 生成数量 1-4 */
    private Integer numImages;

    /** png / jpeg */
    private String outputFormat;

    /** 是否返回 Base64 */
    private Boolean returnBase64;
}
