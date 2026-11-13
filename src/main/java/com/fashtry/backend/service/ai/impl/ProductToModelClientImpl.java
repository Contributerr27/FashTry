package com.fashtry.backend.service.ai.impl;

import com.fashtry.backend.bean.dto.fashn.FashnStatusResponse;
import com.fashtry.backend.bean.dto.fashn.ProductToModelRequest;
import com.fashtry.backend.common.config.AiProperties;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.bean.dto.fashn.FashnRunResponse;
import com.fashtry.backend.service.ai.ProductToModelClient;
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

/**
 * Product to Model API 客户端实现
 * <p>
 * 提交: POST {baseUrl}/v1/run
 * 轮询: GET {baseUrl}/v1/status/{id}
 * </p>
 */
@Service
public class ProductToModelClientImpl implements ProductToModelClient {

    private static final Logger log = LoggerFactory.getLogger(ProductToModelClientImpl.class);

    private static final String RUN_PATH = "/v1/run";
    private static final String STATUS_PATH = "/v1/status/";

    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;

    public ProductToModelClientImpl(RestTemplate restTemplate, AiProperties aiProperties) {
        this.restTemplate = restTemplate;
        this.aiProperties = aiProperties;
    }

    @Override
    public String submit(ProductToModelRequest request) {
        if (request == null || request.getInputs() == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请求参数不能为空");
        }
        if (!StringUtils.hasText(request.getInputs().getProductImage())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "商品图片 product_image 不能为空");
        }

        String url = buildUrl(RUN_PATH);
        HttpHeaders headers = buildHeaders();
        HttpEntity<ProductToModelRequest> entity = new HttpEntity<>(request, headers);

        log.info("ProductToModel 提交: url={}", url);

        try {
            ResponseEntity<FashnRunResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    FashnRunResponse.class
            );

            FashnRunResponse body = response.getBody();
            if (body == null) {
                throw new BusinessException(ResultCode.AI_TRYON_FAIL, "API 返回为空");
            }
            if (StringUtils.hasText(body.getError())) {
                log.warn("ProductToModel 提交返回错误: {}", body.getError());
                throw new BusinessException(ResultCode.AI_TRYON_FAIL, "API: " + body.getError());
            }
            if (!StringUtils.hasText(body.getId())) {
                throw new BusinessException(ResultCode.AI_TRYON_FAIL, "API 未返回 prediction id");
            }

            log.info("ProductToModel 提交成功: prediction_id={}", body.getId());
            return body.getId();
        } catch (HttpClientErrorException e) {
            String msg = parseErrorResponse(e.getResponseBodyAsString(), e.getStatusCode().value());
            log.error("ProductToModel 提交 4xx: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ResultCode.AI_TRYON_FAIL, msg);
        } catch (HttpServerErrorException e) {
            String msg = parseErrorResponse(e.getResponseBodyAsString(), e.getStatusCode().value());
            log.error("ProductToModel 提交 5xx: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ResultCode.AI_TRYON_FAIL, msg);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("ProductToModel 提交异常: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.AI_TRYON_FAIL, "服务调用失败: " + e.getMessage());
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
                throw new BusinessException(ResultCode.AI_TRYON_FAIL, "状态接口返回为空");
            }
            return body;
        } catch (HttpClientErrorException e) {
            log.error("ProductToModel 轮询 4xx: predictionId={}, status={}",
                    predictionId, e.getStatusCode());
            throw new BusinessException(ResultCode.AI_TRYON_FAIL,
                    parseErrorResponse(e.getResponseBodyAsString(), e.getStatusCode().value()));
        } catch (HttpServerErrorException e) {
            log.error("ProductToModel 轮询 5xx: predictionId={}, status={}",
                    predictionId, e.getStatusCode());
            throw new BusinessException(ResultCode.AI_TRYON_FAIL,
                    parseErrorResponse(e.getResponseBodyAsString(), e.getStatusCode().value()));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("ProductToModel 轮询异常: predictionId={}, error={}", predictionId, e.getMessage(), e);
            throw new BusinessException(ResultCode.AI_TRYON_FAIL, "状态查询失败: " + e.getMessage());
        }
    }

    private String buildUrl(String path) {
        String base = aiProperties.getFashnApiBaseUrl();
        if (base == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "未配置 app.ai.fashn-api-base-url");
        }
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return base + path;
    }

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

    private String parseErrorResponse(String body, int statusCode) {
        if (StringUtils.hasText(body) && body.length() < 200) {
            return "API 错误(" + statusCode + "): " + body;
        }
        return "API 错误(" + statusCode + ")，请稍后重试";
    }
}
