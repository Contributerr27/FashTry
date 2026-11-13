package com.fashtry.backend.bean.dto.fashn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Product to Model API inputs
 * <p>
 * 商品图转模特图：Product Only 模式仅需 product_image，Try-on 模式需 product_image + model_image
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductToModelInputs {

    /** 商品图片（服装、鞋子、配饰等），URL 或 Base64，必填 */
    @JsonProperty("product_image")
    private String productImage;

    /** 模特图片，用于试穿模式，可选 */
    @JsonProperty("model_image")
    private String modelImage;

    /** 参考图，用于控制姿势、环境和光线，可选 */
    @JsonProperty("image_prompt")
    private String imagePrompt;

    /** 脸部参考图，指定生成模特的脸部，可选（使用则消耗 4 积分） */
    @JsonProperty("face_reference")
    private String faceReference;

    /** match_reference（默认）：优先匹配参考脸；match_base：优先匹配 prompt */
    @JsonProperty("face_reference_mode")
    private String faceReferenceMode;

    /** 描述人物外观、穿搭或背景 */
    private String prompt;

    /** 比例：1:1 / 3:4 / 4:3 / 9:16 / 16:9 / 2:3 / 3:2 / 4:5 / 5:4 */
    @JsonProperty("aspect_ratio")
    private String aspectRatio;

    /** 1k（默认）：标准生成；4k：更高细节 */
    private String resolution;

    /** 背景参考图片 */
    @JsonProperty("background_reference")
    private String backgroundReference;

    /** 随机种子（用于复现结果） */
    private Integer seed;

    /** 生成图片数量（1-4） */
    @JsonProperty("num_images")
    private Integer numImages;

    /** png / jpeg */
    @JsonProperty("output_format")
    private String outputFormat;

    /** 是否返回 Base64 图片 */
    @JsonProperty("return_base64")
    private Boolean returnBase64;
}
