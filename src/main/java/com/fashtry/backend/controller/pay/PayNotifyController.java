package com.fashtry.backend.controller.pay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fashtry.backend.bean.db.Order;
import com.fashtry.backend.mapper.OrderMapper;
import com.fashtry.backend.pay.LemonSqueezyService;
import com.fashtry.backend.pay.NowPaymentsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashtry.backend.service.business.PayRechargeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * 第三方支付异步通知（无需登录）
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PayNotifyController {

    private final NowPaymentsService nowPaymentsService;
    private final LemonSqueezyService lemonSqueezyService;
    private final ObjectMapper objectMapper;
    private final OrderMapper orderMapper;
    private final PayRechargeService payRechargeService;

    /**
     * NOWPayments IPN：支付完成后回调，校验签名后完成充值
     */
    @PostMapping(value = "/api/pay/notify/nowpayments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> nowpaymentsIpn(HttpServletRequest request, @RequestBody String body) {
        String sig = request.getHeader("x-nowpayments-sig");
        if (!nowPaymentsService.verifyIpnSignature(body, sig)) {
            log.warn("NOWPayments IPN 签名校验失败");
            return ResponseEntity.status(401).body("invalid signature");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String orderNo = text(root, "order_id");
            if (orderNo == null || orderNo.isBlank()) {
                return ResponseEntity.badRequest().body("missing order_id");
            }
            String status = text(root, "payment_status");
            if (!isPaidNowPaymentsStatus(status)) {
                return ResponseEntity.ok("OK");
            }
            String paymentId = text(root, "payment_id");

            Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
            if (order == null) {
                log.warn("NOWPayments IPN 订单不存在: orderNo={}", orderNo);
                return ResponseEntity.ok("OK");
            }
            String thirdParty = paymentId != null && !paymentId.isBlank() ? paymentId : "nowp-" + orderNo;
            payRechargeService.handlePaymentSuccess(order, thirdParty);
            log.info("NOWPayments IPN 充值完成: orderNo={}, paymentId={}", orderNo, thirdParty);
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("NOWPayments IPN 处理异常", e);
            return ResponseEntity.internalServerError().body("error");
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return n != null && !n.isNull() ? n.asText() : null;
    }

    private static boolean isPaidNowPaymentsStatus(String status) {
        if (status == null) {
            return false;
        }
        String s = status.trim().toLowerCase(Locale.ROOT);
        return "finished".equals(s) || "confirmed".equals(s);
    }

    /**
     * Lemon Squeezy Webhook：校验 X-Signature 后，对已支付订单执行充值。
     * <p>在 LS Dashboard 配置 URL：{@code https://fashtry.com/api/pay/notify/lemonsqueezy}（与 app.pay.notify-base-url 一致）</p>
     */
    @PostMapping(value = "/api/pay/notify/lemonsqueezy", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> lemonSqueezyWebhook(HttpServletRequest request, @RequestBody String body) {
        String sig = request.getHeader("X-Signature");
        if (!lemonSqueezyService.verifyWebhookSignature(body, sig)) {
            log.warn("Lemon Squeezy Webhook 签名校验失败");
            return ResponseEntity.status(401).body("invalid signature");
        }
        String eventName = request.getHeader("X-Event-Name");
        if (eventName == null
                || (!eventName.equalsIgnoreCase("order_created") && !eventName.equalsIgnoreCase("order_updated"))) {
            return ResponseEntity.ok("OK");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode attrs = root.path("data").path("attributes");
            String st = attrs.path("status").asText("");
            if (!"paid".equalsIgnoreCase(st)) {
                return ResponseEntity.ok("OK");
            }
            JsonNode orderNoNode = root.path("meta").path("custom_data").path("order_no");
            if (!orderNoNode.isTextual()) {
                log.warn("Lemon Squeezy Webhook 无 meta.custom_data.order_no，跳过");
                return ResponseEntity.ok("OK");
            }
            String orderNo = orderNoNode.asText().trim();
            if (orderNo.isEmpty()) {
                return ResponseEntity.ok("OK");
            }
            Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
            if (order == null) {
                log.warn("Lemon Squeezy Webhook 订单不存在: orderNo={}", orderNo);
                return ResponseEntity.ok("OK");
            }
            if (!"lemonsqueezy".equalsIgnoreCase(order.getPayChannel())) {
                log.warn("Lemon Squeezy Webhook 订单渠道不符: orderNo={}, payChannel={}", orderNo, order.getPayChannel());
                return ResponseEntity.ok("OK");
            }
            String mismatch = lemonSqueezyService.validateWebhookPaidMatchesOrder(root, order);
            if (mismatch != null) {
                log.warn("Lemon Squeezy Webhook 实付与本地订单不一致，拒绝加款: orderNo={}, reason={}", orderNo, mismatch);
                return ResponseEntity.ok("OK");
            }
            String lsOrderId = root.path("data").path("id").asText("");
            String thirdParty = StringUtils.hasText(lsOrderId) ? "ls-" + lsOrderId : "ls-" + orderNo;
            payRechargeService.handlePaymentSuccess(order, thirdParty);
            log.info("Lemon Squeezy Webhook 充值完成: orderNo={}, event={}, lsOrderId={}", orderNo, eventName, lsOrderId);
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Lemon Squeezy Webhook 处理异常", e);
            return ResponseEntity.internalServerError().body("error");
        }
    }
}
