package com.fashtry.backend.pay;

import com.fashtry.backend.bean.db.Order;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Lemon Squeezy 托管结账链接与 Webhook 签名校验。
 * <p>同一 Product 下多档 variant 时，直链 {@code /checkout/buy/{slug}} 会在结账页展示全部档位；
 * 配置 api-key、id（Store）、product-id 后改为调用 Checkout API，并设置 {@code enabled_variants} 仅当前档。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LemonSqueezyService {

    private static final String LS_API_BASE = "https://api.lemonsqueezy.com/v1";

    private final LemonSqueezyProperties properties;
    private final ObjectMapper objectMapper;

    private final Object variantSlugCacheLock = new Object();
    private Map<String, String> variantSlugToApiIdCache;

    public boolean isConfiguredForCheckout(int packageId) {
        if (!StringUtils.hasText(resolveVariantSlug(packageId))) {
            return false;
        }
        return useCheckoutApi() || StringUtils.hasText(properties.getCheckoutBaseUrl());
    }

    private boolean useCheckoutApi() {
        return StringUtils.hasText(properties.getApiKey())
                && properties.getId() != null
                && properties.getProductId() != null;
    }

    private String resolveVariantSlug(int packageId) {
        if (properties.getVariantByPackageId() == null) {
            return null;
        }
        String v = properties.getVariantByPackageId().get(String.valueOf(packageId));
        return v != null ? v.trim() : null;
    }

    /**
     * 生成结账 URL：优先 Checkout API（单档）；否则托管直链 {@code /checkout/buy/{variantSlug}}。
     */
    public String buildHostedCheckoutUrl(int packageId, String orderNo) {
        String variantSlug = resolveVariantSlug(packageId);
        if (!StringUtils.hasText(variantSlug)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该套餐未配置 Lemon Squeezy Variant");
        }
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "订单号为空");
        }
        if (useCheckoutApi()) {
            return createSingleVariantCheckoutUrl(packageId, orderNo, variantSlug);
        }
        String base = properties.getCheckoutBaseUrl() != null ? properties.getCheckoutBaseUrl().trim() : "";
        if (!StringUtils.hasText(base)) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR,
                    "未配置 lemon-squeezy.checkout-base-url（或补齐 api-key、id、product-id 以走 Checkout API）");
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return UriComponentsBuilder.fromHttpUrl(base + "/" + variantSlug)
                .queryParam("checkout[custom][order_no]", orderNo)
                .queryParam("checkout[custom][package_id]", String.valueOf(packageId))
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();
    }

    private String createSingleVariantCheckoutUrl(int packageId, String orderNo, String variantSlug) {
        String variantApiId = resolveVariantApiId(variantSlug);
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode data = body.putObject("data");
        data.put("type", "checkouts");
        ObjectNode attributes = data.putObject("attributes");
        ObjectNode productOptions = attributes.putObject("product_options");
        ArrayNode enabled = productOptions.putArray("enabled_variants");
        enabled.add(Long.parseLong(variantApiId));
        ObjectNode checkoutData = attributes.putObject("checkout_data");
        ObjectNode custom = checkoutData.putObject("custom");
        custom.put("order_no", orderNo);
        custom.put("package_id", String.valueOf(packageId));

        ObjectNode relationships = data.putObject("relationships");
        ObjectNode storeData = relationships.putObject("store").putObject("data");
        storeData.put("type", "stores");
        storeData.put("id", String.valueOf(properties.getId()));
        ObjectNode variantData = relationships.putObject("variant").putObject("data");
        variantData.put("type", "variants");
        variantData.put("id", variantApiId);

        String rawBody;
        try {
            rawBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "构建 Lemon Squeezy Checkout 请求失败");
        }

        try {
            String responseJson = lsRestClient()
                    .post()
                    .uri(URI.create(LS_API_BASE + "/checkouts"))
                    .contentType(MediaType.parseMediaType("application/vnd.api+json"))
                    .body(rawBody)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(responseJson);
            String url = root.path("data").path("attributes").path("url").asText("").trim();
            if (url.isEmpty()) {
                log.warn("Lemon Squeezy Create Checkout 响应无 url: {}", responseJson);
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "Lemon Squeezy 创建结账失败");
            }
            return url;
        } catch (RestClientResponseException e) {
            log.warn("Lemon Squeezy Create Checkout HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new BusinessException(ResultCode.SYSTEM_ERROR,
                    "Lemon Squeezy 创建结账失败，请检查 api-key、Store/Product 与 variant 是否同属一商品");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Lemon Squeezy Create Checkout 异常: {}", e.getMessage());
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Lemon Squeezy 创建结账失败");
        }
    }

    private RestClient lsRestClient() {
        return RestClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey().trim())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.api+json")
                .build();
    }

    private String resolveVariantApiId(String variantSlug) {
        String key = variantSlug.trim().toLowerCase(Locale.ROOT);
        Map<String, String> map = getVariantSlugToApiIdMap();
        String id = map.get(key);
        if (id == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR,
                    "未在 Lemon Squeezy Product " + properties.getProductId() + " 下找到 variant slug: " + variantSlug
                            + "。请确认 variant-by-package-id 与 product-id 对应同一商品。");
        }
        return id;
    }

    private Map<String, String> getVariantSlugToApiIdMap() {
        synchronized (variantSlugCacheLock) {
            if (variantSlugToApiIdCache != null) {
                return variantSlugToApiIdCache;
            }
            variantSlugToApiIdCache = fetchVariantSlugToApiIdMap();
            return variantSlugToApiIdCache;
        }
    }

    private Map<String, String> fetchVariantSlugToApiIdMap() {
        Long productId = properties.getProductId();
        URI uri = UriComponentsBuilder.fromHttpUrl(LS_API_BASE + "/variants")
                .queryParam("filter[product_id]", productId)
                .queryParam("page[size]", 100)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        String responseJson;
        try {
            responseJson = lsRestClient()
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("Lemon Squeezy List Variants HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "拉取 Lemon Squeezy 套餐列表失败");
        } catch (Exception e) {
            log.warn("Lemon Squeezy List Variants 异常: {}", e.getMessage());
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "拉取 Lemon Squeezy 套餐列表失败");
        }
        Map<String, String> map = new HashMap<>();
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return map;
            }
            for (JsonNode v : data) {
                String id = v.path("id").asText("").trim();
                String slug = v.path("attributes").path("slug").asText("").trim().toLowerCase(Locale.ROOT);
                if (!id.isEmpty() && !slug.isEmpty()) {
                    map.put(slug, id);
                }
            }
        } catch (Exception e) {
            log.warn("解析 Lemon Squeezy Variants 响应失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "解析 Lemon Squeezy 套餐列表失败");
        }
        return map;
    }

    /**
     * 校验 LS 实付与本地待支付订单一致，防止篡改结账链接中的 variant 低价支付却用原 order_no 加款。
     *
     * @return {@code null} 表示通过；否则为拒绝原因（不落库、不加款）
     */
    public String validateWebhookPaidMatchesOrder(JsonNode root, Order order) {
        JsonNode attrs = root.path("data").path("attributes");
        JsonNode meta = root.path("meta").path("custom_data");

        JsonNode pkgNode = meta.path("package_id");
        if (!pkgNode.isMissingNode() && !pkgNode.isNull()) {
            String pkgRaw = pkgNode.isIntegralNumber()
                    ? String.valueOf(pkgNode.asInt())
                    : pkgNode.asText("").trim();
            if (!pkgRaw.isEmpty()) {
                try {
                    int pid = Integer.parseInt(pkgRaw);
                    if (!Objects.equals(order.getPackageId(), pid)) {
                        return "custom_data.package_id 与订单 package_id 不一致";
                    }
                } catch (NumberFormatException e) {
                    return "custom_data.package_id 非法";
                }
            }
        }

        String lsCurrency = attrs.path("currency").asText("").trim();
        String orderCurrency = order.getCurrency() != null ? order.getCurrency().trim() : "";
        if (!orderCurrency.isEmpty() && !lsCurrency.isEmpty()
                && !orderCurrency.equalsIgnoreCase(lsCurrency)) {
            return "币种不一致: order=" + orderCurrency + " ls=" + lsCurrency;
        }

        JsonNode totalNode = attrs.get("total");
        if (totalNode == null || totalNode.isNull()) {
            return "缺少 attributes.total";
        }
        long lsTotalMinor;
        if (totalNode.isNumber()) {
            lsTotalMinor = totalNode.longValue();
        } else if (totalNode.isTextual()) {
            try {
                lsTotalMinor = Long.parseLong(totalNode.asText().trim());
            } catch (NumberFormatException e) {
                return "attributes.total 非数字";
            }
        } else {
            return "attributes.total 类型非法";
        }

        BigDecimal amt = order.getAmount();
        if (amt == null) {
            return "订单金额为空";
        }
        long expectedMinor = amt.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
        if (Math.abs(lsTotalMinor - expectedMinor) > 1) {
            return "实付 minor 与订单不一致: lsTotal=" + lsTotalMinor + " expectedMinor=" + expectedMinor;
        }
        return null;
    }

    /**
     * 校验 X-Signature：HMAC-SHA256(rawBody, secret) 的十六进制，与官方文档一致
     */
    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
        String secret = properties.getWebhookSigningSecret();
        if (!StringUtils.hasText(secret)) {
            log.warn("lemon-squeezy.webhook-signing-secret 未配置，拒绝 Webhook");
            return false;
        }
        if (!StringUtils.hasText(signatureHeader) || rawBody == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String hex = signatureHeader.trim().toLowerCase();
            byte[] got = HexFormat.of().parseHex(hex);
            return MessageDigest.isEqual(expected, got);
        } catch (IllegalArgumentException e) {
            log.warn("Lemon Squeezy Webhook 签名解析失败");
            return false;
        } catch (Exception e) {
            log.warn("Lemon Squeezy Webhook 签名校验异常: {}", e.getMessage());
            return false;
        }
    }
}
