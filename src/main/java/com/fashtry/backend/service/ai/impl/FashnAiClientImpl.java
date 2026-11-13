package com.fashtry.backend.service.ai.impl;

import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.bean.dto.fashn.FashnRunInputs;
import com.fashtry.backend.bean.dto.fashn.FashnRunRequest;
import com.fashtry.backend.bean.dto.fashn.FashnRunResponse;
import com.fashtry.backend.bean.dto.fashn.FashnStatusResponse;
import com.fashtry.backend.common.config.AiProperties;
import com.fashtry.backend.common.config.TryonProperties;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.service.ai.FashnAiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ThreadLocalRandom;

/**
 * FASHN /v1/run 试穿客户端（Try-On Max 与 tryon-v1.6）
 * <p>
 * 提交: POST {baseUrl}/v1/run，返回 prediction_id；
 * 轮询: GET {baseUrl}/v1/status/{prediction_id}，返回 status/output/error
 * </p>
 */
@Service
public class FashnAiClientImpl implements FashnAiClient {

    private static final Logger log = LoggerFactory.getLogger(FashnAiClientImpl.class);

    /** FASHN 提交试穿接口路径 */
    private static final String RUN_PATH = "/v1/run";
    /** FASHN 状态轮询接口路径前缀 */
    private static final String STATUS_PATH = "/v1/status/";

    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;
    private final TryonProperties tryonProperties;

    public FashnAiClientImpl(RestTemplate restTemplate,
                               AiProperties aiProperties,
                               TryonProperties tryonProperties) {
        this.restTemplate = restTemplate;
        this.aiProperties = aiProperties;
        this.tryonProperties = tryonProperties;
    }

    @Override
    public String submitTryon(String modelImageUrl, String garmentImageUrl) {
        return submitTryon(modelImageUrl, garmentImageUrl, "auto", null);
    }

    @Override
    public String submitTryon(String modelImageUrl, String garmentImageUrl, String category) {
        return submitTryon(modelImageUrl, garmentImageUrl, category, null);
    }

    @Override
    public String submitTryon(String modelImageUrl, String garmentImageUrl, String category, String generationModeOverride) {
        String modelName = tryonProperties.getFashnModelName();
        if (!StringUtils.hasText(modelName)) {
            modelName = "tryon-max";
        }
        FashnRunInputs.FashnRunInputsBuilder in = FashnRunInputs.builder().modelImage(modelImageUrl);
        FashnRunRequest.FashnRunRequestBuilder builder = FashnRunRequest.builder().modelName(modelName);
        if ("tryon-max".equalsIgnoreCase(modelName.trim())) {
            in.productImage(garmentImageUrl);
            String resolution = trimOrNull(tryonProperties.getFashnResolution());
            if (resolution == null) {
                resolution = "1k";
            }
            String generationMode = trimOrNull(generationModeOverride);
            if (generationMode == null) {
                generationMode = trimOrNull(tryonProperties.getFashnGenerationMode());
            }
            if (generationMode == null) {
                generationMode = "quality";
            }
            generationMode = generationMode.toLowerCase();
            if (!"balanced".equals(generationMode) && !"quality".equals(generationMode)) {
                generationMode = "quality";
            }
            builder.inputs(in.build())
                    .resolution(resolution)
                    .generationMode(generationMode)
                    .numImages(1);
        } else {
            in.garmentImage(garmentImageUrl);
            String v16Mode = trimOrNull(generationModeOverride);
            if (v16Mode == null) {
                v16Mode = trimOrNull(tryonProperties.getFashnLegacyMode());
            }
            if (v16Mode == null) {
                v16Mode = "balanced";
            }
            builder.inputs(in.build()).mode(v16Mode);
            if (category != null && !category.isBlank()) {
                builder.category(category.trim());
            }
        }
        builder.seed(randomTryonSeed());
        return submitTryon(builder.build());
    }

