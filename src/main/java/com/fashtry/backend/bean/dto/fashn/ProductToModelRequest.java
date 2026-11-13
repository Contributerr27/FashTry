package com.fashtry.backend.bean.dto.fashn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Product to Model API 请求体
 * <p>
 * POST https://api.fashn.ai/v1/run
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductToModelRequest {

    @Builder.Default
    @JsonProperty("model_name")
    private String modelName = "product-to-model";

    private ProductToModelInputs inputs;
}
