package com.fashtry.backend.bean.dto.fashn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FASHN API POST /v1/run 请求体
 * <p>
 * tryon-max：{@code model_name=tryon-max}、{@code inputs.product_image}、{@code inputs.model_image}，
 * 可选 {@code resolution}、{@code generation_mode}、{@code seed}、{@code num_images} 等（见官方 Try-On Max 文档）。
 * tryon-v1.6：{@code garment_image}、{@code mode}、{@code num_samples} 等旧字段。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FashnRunRequest {

    /** 模型名称：tryon-max（推荐）或 tryon-v1.6 */
    @Builder.Default
    @JsonProperty("model_name")
    private String modelName = "tryon-max";

    /** 必填：人物图与服装图 */
    private FashnRunInputs inputs;

    /** 服装类型: auto | tops | bottoms | one-pieces，默认 auto */
    private String category;

    /** 是否无分割直接拟合，默认 true */
    @JsonProperty("segmentation_free")
    private Boolean segmentationFree;

    /** 内容审核: conservative | permissive | none，默认 permissive */
    @JsonProperty("moderation_level")
    private String moderationLevel;

    /** 服装照片类型: auto | flat-lay | model，默认 auto */
    @JsonProperty("garment_photo_type")
    private String garmentPhotoType;

    /** tryon-v1.6 生成模式: performance | balanced | quality */
    private String mode;

    /** tryon-max：输出分辨率 1k | 2k | 4k */
    private String resolution;

    /** tryon-max：balanced | quality */
    @JsonProperty("generation_mode")
    private String generationMode;

    /** 随机种子，用于复现结果 */
    private Integer seed;

    /** tryon-max：生成张数 1–4，对应 JSON {@code num_images} */
    @JsonProperty("num_images")
    private Integer numImages;

    /** tryon-v1.6：生成张数字段名 {@code num_samples} */
    @JsonProperty("num_samples")
    private Integer numSamples;

    /** 输出格式: png | jpeg，默认 png */
    @JsonProperty("output_format")
    private String outputFormat;

    /** 是否返回 base64 而非 CDN URL，默认 false */
    @JsonProperty("return_base64")
    private Boolean returnBase64;
}
