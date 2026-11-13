package com.fashtry.backend.service.business;

import com.fashtry.backend.bean.db.Order;

/**
 * 支付充值业务服务
 * <p>
 * 支付成功后：更新订单状态、增加用户点数、创建点数记录
 * </p>
 */
public interface PayRechargeService {

    /**
     * 处理支付成功，执行充值业务
     *
     * @param order 已支付订单
     * @param thirdPartyNo 第三方支付单号（微信/支付宝交易号）
     */
    void handlePaymentSuccess(Order order, String thirdPartyNo);
}
