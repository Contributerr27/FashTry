package com.fashtry.backend.service.business;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fashtry.backend.bean.dto.CreateOrderRequest;
import com.fashtry.backend.bean.vo.OrderCreateVO;
import com.fashtry.backend.bean.vo.OrderRecordVO;
import com.fashtry.backend.bean.vo.OrderStatusVO;

/**
 * 订单服务
 */
public interface OrderService {

    /**
     * 创建充值订单并获取支付二维码
     *
     * @param userId    用户 ID
     * @param packageId  套餐 ID
     * @param req        下单参数（含可选 wxLoginCode 用于小程序微信支付）
     * @param localeHeader 请求头 X-Locale
     * @return 订单信息及支付信息
     */
    OrderCreateVO createRechargeOrder(Long userId, CreateOrderRequest req, String localeHeader);

    /**
     * 查询订单支付状态（用于前端轮询）
     *
     * @param userId  当前用户 ID
     * @param orderId 订单 ID
     * @return 订单状态
     */
    OrderStatusVO getOrderStatus(Long userId, Long orderId);

    /**
     * 分页查询当前用户的充值订单记录
     */
    IPage<OrderRecordVO> listMyOrders(Long userId, int page, int size);

    /**
     * PayPal 支付回调：捕获订单并完成充值
     *
     * @param userId        当前用户 ID
     * @param orderNo       我方订单号
     * @param paypalOrderId PayPal 订单 ID（token）
     * @return 是否成功
     */
    boolean capturePayPalOrder(Long userId, String orderNo, String paypalOrderId);
}
