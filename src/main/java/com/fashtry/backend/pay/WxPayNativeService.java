package com.fashtry.backend.pay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import com.fashtry.backend.bean.dto.PayQueryResult;
import com.fashtry.backend.bean.vo.WxPayMiniSignVO;

import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微信支付 Native 扫码（使用 wechatpay-apache-httpclient，平台证书模式）
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wx.pay", name = "mch-id")
public class WxPayNativeService {

    private static final String NATIVE_URL = "https://api.mch.weixin.qq.com/v3/pay/transactions/native";
    private static final String JSAPI_URL = "https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi";
    private static final String QUERY_URL = "https://api.mch.weixin.qq.com/v3/pay/transactions/out-trade-no/%s?mchid=%s";

    private final WxPayProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private CloseableHttpClient httpClient;
    private volatile PrivateKey cachedMerchantPrivateKey;

    private synchronized CloseableHttpClient getClient() throws Exception {
        if (httpClient == null) {
            InputStream privateKeyStream = loadStream(props.getPrivateKeyPath());
            InputStream platformCertStream = loadStream(props.getPlatformCertPath());
            PrivateKey merchantPrivateKey = PemUtil.loadPrivateKey(privateKeyStream);
            X509Certificate certificate = PemUtil.loadCertificate(platformCertStream);
            httpClient = WechatPayHttpClientBuilder.create()
                    .withMerchant(props.getMchId(), props.getCertSerialNo(), merchantPrivateKey)
                    .withWechatPay(Arrays.asList(certificate))
                    .build();
        }
        return httpClient;
    }

