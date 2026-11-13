package com.fashtry.backend.bean.dto.fashn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FASHN API /v1/run 请求的 inputs 嵌套对象
 * <p>
 * tryon-v1.6：model_image + garment_image；
 * tryon-max：model_image + product_image（商品/服装图）
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FashnRunInputs {

    /** 人物照片（URL 或 base64，base64 需带前缀如 data:image/jpg;base64,xxx） */
    @JsonProperty("model_image")
    private String modelImage;

    /** tryon-v1.6 服装图 */
    @JsonProperty("garment_image")
    private String garmentImage;

    /** tryon-max 商品/服装图 */
    @JsonProperty("product_image")
    private String productImage;
}
