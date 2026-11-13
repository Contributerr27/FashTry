package com.fashtry.backend.service.business.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fashtry.backend.bean.db.Order;
import com.fashtry.backend.bean.db.RechargePackage;
import com.fashtry.backend.bean.vo.RechargePackageVO;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.OrderMapper;
import com.fashtry.backend.mapper.RechargePackageMapper;
import com.fashtry.backend.pay.NowPaymentsService;
import com.fashtry.backend.service.business.RechargePackageService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 充值套餐服务实现
 * - 首充套餐仅对从未支付过的用户展示/可购
 * - 支持套餐生效时间限制
 */
@Service
public class RechargePackageServiceImpl implements RechargePackageService {

    /** 订单状态：1-已支付 */
    private static final int ORDER_STATUS_PAID = 1;

    private final RechargePackageMapper packageMapper;
    private final OrderMapper orderMapper;
    private final NowPaymentsService nowPaymentsService;

    private static final Duration PACKAGES_CACHE_TTL = Duration.ofSeconds(30);
    private final AtomicReference<PackagesCache> packagesCacheRef = new AtomicReference<>(null);

    public RechargePackageServiceImpl(RechargePackageMapper packageMapper,
                                      OrderMapper orderMapper,
                                      NowPaymentsService nowPaymentsService) {
        this.packageMapper = packageMapper;
        this.orderMapper = orderMapper;
        this.nowPaymentsService = nowPaymentsService;
    }

    @Override
    public List<RechargePackageVO> listPackages(Long userId, String locale) {
        List<RechargePackage> list = getActivePackagesCached();
        boolean hasPaidOrder = userId != null && hasPaidOrder(userId);
        LocalDateTime now = LocalDateTime.now();
        boolean useUsd = "en".equalsIgnoreCase(locale);

        return list.stream()
                .map(pkg -> toVO(pkg, userId, hasPaidOrder, now, useUsd))
                .collect(Collectors.toList());
    }

    @Override
    public boolean canPurchase(Long userId, Integer packageId) {
        RechargePackage pkg = packageMapper.selectById(packageId);
        if (pkg == null || !Boolean.TRUE.equals(pkg.getActive())) {
            return false;
        }
        if (!isInEffect(pkg, LocalDateTime.now())) {
            return false;
        }
        if (Boolean.TRUE.equals(pkg.getFirstPurchaseOnly()) && hasPaidOrder(userId)) {
            return false;
        }
        return true;
    }

    private RechargePackageVO toVO(RechargePackage pkg, Long userId, boolean hasPaidOrder, LocalDateTime now, boolean useUsd) {
        boolean purchasable = true;
        if (!isInEffect(pkg, now)) {
            purchasable = false;
        } else if (Boolean.TRUE.equals(pkg.getFirstPurchaseOnly()) && hasPaidOrder) {
            purchasable = false;
        }

        String displayName = useUsd && pkg.getNameEn() != null ? pkg.getNameEn() : pkg.getName();
        String displayStrategy = useUsd && pkg.getStrategyTagEn() != null ? pkg.getStrategyTagEn() : pkg.getStrategyTag();
        RechargePackageVO.RechargePackageVOBuilder b = RechargePackageVO.builder()
                .id(pkg.getId())
                .code(pkg.getCode())
                .name(displayName)
                .credits(pkg.getCredits())
                .unitPrice(pkg.getUnitPrice())
                .firstPurchaseOnly(pkg.getFirstPurchaseOnly())
                .strategyTag(displayStrategy)
                .purchasable(purchasable);
        BigDecimal bizAmt;
        String bizCur;
        if (useUsd && pkg.getPriceUsd() != null) {
            b.price(pkg.getPriceUsd()).priceUsd(pkg.getPriceUsd()).currency("USD");
            bizAmt = pkg.getPriceUsd();
            bizCur = "USD";
        } else {
            b.price(pkg.getPrice()).priceUsd(pkg.getPriceUsd()).currency("CNY");
            bizAmt = pkg.getPrice();
            bizCur = "CNY";
        }
        boolean usdtTronOk = bizAmt != null
                && bizAmt.compareTo(BigDecimal.ZERO) > 0
                && nowPaymentsService.isUsdtTronNetworkAllowed(bizAmt, bizCur);
        b.usdtTronAvailable(usdtTronOk);
        return b.build();
    }

    private boolean isInEffect(RechargePackage pkg, LocalDateTime now) {
        if (pkg.getStartTime() != null && now.isBefore(pkg.getStartTime())) {
            return false;
        }
        if (pkg.getEndTime() != null && now.isAfter(pkg.getEndTime())) {
            return false;
        }
        return true;
    }

    private boolean hasPaidOrder(Long userId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        // 用 exists 查询替代 count，配合索引更快
        wrapper.select(Order::getId)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, ORDER_STATUS_PAID)
                .last("LIMIT 1");
        return orderMapper.selectOne(wrapper) != null;
    }

    private List<RechargePackage> getActivePackagesCached() {
        PackagesCache cached = packagesCacheRef.get();
        long nowMs = System.currentTimeMillis();
        if (cached != null && nowMs - cached.atMs <= PACKAGES_CACHE_TTL.toMillis() && cached.list != null) {
            return cached.list;
        }
        LambdaQueryWrapper<RechargePackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RechargePackage::getActive, true)
                .orderByAsc(RechargePackage::getSortOrder);
        // 仅取套餐展示需要字段（避免 select *）
        wrapper.select(
                RechargePackage::getId,
                RechargePackage::getCode,
                RechargePackage::getName,
                RechargePackage::getNameEn,
                RechargePackage::getCredits,
                RechargePackage::getUnitPrice,
                RechargePackage::getFirstPurchaseOnly,
                RechargePackage::getStrategyTag,
                RechargePackage::getStrategyTagEn,
                RechargePackage::getPrice,
                RechargePackage::getPriceUsd,
                RechargePackage::getSortOrder,
                RechargePackage::getStartTime,
                RechargePackage::getEndTime,
                RechargePackage::getActive
        );
        List<RechargePackage> list = packageMapper.selectList(wrapper);
        packagesCacheRef.set(new PackagesCache(list, nowMs));
        return list;
    }

    private record PackagesCache(List<RechargePackage> list, long atMs) {}
}
