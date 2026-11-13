package com.fashtry.backend.pay;

import com.fashtry.backend.common.config.PayPalProperties;
import com.fashtry.backend.common.config.PayProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * PayPal REST API v2 支付服务
 * <p>
 * 流程：创建订单 → 返回 approval_url → 用户跳转支付 → 回调后 capture 捕获
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayPalService {

    private static final BigDecimal CNY_TO_USD = new BigDecimal("0.14");

    private final RestTemplate restTemplate;
    private final PayPalProperties paypalProperties;
    private final PayProperties payProperties;

    @PostConstruct
    public void logConfig() {
        if (paypalProperties.isConfigured()) {
            boolean isLive = paypalProperties.getBaseUrl() != null && paypalProperties.getBaseUrl().contains("api-m.paypal.com") && !paypalProperties.getBaseUrl().contains("sandbox");
            log.info("PayPal 已配置: baseUrl={}, 环境={}", paypalProperties.getBaseUrl(), isLive ? "Live" : "Sandbox");
        } else {
            log.warn("PayPal 未配置: clientId 或 clientSecret 为空");
        }
    }

    /**
     * 创建 PayPal 订单（金额为美元）
     *
     * @param orderNo   我方订单号
     * @param amountUSD 金额（美元）
     * @param subject   商品描述
     * @return approval_url
     */
    public String createOrderWithUSD(String orderNo, BigDecimal amountUSD, String subject) {
        return createOrderInternal(orderNo, amountUSD.setScale(2, RoundingMode.HALF_UP), subject);
    }

    /**
     * 创建 PayPal 订单，返回用户跳转的 approval URL（金额为人民币，内部换算为美元）
     *
     * @param orderNo   我方订单号
     * @param amountCNY 金额（人民币）
     * @param subject   商品描述
     * @return approval_url，用户跳转此 URL 完成支付
     */
    @SuppressWarnings("unchecked")
    public String createOrder(String orderNo, BigDecimal amountCNY, String subject) {
        if (!paypalProperties.isConfigured()) {
            log.warn("PayPal 未配置");
            return null;
        }
        String accessToken = getAccessToken();
        if (accessToken == null) return null;

        BigDecimal amountUSD = amountCNY.multiply(CNY_TO_USD).setScale(2, RoundingMode.HALF_UP);
        return createOrderInternal(orderNo, amountUSD, subject);
    }

    @SuppressWarnings("unchecked")
    private String createOrderInternal(String orderNo, BigDecimal amountUSD, String subject) {
        if (!paypalProperties.isConfigured()) return null;
        String accessToken = getAccessToken();
        if (accessToken == null) return null;

        String returnUrl = payProperties.getNotifyBaseUrl() + "/recharge.html?paypal_return=1&orderNo=" + orderNo;
        String cancelUrl = payProperties.getNotifyBaseUrl() + "/recharge.html?paypal_cancel=1";

        String url = paypalProperties.getBaseUrl().replaceAll("/$", "") + "/v2/checkout/orders";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", new Object[]{
                        Map.of(
                                "reference_id", orderNo,
                                "description", subject,
                                "amount", Map.of(
                                        "currency_code", "USD",
                                        "value", amountUSD.toString()
                                )
                        )
                },
                "application_context", Map.of(
                        "return_url", returnUrl,
                        "cancel_url", cancelUrl,
                        "brand_name", "AI时尚换装"
                )
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> respBody = resp.getBody();
            if (respBody == null || resp.getStatusCode() != HttpStatusCode.valueOf(201)) {
                log.error("PayPal 创建订单失败: status={}, body={}", resp.getStatusCode(), respBody);
                return null;
            }
            for (Object link : (Iterable<?>) respBody.getOrDefault("links", java.util.Collections.emptyList())) {
                Map<String, String> m = (Map<String, String>) link;
                if ("approve".equals(m.get("rel"))) {
                    String approvalUrl = m.get("href");
                    log.info("PayPal 订单创建成功: orderNo={}, approvalUrl={}", orderNo, approvalUrl);
                    return approvalUrl;
                }
            }
            log.error("PayPal 响应中未找到 approval link");
            return null;
        } catch (Exception e) {
            log.error("PayPal 创建订单异常: orderNo={}, error={}", orderNo, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 捕获已批准的 PayPal 订单
     *
     * @param paypalOrderId PayPal 订单 ID（即 return_url 中的 token）
     * @return 是否成功
     */
    @SuppressWarnings("unchecked")
    public boolean captureOrder(String paypalOrderId) {
        if (!paypalProperties.isConfigured() || paypalOrderId == null || paypalOrderId.isBlank()) {
            return false;
        }
        String accessToken = getAccessToken();
        if (accessToken == null) return false;

        String url = paypalProperties.getBaseUrl().replaceAll("/$", "") + "/v2/checkout/orders/" + paypalOrderId + "/capture";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> respBody = resp.getBody();
            if (respBody == null) return false;
            String status = (String) respBody.get("status");
            boolean ok = "COMPLETED".equals(status);
            if (ok) {
                log.info("PayPal 捕获成功: paypalOrderId={}", paypalOrderId);
            } else {
                log.warn("PayPal 捕获状态异常: status={}, paypalOrderId={}", status, paypalOrderId);
            }
            return ok;
        } catch (Exception e) {
            log.error("PayPal 捕获异常: paypalOrderId={}", paypalOrderId, e);
            return false;
        }
    }

    private String getAccessToken() {
        String url = paypalProperties.getBaseUrl().replaceAll("/$", "") + "/v1/oauth2/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(paypalProperties.getClientId(), paypalProperties.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> respBody = resp.getBody();
            if (respBody != null && respBody.containsKey("access_token")) {
                return (String) respBody.get("access_token");
            }
            log.error("PayPal 获取 access_token 失败: status={}, body={}", resp.getStatusCode(), respBody);
            return null;
        } catch (Exception e) {
            log.error("PayPal 获取 access_token 异常: {}", e.getMessage(), e);
            return null;
        }
    }
}
