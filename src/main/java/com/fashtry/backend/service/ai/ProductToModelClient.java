package com.fashtry.backend.service.ai;

import com.fashtry.backend.bean.dto.fashn.FashnStatusResponse;
import com.fashtry.backend.bean.dto.fashn.ProductToModelInputs;
import com.fashtry.backend.bean.dto.fashn.ProductToModelRequest;

/**
 * Product to Model API 客户端
 * <p>
 * 商品图转模特图：Product Only 模式仅需商品图，Try-on 模式需商品图 + 模特图
 * 文档: Product to Model API 文档
 * </p>
 */
public interface ProductToModelClient {

    /**
     * 提交商品图转模特图任务
     *
     * @param request 请求体，inputs.product_image 必填
     * @return prediction id，用于后续轮询
     */
    String submit(ProductToModelRequest request);

    /**
     * 轮询任务状态（复用 FASHN 状态接口）
     *
     * @param predictionId 提交时返回的 id
     * @return 状态响应，含 output URLs 或 error
     */
    FashnStatusResponse pollStatus(String predictionId);

    /**
     * 便捷方法：仅商品图（Product Only 模式）
     */
    default String submitProductOnly(String productImageUrl) {
        return submit(ProductToModelRequest.builder()
                .modelName("product-to-model")
                .inputs(ProductToModelInputs.builder()
                        .productImage(productImageUrl)
                        .build())
                .build());
    }

    /**
     * 便捷方法：商品图 + 模特图（Try-on 模式）
     */
    default String submitTryOn(String productImageUrl, String modelImageUrl) {
        return submit(ProductToModelRequest.builder()
                .modelName("product-to-model")
                .inputs(ProductToModelInputs.builder()
                        .productImage(productImageUrl)
                        .modelImage(modelImageUrl)
                        .build())
                .build());
    }
}
