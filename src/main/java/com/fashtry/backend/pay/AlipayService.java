package com.fashtry.backend.pay;

import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.fashtry.backend.bean.dto.PayQueryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 支付宝当面付（扫码），使用官方 alipay-sdk-java
 */
@Slf4j
@Service
public class AlipayService {

    @Value("${alipay.server-url:https://openapi.alipay.com/gateway.do}")
    private String serverUrl;

    @Value("${alipay.app-id:}")
    private String appId;

    @Value("${alipay.private-key:}")
    private String privateKey;

    @Value("${alipay.alipay-public-key:}")
    private String alipayPublicKey;

    private AlipayClient alipayClient;

    private synchronized AlipayClient getClient() throws Exception {
        if (alipayClient == null) {
            AlipayConfig config = new AlipayConfig();
            config.setServerUrl(serverUrl);
            config.setAppId(appId);
            config.setPrivateKey(privateKey);
            config.setFormat("json");
            config.setAlipayPublicKey(alipayPublicKey);
            config.setCharset("UTF-8");
            config.setSignType("RSA2");
            alipayClient = new DefaultAlipayClient(config);
        }
        return alipayClient;
    }

    /**
     * 创建当面付订单，返回 qr_code 供前端生成二维码
     */
    public String preCreate(String orderNo, BigDecimal amount, String subject, String notifyUrl) {
        if (appId == null || appId.isBlank() || privateKey == null || privateKey.isBlank()) {
            log.warn("支付宝未配置，跳过");
            return null;
        }
        try {
            AlipayClient client = getClient();
            AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
            if (notifyUrl != null && !notifyUrl.isBlank()) {
                request.setNotifyUrl(notifyUrl);
            }
            AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
            model.setQrCodeTimeoutExpress("10m");
            model.setSubject(subject);
            model.setProductCode("FACE_TO_FACE_PAYMENT");
            model.setOutTradeNo(orderNo);
            model.setTotalAmount(amount.toString());
            request.setBizModel(model);

            AlipayTradePrecreateResponse response = client.execute(request);
            if (response.isSuccess()) {
                log.info("支付宝 precreate 成功: orderNo={}", orderNo);
                return response.getQrCode();
            }
            log.error("支付宝 precreate 失败: code={}, subCode={}, subMsg={}", response.getCode(), response.getSubCode(), response.getSubMsg());
            throw new RuntimeException("支付宝下单失败: " + (response.getSubMsg() != null ? response.getSubMsg() : response.getMsg()));
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            log.error("支付宝 precreate 异常: orderNo={}", orderNo, e);
            throw new RuntimeException("支付宝下单失败: " + e.getMessage());
        }
    }

    /**
     * 查询订单支付状态
     */
    public PayQueryResult queryOrder(String orderNo) {
        if (appId == null || appId.isBlank() || privateKey == null || privateKey.isBlank()) {
            return null;
        }
        try {
            AlipayClient client = getClient();
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            AlipayTradeQueryModel model = new AlipayTradeQueryModel();
            model.setOutTradeNo(orderNo);
            request.setBizModel(model);

            AlipayTradeQueryResponse response = client.execute(request);
            if (response.isSuccess()) {
                String tradeStatus = response.getTradeStatus();
                boolean paid = "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
                return new PayQueryResult(paid, response.getTradeNo());
            }
            // ACQ.TRADE_NOT_EXIST: 订单在支付宝侧不存在，可能是该订单实际为微信支付创建，或 precreate 未成功
            if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
                log.debug("支付宝订单不存在(可能为微信订单或未创建): orderNo={}", orderNo);
            } else {
                log.warn("支付宝查询订单失败: orderNo={}, code={}, subCode={}, subMsg={}", orderNo, response.getCode(), response.getSubCode(), response.getSubMsg());
            }
            return null;
        } catch (Exception e) {
            log.error("支付宝查询订单异常: orderNo={}", orderNo, e);
            return null;
        }
    }
}
