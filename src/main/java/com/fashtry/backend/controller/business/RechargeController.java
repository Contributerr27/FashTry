package com.fashtry.backend.controller.business;

import com.fashtry.backend.bean.dto.CreateOrderRequest;
import com.fashtry.backend.bean.vo.OrderCreateVO;
import com.fashtry.backend.bean.vo.RechargePackageVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.business.OrderService;
import com.fashtry.backend.service.business.RechargePackageService;
import com.fashtry.backend.bean.dto.PayPalCaptureRequest;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fashtry.backend.bean.vo.OrderRecordVO;
import com.fashtry.backend.bean.vo.OrderStatusVO;
import com.fashtry.backend.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 充值相关接口
 */
@RestController
@RequestMapping("/api/recharge")
public class RechargeController {

    private final RechargePackageService rechargePackageService;
    private final OrderService orderService;

    public RechargeController(RechargePackageService rechargePackageService, OrderService orderService) {
        this.rechargePackageService = rechargePackageService;
        this.orderService = orderService;
    }

    /**
     * 获取充值套餐列表（无需登录）
     * 已登录时含首充校验：尝鲜包(9.9)仅对从未支付过的用户显示可购买
     */
    @GetMapping("/packages")
    public Result<List<RechargePackageVO>> listPackages(
            @RequestHeader(value = "X-Locale", required = false, defaultValue = "zh") String locale) {
        Long userId = SecurityUtils.getCurrentUserIdOrNull();
        List<RechargePackageVO> list = rechargePackageService.listPackages(userId, locale);
        return Result.success(list);
    }

    /**
     * 校验是否可购买指定套餐（下单前调用）
     */
    @GetMapping("/packages/can-purchase")
    public Result<Boolean> canPurchase(@RequestParam Integer packageId) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean can = rechargePackageService.canPurchase(userId, packageId);
        return Result.success(can);
    }

    /**
     * 创建充值订单，生成支付二维码或跳转链接
     * 支付方式: wxpay, alipay, paypal, usdt_tron, usdt_bsc（NOWPayments，跳转 invoice）
     */
    @PostMapping("/order")
    public Result<OrderCreateVO> createOrder(
            @Valid @RequestBody CreateOrderRequest req,
            @RequestHeader(value = "X-Locale", required = false, defaultValue = "zh") String locale) {
        Long userId = SecurityUtils.getCurrentUserId();
        OrderCreateVO vo = orderService.createRechargeOrder(userId, req, locale);
        return Result.success(vo);
    }

    /**
     * 查询订单支付状态（前端轮询，用于展示支付结果和倒计时）
     */
    @GetMapping("/order/{orderId}/status")
    public Result<OrderStatusVO> getOrderStatus(@PathVariable Long orderId) {
        Long userId = SecurityUtils.getCurrentUserId();
        OrderStatusVO vo = orderService.getOrderStatus(userId, orderId);
        return Result.success(vo);
    }

    /**
     * PayPal 支付完成回调：捕获订单并完成充值
     * 用户从 PayPal 跳转回 recharge.html 后，前端携带 token 调用此接口
     */
    @PostMapping("/paypal/capture")
    public Result<Boolean> capturePayPal(@Valid @RequestBody PayPalCaptureRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean ok = orderService.capturePayPalOrder(userId, req.getOrderNo(), req.getToken());
        return Result.success(ok);
    }

    /**
     * 充值记录分页查询
     */
    @GetMapping("/orders")
    public Result<IPage<OrderRecordVO>> listMyOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        IPage<OrderRecordVO> data = orderService.listMyOrders(userId, page, size);
        return Result.success(data);
    }
}