    /** FASHN 要求 seed ∈ [0, 2³²−1]，此处用非负 int 均匀随机 */
    private static int randomTryonSeed() {
        return ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Try-On Max 与 tryon-v1.6 共用 /v1/run，去掉 v1.6 字段，避免与 {@code generation_mode} / {@code num_images} 混用。
     */
    private static void sanitizeTryonMaxPayload(FashnRunRequest r) {
        r.setMode(null);
        r.setCategory(null);
        r.setSegmentationFree(null);
        r.setModerationLevel(null);
        r.setGarmentPhotoType(null);
        r.setNumSamples(null);
        if (r.getInputs() != null) {
            r.getInputs().setGarmentImage(null);
        }
        if (r.getNumImages() == null) {
            r.setNumImages(1);
        }
        if (!StringUtils.hasText(r.getResolution())) {
            r.setResolution("1k");
        }
        if (!StringUtils.hasText(r.getGenerationMode())) {
            r.setGenerationMode("quality");
        }
    }

    @Override
    public String submitTryon(FashnRunRequest request) {
        if (request == null || request.getInputs() == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "试穿请求参数不能为空");
        }
        if (!StringUtils.hasText(request.getInputs().getModelImage())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "人物照片 model_image 不能为空");
        }
        if (request.getSeed() == null) {
            request.setSeed(randomTryonSeed());
        }
        boolean tryonMax = request.getModelName() != null && "tryon-max".equalsIgnoreCase(request.getModelName().trim());
        if (tryonMax) {
            if (!StringUtils.hasText(request.getInputs().getProductImage())) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "tryon-max 需要商品图 product_image");
            }
        } else if (!StringUtils.hasText(request.getInputs().getGarmentImage())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "服装照片 garment_image 不能为空");
        }

        if (tryonMax) {
            sanitizeTryonMaxPayload(request);
        }

        String url = buildUrl(RUN_PATH);
        HttpHeaders headers = buildHeaders();
        HttpEntity<FashnRunRequest> entity = new HttpEntity<>(request, headers);

        if (tryonMax) {
            log.info("FASHN tryon-max POST {}: resolution={}, generation_mode={}, num_images={}, seed={}, model/ product 图长度={}/{}",
                    url,
                    request.getResolution(),
                    request.getGenerationMode(),
                    request.getNumImages(),
                    request.getSeed(),
                    maskLength(request.getInputs().getModelImage()),
                    maskLength(request.getInputs().getProductImage()));
        } else {
            log.info("FASHN tryon-v1.6 POST {}: mode={}, model_image 长度={}",
                    url, request.getMode(), maskLength(request.getInputs().getModelImage()));
        }

        try {
            ResponseEntity<FashnRunResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    FashnRunResponse.class
            );

            FashnRunResponse body = response.getBody();
            if (body == null) {
                throw new BusinessException(ResultCode.AI_TRYON_FAIL, "FASHN API 返回为空");
            }
            if (StringUtils.hasText(body.getError())) {
                log.warn("FASHN 提交返回错误: {}", body.getError());
                throw new BusinessException(ResultCode.AI_TRYON_FAIL, "FASHN: " + body.getError());
            }
            if (!StringUtils.hasText(body.getId())) {
                throw new BusinessException(ResultCode.AI_TRYON_FAIL, "FASHN API 未返回 prediction id");
            }

            log.info("FASHN 提交成功: prediction_id={}", body.getId());
            return body.getId();
        } catch (HttpClientErrorException e) {
            String msg = parseErrorResponse(e.getResponseBodyAsString(), e.getStatusCode().value());
            log.error("FASHN 提交 4xx: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ResultCode.AI_TRYON_FAIL, msg);
        } catch (HttpServerErrorException e) {
            String msg = parseErrorResponse(e.getResponseBodyAsString(), e.getStatusCode().value());
            log.error("FASHN 提交 5xx: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ResultCode.AI_TRYON_FAIL, msg);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("FASHN 提交异常: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.AI_TRYON_FAIL, "试穿服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public FashnStatusResponse pollStatus(String predictionId) {
        if (!StringUtils.hasText(predictionId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "prediction_id 不能为空");
        }

        String url = buildUrl(STATUS_PATH + predictionId);
        HttpHeaders headers = buildHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<FashnStatusResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    FashnStatusResponse.class
            );

            FashnStatusResponse body = response.getBody();
            if (body == null) {
                throw new BusinessException(ResultCode.AI_TRYON_FAIL, "FASHN 状态接口返回为空");
            }
            return body;
        } catch (HttpClientErrorException e) {
            log.error("FASHN 轮询 4xx: predictionId={}, status={}, body={}",
                    predictionId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ResultCode.AI_TRYON_FAIL,
                    parseErrorResponse(e.getResponseBodyAsString(), e.getStatusCode().value()));
        } catch (HttpServerErrorException e) {
            log.error("FASHN 轮询 5xx: predictionId={}, status={}, body={}",
                    predictionId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ResultCode.AI_TRYON_FAIL,
                    parseErrorResponse(e.getResponseBodyAsString(), e.getStatusCode().value()));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("FASHN 轮询异常: predictionId={}, error={}", predictionId, e.getMessage(), e);
            throw new BusinessException(ResultCode.AI_TRYON_FAIL, "状态查询失败: " + e.getMessage());
        }
    }

    /** 拼接完整请求 URL */
    private String buildUrl(String path) {
        String base = aiProperties.getFashnApiBaseUrl();
        if (base == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "未配置 app.ai.fashn-api-base-url");
        }
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return base + path;
    }

    /** 构建请求头（Content-Type + Bearer Token） */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String apiKey = aiProperties.getFashnApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "未配置 app.ai.fashn-api-key");
        }
        headers.setBearerAuth(apiKey);
        return headers;
    }

    /** 脱敏：仅输出长度，避免日志泄露 base64 */
    private String maskLength(String value) {
        if (value == null) return "null";
        if (value.length() > 50) return value.length() + " chars";
        return value.length() + " chars";
    }

    /** 解析 HTTP 错误响应，截断过长内容 */
    private String parseErrorResponse(String body, int statusCode) {
        if (StringUtils.hasText(body) && body.length() < 200) {
            return "FASHN API 错误(" + statusCode + "): " + body;
        }
        return "FASHN API 错误(" + statusCode + ")，请稍后重试";
    }
}
