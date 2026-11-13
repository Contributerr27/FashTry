package com.fashtry.backend.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fashtry.backend.bean.db.Order;
import com.fashtry.backend.common.config.PayProperties;
import com.fashtry.backend.mapper.OrderMapper;
import com.fashtry.backend.pay.AlipayService;
import com.fashtry.backend.pay.WxPayNativeService;
import com.fashtry.backend.bean.dto.PayQueryResult;
import com.fashtry.backend.service.business.PayRechargeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付状态轮询定时任务
 * <p>
 * 每 30 秒查询待支付订单，调用微信/支付宝查询接口确认是否已支付，
 * 支付成功则执行充值业务（增加点数、更新订单状态）。
 * 超过支付时限（默认 10 分钟）的待支付订单不再查询，直接标记为支付超时关闭。
 * </p>
 */
@Component
public class PayStatusPollTask {

    private static final Logger log = LoggerFactory.getLogger(PayStatusPollTask.class);

    /** 订单状态：0-待支付 */
    private static final int ORDER_STATUS_PENDING = 0;
    /** 订单状态：2-支付超时关闭 */
    private static final int ORDER_STATUS_TIMEOUT = 2;
    /** 每轮最多处理的待支付订单数 */
    private static final int BATCH_LIMIT = 20;

    private final OrderMapper orderMapper;
    private final PayRechargeService payRechargeService;
    private final PayProperties payProperties;

    @Autowired(required = false)
    private WxPayNativeService wxPayNativeService;

    @Autowired(required = false)
    private AlipayService alipayService;

    public PayStatusPollTask(OrderMapper orderMapper, PayRechargeService payRechargeService, PayProperties payProperties) {
        this.orderMapper = orderMapper;
        this.payRechargeService = payRechargeService;
        this.payProperties = payProperties;
    }

    /**
     * 轮询待支付订单
     * 每 30 秒执行一次
     */
    @Scheduled(fixedRate = 3000)
    public void pollPaymentStatus() {
        LocalDateTime now = LocalDateTime.now();
        int timeoutMinutes = payProperties.getPaymentTimeoutMinutes() > 0
                ? payProperties.getPaymentTimeoutMinutes()
                : 10;
        LocalDateTime expireThreshold = now.minusMinutes(timeoutMinutes);

        // 1. 将已过期的待支付订单批量更新为支付超时
        LambdaUpdateWrapper<Order> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Order::getStatus, ORDER_STATUS_PENDING)
                .lt(Order::getCreatedAt, expireThreshold)
                .ne(Order::getPayChannel, "lemonsqueezy")
                .set(Order::getStatus, ORDER_STATUS_TIMEOUT);
        int updated = orderMapper.update(null, updateWrapper);
        if (updated > 0) {
            log.info("支付超时关闭订单: 共 {} 笔", updated);
        }

        // 2. 仅查询未过期的待支付订单
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, ORDER_STATUS_PENDING)
                .ge(Order::getCreatedAt, expireThreshold)
                .in(Order::getPayChannel, "wxpay", "alipay")
                .last("LIMIT " + BATCH_LIMIT);

        List<Order> orders = orderMapper.selectList(wrapper);
        if (orders.isEmpty()) {
            return;
        }

        for (Order order : orders) {
            try {
                String payChannel = order.getPayChannel();
                String orderNo = order.getOrderNo();
                PayQueryResult result = null;

                if ("wxpay".equalsIgnoreCase(payChannel) && wxPayNativeService != null) {
                    result = wxPayNativeService.queryOrder(orderNo);
                } else if ("alipay".equalsIgnoreCase(payChannel) && alipayService != null) {
                    result = alipayService.queryOrder(orderNo);
                }

                if (result == null || !result.isPaid()) {
                    continue;
                }

                String thirdPartyNo = result.getTransactionId() != null
                        ? result.getTransactionId()
                        : payChannel + "-" + orderNo;
                payRechargeService.handlePaymentSuccess(order, thirdPartyNo);
                log.info("轮询支付成功: orderNo={}, userId={}, transactionId={}", orderNo, order.getUserId(), thirdPartyNo);
            } catch (Exception e) {
                log.error("轮询支付异常: orderNo={}", order.getOrderNo(), e);
            }
        }
    }
}
