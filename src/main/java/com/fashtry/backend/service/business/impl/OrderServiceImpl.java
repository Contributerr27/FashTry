package com.fashtry.backend.service.business.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fashtry.backend.bean.db.Order;
import com.fashtry.backend.bean.db.RechargePackage;
import com.fashtry.backend.bean.dto.CreateOrderRequest;
import com.fashtry.backend.bean.vo.OrderCreateVO;
import com.fashtry.backend.bean.vo.WxPayMiniSignVO;
import com.fashtry.backend.common.config.PayProperties;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.pay.LemonSqueezyService;
import com.fashtry.backend.pay.NowPaymentsService;
import com.fashtry.backend.pay.WxMiniProgramService;
import com.fashtry.backend.pay.WxPayNativeService;
import com.fashtry.backend.service.business.RechargePackageService;
import com.fashtry.backend.bean.vo.OrderRecordVO;
import com.fashtry.backend.bean.vo.OrderStatusVO;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.OrderMapper;
import com.fashtry.backend.mapper.RechargePackageMapper;
import com.fashtry.backend.pay.AlipayService;
import com.fashtry.backend.pay.PayPalService;
import com.fashtry.backend.service.business.OrderService;
import com.fashtry.backend.service.business.PayRechargeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

/**
 * 订单服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final int ORDER_STATUS_PENDING = 0;
    private static final int ORDER_STATUS_PAID = 1;
    private static final int ORDER_STATUS_TIMEOUT = 2;
    private static final String CURRENCY_CNY = "CNY";

    private final OrderMapper orderMapper;
    private final RechargePackageMapper packageMapper;
    private final RechargePackageService rechargePackageService;
    private final PayProperties payProperties;
    private static final int PAYMENT_TIMEOUT_MINUTES_DEFAULT = 10; // 支付超时默认分钟数
    private final WxPayNativeService wxPayNativeService;
    private final AlipayService alipayService;
    private final PayPalService payPalService;
    private final NowPaymentsService nowPaymentsService;
    private final PayRechargeService payRechargeService;
    private final WxMiniProgramService wxMiniProgramService;
    private final LemonSqueezyService lemonSqueezyService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateVO createRechargeOrder(Long userId, CreateOrderRequest req, String localeHeader) {
        Integer packageId = req.getPackageId();
        String payChannel = req.getPayChannel();
        String locale = StringUtils.hasText(req.getLocale()) ? req.getLocale() : localeHeader;

        // 1. 校验套餐可购买
        if (!rechargePackageService.canPurchase(userId, packageId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该套餐不可购买");
        }

        RechargePackage pkg = packageMapper.selectById(packageId);
        boolean useUsd = "en".equalsIgnoreCase(locale)
                && pkg.getPriceUsd() != null
                && pkg.getPriceUsd().compareTo(BigDecimal.ZERO) > 0
                && ("paypal".equalsIgnoreCase(payChannel)
                        || "usdt_tron".equalsIgnoreCase(payChannel)
                        || "usdt_bsc".equalsIgnoreCase(payChannel));
        BigDecimal amount = useUsd ? pkg.getPriceUsd() : pkg.getPrice();
        String currency = useUsd ? "USD" : CURRENCY_CNY;

        if (pkg == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "套餐不存在或价格异常");
        }

        // 2. 创建订单
        String orderNo = generateOrderNo();
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setPackageId(packageId);
        order.setAmount(amount);
        order.setCurrency(currency);
        order.setCreditsBought(pkg.getCredits());
        order.setPayChannel(normalizePayChannel(payChannel));
        order.setStatus(ORDER_STATUS_PENDING);
        order.setDescription(pkg.getName());
        order.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(order);

        // 3. 调用支付获取二维码或小程序调起参数
        String codeUrl = null;
        WxPayMiniSignVO wxPaySign = null;
        String subject = "充值-" + pkg.getName();

        switch (order.getPayChannel().toLowerCase()) {
            case "wxpay" -> {
                String notifyUrl = payProperties.getNotifyBaseUrl() + "/api/pay/notify/wxpay";
                String wxCode = req.getWxLoginCode();
                if (StringUtils.hasText(wxCode)) {
                    String mpAppId = wxMiniProgramService.resolveMiniProgramAppId();
                    if (!StringUtils.hasText(mpAppId)) {
                        throw new BusinessException(ResultCode.PARAM_ERROR, "请配置 wx.mini-program.app-id（须与小程序 manifest 的 appid 一致）或 wx.pay.app-id");
                    }
                    String openId = wxMiniProgramService.exchangeCodeForOpenId(wxCode.trim());
                    if (openId == null) {
                        throw new BusinessException(ResultCode.PARAM_ERROR, "小程序支付需配置 wx.mini-program.secret，或登录态失效请重试");
                    }
                    order.setOpenId(openId);
                    String prepayId = wxPayNativeService.createJsapiOrder(orderNo, pkg.getPrice(), subject, notifyUrl, openId, mpAppId);
                    if (prepayId == null) {
                        throw new BusinessException(ResultCode.SYSTEM_ERROR, "微信支付下单失败");
                    }
                    wxPaySign = wxPayNativeService.buildMiniProgramPayParams(prepayId, mpAppId);
                } else {
                    codeUrl = wxPayNativeService.createNativeOrder(orderNo, pkg.getPrice(), subject, notifyUrl);
                    if (codeUrl == null) {
                        throw new BusinessException(ResultCode.SYSTEM_ERROR, "微信支付未配置");
                    }
                }
            }
            case "alipay" -> {
                String notifyUrl = payProperties.getNotifyBaseUrl() + "/api/pay/notify/alipay";
                codeUrl = alipayService.preCreate(orderNo, pkg.getPrice(), subject, notifyUrl);
                if (codeUrl == null) {
                    throw new BusinessException(ResultCode.SYSTEM_ERROR, "支付宝未配置");
                }
            }
            case "paypal" -> {
                String approvalUrl = useUsd
                        ? payPalService.createOrderWithUSD(orderNo, pkg.getPriceUsd(), subject)
                        : payPalService.createOrder(orderNo, pkg.getPrice(), subject);
                if (approvalUrl == null) {
                    throw new BusinessException(ResultCode.SYSTEM_ERROR, "PayPal 创建订单失败");
                }
                codeUrl = approvalUrl;
            }
            case "usdt_tron", "usdt_bsc" -> {
                if (!nowPaymentsService.isConfigured()) {
                    throw new BusinessException(ResultCode.SYSTEM_ERROR, "USDT 支付未配置");
                }
                if ("usdt_tron".equalsIgnoreCase(order.getPayChannel())
                        && !nowPaymentsService.isUsdtTronNetworkAllowed(amount, currency)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "该金额请使用 USDT (BSC) 支付");
                }
                String notifyUrl = payProperties.getNotifyBaseUrl() + "/api/pay/notify/nowpayments";
                String npCurrency = nowPaymentsService.payCurrencyForChannel(order.getPayChannel());
                codeUrl = nowPaymentsService.createPayment(orderNo, amount, currency, subject, notifyUrl, npCurrency);
                if (codeUrl == null || codeUrl.isBlank()) {
                    throw new BusinessException(ResultCode.SYSTEM_ERROR, "USDT 支付创建失败，请稍后重试或联系客服");
                }
            }
            case "lemonsqueezy" -> {
                if (!lemonSqueezyService.isConfiguredForCheckout(packageId)) {
                    throw new BusinessException(ResultCode.SYSTEM_ERROR, "Lemon Squeezy 未配置或未映射该套餐");
                }
                codeUrl = lemonSqueezyService.buildHostedCheckoutUrl(packageId, orderNo);
            }
            default -> throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的支付方式");
        }

        // 4. 更新订单 code_url
        order.setCodeUrl(codeUrl);
        orderMapper.updateById(order);

        int timeoutMinutes = payProperties.getPaymentTimeoutMinutes() > 0
                ? payProperties.getPaymentTimeoutMinutes()
                : PAYMENT_TIMEOUT_MINUTES_DEFAULT;
        LocalDateTime expireAt = order.getCreatedAt().plusMinutes(timeoutMinutes);

        BigDecimal cryptoPayAmount = null;
        String cryptoPayCurrency = null;
        String ch = order.getPayChannel() != null ? order.getPayChannel().toLowerCase() : "";
        if ("usdt_tron".equals(ch) || "usdt_bsc".equals(ch)) {
            cryptoPayAmount = nowPaymentsService.computePayPriceAmount(amount, currency);
            cryptoPayCurrency = "USDT";
        }

        return OrderCreateVO.builder()
                .orderId(order.getId())
                .orderNo(orderNo)
                .payChannel(order.getPayChannel())
                .amount(order.getAmount())
                .currency(currency)
                .cryptoPayAmount(cryptoPayAmount)
                .cryptoPayCurrency(cryptoPayCurrency)
                .credits(order.getCreditsBought())
                .codeUrl(codeUrl)
                .wxPay(wxPaySign)
                .status(ORDER_STATUS_PENDING)
                .expireAt(expireAt)
                .build();
    }

    private String generateOrderNo() {
        return "R" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @Override
    public OrderStatusVO getOrderStatus(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无权查询该订单");
        }
        int status = order.getStatus() != null ? order.getStatus() : ORDER_STATUS_PENDING;
        String statusText = switch (status) {
            case ORDER_STATUS_PAID -> "已支付";
            case ORDER_STATUS_TIMEOUT -> "支付已超时";
            default -> "待支付";
        };
        LocalDateTime expireAt = null;
        if (status == ORDER_STATUS_PENDING) {
            int timeoutMinutes = payProperties.getPaymentTimeoutMinutes() > 0
                    ? payProperties.getPaymentTimeoutMinutes()
                    : PAYMENT_TIMEOUT_MINUTES_DEFAULT;
            expireAt = order.getCreatedAt().plusMinutes(timeoutMinutes);
        }
        return OrderStatusVO.builder()
                .orderId(order.getId())
                .status(status)
                .statusText(statusText)
                .expireAt(expireAt)
                .build();
    }

    @Override
    public IPage<OrderRecordVO> listMyOrders(Long userId, int page, int size) {
        Page<Order> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreatedAt);
        IPage<Order> orderPage = orderMapper.selectPage(pageParam, wrapper);
        List<OrderRecordVO> records = orderPage.getRecords().stream()
                .map(this::toOrderRecordVO)
                .collect(Collectors.toList());
        Page<OrderRecordVO> result = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        result.setRecords(records);
        return result;
    }

    private OrderRecordVO toOrderRecordVO(Order o) {
        int status = o.getStatus() != null ? o.getStatus() : ORDER_STATUS_PENDING;
        String statusText = switch (status) {
            case ORDER_STATUS_PAID -> "已支付";
            case ORDER_STATUS_TIMEOUT -> "支付已超时";
            case 3 -> "已退款";
            case 4 -> "支付失败";
            default -> "待支付";
        };
        return OrderRecordVO.builder()
                .id(o.getId())
                .orderNo(o.getOrderNo())
                .amount(o.getAmount())
                .currency(o.getCurrency())
                .creditsBought(o.getCreditsBought())
                .payChannel(o.getPayChannel())
                .status(status)
                .statusText(statusText)
                .description(o.getDescription())
                .createdAt(o.getCreatedAt())
                .paidAt(o.getPaidAt())
                .build();
    }

    private String normalizePayChannel(String payChannel) {
        if (payChannel == null) return "";
        return payChannel.trim().toLowerCase();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean capturePayPalOrder(Long userId, String orderNo, String paypalOrderId) {
        if (orderNo == null || orderNo.isBlank() || paypalOrderId == null || paypalOrderId.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null || !order.getUserId().equals(userId)) {
            return false;
        }
        if (order.getStatus() != null && order.getStatus() == ORDER_STATUS_PAID) {
            return true;
        }
        if (!"paypal".equalsIgnoreCase(order.getPayChannel())) {
            return false;
        }
        if (!payPalService.captureOrder(paypalOrderId)) {
            return false;
        }
        payRechargeService.handlePaymentSuccess(order, paypalOrderId);
        return true;
    }
}