    private InputStream loadStream(String path) throws Exception {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("证书路径未配置");
        }
        if (path.startsWith("classpath:")) {
            String p = path.substring("classpath:".length()).trim();
            return new ClassPathResource(p).getInputStream();
        }
        return Files.newInputStream(Path.of(path));
    }

    /**
     * 创建 Native 支付订单，返回 code_url 供前端生成二维码
     */
    public String createNativeOrder(String orderNo, BigDecimal amount, String subject, String notifyUrl) {
        try {
            int totalFee = amount.multiply(BigDecimal.valueOf(100)).intValue();
            if (totalFee <= 0) {
                throw new IllegalArgumentException("支付金额必须大于0");
            }

            Map<String, Object> requestData = new HashMap<>();
            Map<String, Object> amountMap = new HashMap<>();
            amountMap.put("total", totalFee);
            amountMap.put("currency", "CNY");
            requestData.put("amount", amountMap);
            requestData.put("mchid", props.getMchId());
            requestData.put("description", subject);
            requestData.put("notify_url", notifyUrl);
            requestData.put("out_trade_no", orderNo);
            requestData.put("appid", props.getAppId());

            String requestBody = objectMapper.writeValueAsString(requestData);
            HttpPost httpPost = new HttpPost(NATIVE_URL);
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Accept", "application/json");

            CloseableHttpClient client = getClient();
            try (CloseableHttpResponse response = client.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (statusCode == 200) {
                    Map<?, ?> respMap = objectMapper.readValue(responseBody, Map.class);
                    return (String) respMap.get("code_url");
                }
                log.error("微信 Native 下单失败: status={}, body={}", statusCode, responseBody);
                throw new RuntimeException("微信支付下单失败: " + responseBody);
            }
        } catch (Exception e) {
            log.error("微信 Native 下单失败: orderNo={}", orderNo, e);
            throw new RuntimeException("微信支付下单失败: " + e.getMessage());
        }
    }

    /**
     * 小程序 JSAPI 下单，返回 prepay_id
     */
    /**
     * @param jsapiAppId 小程序 AppId，须与 jscode2session 及商户平台关联的小程序一致，不可与 openid 所属 appid 混用
     */
    public String createJsapiOrder(String orderNo, BigDecimal amount, String subject, String notifyUrl, String openId, String jsapiAppId) {
        try {
            int totalFee = amount.multiply(BigDecimal.valueOf(100)).intValue();
            if (totalFee <= 0) {
                throw new IllegalArgumentException("支付金额必须大于0");
            }
            if (openId == null || openId.isBlank()) {
                throw new IllegalArgumentException("openid 不能为空");
            }
            if (jsapiAppId == null || jsapiAppId.isBlank()) {
                throw new IllegalArgumentException("JSAPI appid 不能为空");
            }
            Map<String, Object> requestData = new HashMap<>();
            Map<String, Object> amountMap = new HashMap<>();
            amountMap.put("total", totalFee);
            amountMap.put("currency", "CNY");
            requestData.put("amount", amountMap);
            requestData.put("mchid", props.getMchId());
            requestData.put("description", subject);
            requestData.put("notify_url", notifyUrl);
            requestData.put("out_trade_no", orderNo);
            requestData.put("appid", jsapiAppId);
            Map<String, Object> payer = new HashMap<>();
            payer.put("openid", openId);
            requestData.put("payer", payer);

            String requestBody = objectMapper.writeValueAsString(requestData);
            HttpPost httpPost = new HttpPost(JSAPI_URL);
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Accept", "application/json");

            CloseableHttpClient client = getClient();
            try (CloseableHttpResponse response = client.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (statusCode == 200) {
                    Map<?, ?> respMap = objectMapper.readValue(responseBody, Map.class);
                    return (String) respMap.get("prepay_id");
                }
                log.error("微信 JSAPI 下单失败: status={}, body={}", statusCode, responseBody);
                throw new RuntimeException("微信支付下单失败: " + responseBody);
            }
        } catch (Exception e) {
            log.error("微信 JSAPI 下单失败: orderNo={}", orderNo, e);
            throw new RuntimeException("微信支付下单失败: " + e.getMessage());
        }
    }

    /**
     * 构造小程序调起支付所需签名（API v3）
     */
    /**
     * @param miniProgramAppId 调起支付签名字段须与下单 appid、openid 一致
     */
    public WxPayMiniSignVO buildMiniProgramPayParams(String prepayId, String miniProgramAppId) {
        try {
            if (miniProgramAppId == null || miniProgramAppId.isBlank()) {
                throw new IllegalArgumentException("小程序 appid 不能为空");
            }
            String appId = miniProgramAppId;
            String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);
            String nonceStr = UUID.randomUUID().toString().replace("-", "");
            String pkg = "prepay_id=" + prepayId;
            String message = appId + "\n" + timeStamp + "\n" + nonceStr + "\n" + pkg + "\n";
            String paySign = rsaSignV3(message);
            return WxPayMiniSignVO.builder()
                    .timeStamp(timeStamp)
                    .nonceStr(nonceStr)
                    .packageValue(pkg)
                    .signType("RSA")
                    .paySign(paySign)
                    .build();
        } catch (Exception e) {
            log.error("微信调起支付签名失败", e);
            throw new RuntimeException("微信支付签名失败: " + e.getMessage());
        }
    }

    private String rsaSignV3(String message) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(getOrLoadMerchantPrivateKey());
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private PrivateKey getOrLoadMerchantPrivateKey() throws Exception {
        PrivateKey k = cachedMerchantPrivateKey;
        if (k != null) {
            return k;
        }
        synchronized (this) {
            if (cachedMerchantPrivateKey == null) {
                try (InputStream privateKeyStream = loadStream(props.getPrivateKeyPath())) {
                    cachedMerchantPrivateKey = PemUtil.loadPrivateKey(privateKeyStream);
                }
            }
            return cachedMerchantPrivateKey;
        }
    }

    /**
     * 按商户订单号查询支付状态
     *
     * @param orderNo 商户订单号
     * @return 查询结果，失败返回 null
     */
    public PayQueryResult queryOrder(String orderNo) {
        try {
            String url = String.format(QUERY_URL, orderNo, props.getMchId());
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("Accept", "application/json");

            CloseableHttpClient client = getClient();
            try (CloseableHttpResponse response = client.execute(httpGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (statusCode == 200) {
                    Map<?, ?> respMap = objectMapper.readValue(responseBody, Map.class);
                    String tradeState = (String) respMap.get("trade_state");
                    String transactionId = (String) respMap.get("transaction_id");
                    boolean paid = "SUCCESS".equals(tradeState);
                    return new PayQueryResult(paid, transactionId);
                }
                log.warn("微信查询订单失败: orderNo={}, status={}, body={}", orderNo, statusCode, responseBody);
                return null;
            }
        } catch (Exception e) {
            log.error("微信查询订单异常: orderNo={}", orderNo, e);
            return null;
        }
    }
}
