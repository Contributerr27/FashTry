package com.fashtry.backend.pay;

import com.fashtry.backend.common.config.NowPaymentsProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * NOWPayments 创建支付（USDT TRC20 / BSC 等）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NowPaymentsService {

    /** CNY（元）→ USDT 近似：与常见定价习惯一致，实际链上按 NOWPayments 汇率结算 */
    private static final BigDecimal CNY_TO_USDT = new BigDecimal("0.14");
    private static final int USDT_AMOUNT_SCALE = 8;

    /** 应付 USDT（与 computePayPriceAmount 一致）低于该值时仅允许 BSC，不允许 TRON */
    private static final BigDecimal USDT_TRON_MIN_AMOUNT = new BigDecimal("2");

    private final NowPaymentsProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public boolean isConfigured() {
        return props.isConfigured();
    }

    public String payCurrencyForChannel(String payChannel) {
        return props.resolvePayCurrency(payChannel);
    }

    /**
     * 与创建 NOWPayments 订单时一致的 USDT 数量（含最低额兜底），供前端展示「需支付多少 USDT」。
     */
    public BigDecimal computePayPriceAmount(BigDecimal businessAmount, String businessCurrency) {
        if (businessAmount == null) {
            return BigDecimal.ZERO.setScale(USDT_AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal priceAmount = "USD".equalsIgnoreCase(businessCurrency)
                ? businessAmount.setScale(USDT_AMOUNT_SCALE, RoundingMode.HALF_UP)
                : businessAmount.multiply(CNY_TO_USDT).setScale(USDT_AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal minUsdt = props.getMinAmountUsd();
        if (minUsdt != null && minUsdt.signum() > 0 && priceAmount.compareTo(minUsdt) < 0) {
            priceAmount = minUsdt.setScale(USDT_AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        return priceAmount;
    }

    /**
     * 是否允许使用 TRON 网络（USDT 应付 ≥ 2，与美元等价理解一致）。
     */
    public boolean isUsdtTronNetworkAllowed(BigDecimal businessAmount, String businessCurrency) {
        if (businessAmount == null || businessAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return computePayPriceAmount(businessAmount, businessCurrency).compareTo(USDT_TRON_MIN_AMOUNT) >= 0;
    }

    /**
     * @param businessAmount 业务侧金额（CNY 或 USD，与 businessCurrency 一致）
     * @param businessCurrency CNY 或 USD
     * @param payCurrency NOWPayments 的 pay_currency，如 usdttrc20、usdtbsc
     * @return invoice_url 或 pay_address，失败返回 null
     */
    @SuppressWarnings("unchecked")
    public String createPayment(String orderNo, BigDecimal businessAmount, String businessCurrency,
                                String subject, String notifyUrl, String payCurrency) {
        if (!props.isConfigured()) {
            log.warn("NOWPayments 未配置 api-key");
            return null;
        }

        String currencyToUse = payCurrency != null && !payCurrency.isBlank()
                ? payCurrency.trim().toLowerCase(Locale.ROOT)
                : props.getPayCurrencyTron();

        BigDecimal priceAmount = computePayPriceAmount(businessAmount, businessCurrency);

        if (log.isDebugEnabled()) {
            log.debug("NOWPayments create: orderNo={}, price_amount={}, pay_currency={}", orderNo,
                    priceAmount.toPlainString(), currencyToUse);
        }

        String url = props.getBaseUrl().replaceAll("/$", "") + "/payment";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", props.getApiKey());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("price_amount", priceAmount);
        body.put("price_currency", currencyToUse);
        body.put("order_id", orderNo);
        body.put("order_description", subject != null ? subject : "FashTry 充值");
        body.put("ipn_callback_url", notifyUrl);
        body.put("pay_currency", currencyToUse);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> respBody = resp.getBody();
            if (respBody == null || resp.getStatusCode().value() >= 400) {
                log.error("NOWPayments 创建支付失败: status={}, body={}", resp.getStatusCode(), respBody);
                return null;
            }

            String invoiceUrl = (String) respBody.get("invoice_url");
            String payAddress = (String) respBody.get("pay_address");
            String codeUrl = invoiceUrl;
            if (codeUrl == null || codeUrl.isBlank()) {
                codeUrl = payAddress;
            }
            if (codeUrl == null || codeUrl.isBlank()) {
                log.error("NOWPayments 响应中无 invoice_url 或 pay_address: {}", respBody);
                return null;
            }
            return codeUrl;
        } catch (HttpClientErrorException e) {
            String errBody = e.getResponseBodyAsString();
            log.error("NOWPayments 创建支付失败: status={}, body={}", e.getStatusCode(), errBody);
            return null;
        } catch (ResourceAccessException e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            log.error("NOWPayments 创建支付失败(网络): orderNo={}, root={}", orderNo, root.toString());
            return null;
        } catch (Exception e) {
            log.error("NOWPayments 创建支付异常: orderNo={}", orderNo, e);
            return null;
        }
    }

    /**
     * 校验 IPN：对排序后的 JSON 与 ipn_secret 做 HMAC-SHA512，与 x-nowpayments-sig 比较
     */
    public boolean verifyIpnSignature(String rawBody, String signatureHeader) {
        if (!props.isVerifyIpn()) {
            log.warn("NOWPayments IPN 签名校验已关闭（verify-ipn=false），仅用于开发调试");
            return true;
        }
        String secret = props.getIpnSecret();
        if (secret == null || secret.isBlank() || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(rawBody, Map.class);
            Object sorted = sortRecursively(map);
            String canonical = objectMapper.writeValueAsString(sorted);

            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(keySpec);
            byte[] h = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(h);
            String expected = signatureHeader.trim().toLowerCase(Locale.ROOT);
            return computed.equalsIgnoreCase(expected);
        } catch (JsonProcessingException e) {
            log.warn("NOWPayments IPN 正文非合法 JSON");
            return false;
        } catch (Exception e) {
            log.error("NOWPayments IPN 签名校验异常", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Object sortRecursively(Object o) {
        if (o instanceof Map) {
            TreeMap<String, Object> tm = new TreeMap<>();
            Map<String, Object> m = (Map<String, Object>) o;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                tm.put(e.getKey(), sortRecursively(e.getValue()));
            }
            return tm;
        }
        if (o instanceof List) {
            List<Object> out = new ArrayList<>();
            for (Object x : (List<?>) o) {
                out.add(sortRecursively(x));
            }
            return out;
        }
        return o;
    }
}
