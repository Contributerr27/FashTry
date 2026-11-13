package com.fashtry.backend.service.business;

import com.fashtry.backend.bean.vo.RechargePackageVO;

import java.util.List;

/**
 * 充值套餐服务
 */
public interface RechargePackageService {

    /**
     * 获取可购买的套餐列表（含首充校验、时间校验）
     *
     * @param userId 当前用户 ID，null 表示未登录（首充套餐显示可购买）
     * @param locale 语言：zh-人民币，en-美元
     * @return 套餐列表，purchasable 表示当前用户是否可购买
     */
    List<RechargePackageVO> listPackages(Long userId, String locale);

    /**
     * 校验用户是否可购买指定套餐
     *
     * @param userId    用户 ID
     * @param packageId 套餐 ID
     * @return true 可购买
     */
    boolean canPurchase(Long userId, Integer packageId);
}
