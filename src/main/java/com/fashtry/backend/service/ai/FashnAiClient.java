package com.fashtry.backend.service.ai;

import com.fashtry.backend.bean.dto.fashn.FashnStatusResponse;
import com.fashtry.backend.bean.dto.fashn.FashnRunRequest;

/**
 * FASHN Virtual Try-On API 客户端
 * <p>
 * 封装 FASHN 时尚换装 API 调用，支持提交试穿与轮询状态
 * 文档: https://docs.fashn.ai
 * </p>
 */
public interface FashnAiClient {

    /**
     * 提交虚拟试穿任务
     *
     * @param request 试穿请求；tryon-max 需 model_image + product_image，v1.6 需 model_image + garment_image
     * @return prediction id，用于后续轮询状态
     */
    String submitTryon(FashnRunRequest request);

    /**
     * 使用人物图、服装图 URL 提交试穿（服装类型默认 auto）
     */
    String submitTryon(String modelImageUrl, String garmentImageUrl);

    /**
     * 使用人物图、服装图 URL 及服装类型提交试穿（实现类根据配置选择 tryon-max / v1.6 与参数）
     *
     * @param category 服装类型：auto | tops | bottoms | one-pieces（仅 tryon-v1.6 会传给 API）
     */
    String submitTryon(String modelImageUrl, String garmentImageUrl, String category);

    /**
     * 同 {@link #submitTryon(String, String, String)}，tryon-max 时可指定 {@code generation_mode}（balanced | quality），null 则用配置默认
     */
    String submitTryon(String modelImageUrl, String garmentImageUrl, String category, String generationMode);

    /**
     * 轮询任务状态
     *
     * @param predictionId 提交时返回的 id
     * @return 状态响应，含 output URLs 或 error
     */
    FashnStatusResponse pollStatus(String predictionId);
}
