package com.fashtry.backend.service.business.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fashtry.backend.mapper.OrderMapper;
import com.fashtry.backend.mapper.RechargePackageMapper;
import com.fashtry.backend.mapper.UserCreditRecordMapper;
import com.fashtry.backend.bean.db.Order;
import com.fashtry.backend.bean.db.RechargePackage;
import com.fashtry.backend.bean.db.ReferralRewardConfig;
import com.fashtry.backend.bean.db.UserCreditRecord;
import com.fashtry.backend.bean.db.UserInvitation;
import com.fashtry.backend.bean.db.UserReferralCode;
import com.fashtry.backend.bean.db.UserWallet;
import com.fashtry.backend.mapper.ReferralRewardConfigMapper;
import com.fashtry.backend.mapper.UserInvitationMapper;
import com.fashtry.backend.mapper.UserReferralCodeMapper;
import com.fashtry.backend.mapper.UserWalletMapper;
import com.fashtry.backend.service.business.PayRechargeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 支付充值业务实现
 * <p>
 * 支付成功后：更新订单 status=1、paid_at，增加钱包点数，创建 user_credit_record
 * </p>
 */
@Slf4j
@Service
public class PayRechargeServiceImpl implements PayRechargeService {

    /** 订单状态：1-已支付 */
    private static final int ORDER_STATUS_PAID = 1;
    /** 点数记录状态：0-有效 */
    private static final int CREDIT_STATUS_ACTIVE = 0;

    /** 邀请状态：2-已完成首单（首充特惠），3-已返利 */
    private static final int INVITE_STATUS_FIRST_ORDER = 2;
    private static final int INVITE_STATUS_REWARDED = 3;
    /** 返利类型：1-固定点数，2-充值比例 */
    private static final int REWARD_TYPE_FIXED = 1;
    private static final int REWARD_TYPE_PERCENT = 2;

    private final OrderMapper orderMapper;
    private final RechargePackageMapper packageMapper;
    private final UserWalletMapper walletMapper;
    private final UserCreditRecordMapper creditRecordMapper;
    private final UserInvitationMapper userInvitationMapper;
    private final ReferralRewardConfigMapper referralRewardConfigMapper;
    private final UserReferralCodeMapper userReferralCodeMapper;

    public PayRechargeServiceImpl(OrderMapper orderMapper,
                                   RechargePackageMapper packageMapper,
                                   UserWalletMapper walletMapper,
                                   UserCreditRecordMapper creditRecordMapper,
                                   UserInvitationMapper userInvitationMapper,
                                   ReferralRewardConfigMapper referralRewardConfigMapper,
                                   UserReferralCodeMapper userReferralCodeMapper) {
        this.orderMapper = orderMapper;
        this.packageMapper = packageMapper;
        this.walletMapper = walletMapper;
        this.creditRecordMapper = creditRecordMapper;
        this.userInvitationMapper = userInvitationMapper;
        this.referralRewardConfigMapper = referralRewardConfigMapper;
        this.userReferralCodeMapper = userReferralCodeMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentSuccess(Order order, String thirdPartyNo) {
        if (order.getStatus() != null && order.getStatus() == ORDER_STATUS_PAID) {
            log.info("订单已处理过: orderNo={}", order.getOrderNo());
            return;
        }

        Long userId = order.getUserId();
        Integer packageId = order.getPackageId();
        Integer credits = order.getCreditsBought();
        BigDecimal amount = order.getAmount();

        if (credits == null || credits <= 0) {
            log.warn("订单点数异常: orderNo={}, credits={}", order.getOrderNo(), credits);
            return;
        }

        RechargePackage pkg = packageId != null ? packageMapper.selectById(packageId) : null;
        LocalDateTime expireAt = null;
        if (pkg != null && pkg.getExpirationDays() != null && pkg.getExpirationDays() > 0) {
            expireAt = LocalDateTime.now().plusDays(pkg.getExpirationDays());
        }

        UserWallet wallet = walletMapper.selectById(userId);
        if (wallet == null) {
            wallet = new UserWallet();
            wallet.setUserId(userId);
            wallet.setTotalCredits(0);
            wallet.setFrozenCredits(0);
            wallet.setTotalRecharged(0);
            wallet.setTotalRechargedAmount(BigDecimal.ZERO);
            wallet.setVersion(0);
            wallet.setUpdatedAt(LocalDateTime.now());
            walletMapper.insert(wallet);
        }

        int newTotal = Optional.ofNullable(wallet.getTotalCredits()).orElse(0) + credits;
        int newRecharged = Optional.ofNullable(wallet.getTotalRecharged()).orElse(0) + credits;
        BigDecimal newAmount = Optional.ofNullable(wallet.getTotalRechargedAmount()).orElse(BigDecimal.ZERO)
                .add(amount != null ? amount : BigDecimal.ZERO);

        wallet.setTotalCredits(newTotal);
        wallet.setTotalRecharged(newRecharged);
        wallet.setTotalRechargedAmount(newAmount);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletMapper.updateById(wallet);

        UserCreditRecord record = new UserCreditRecord();
        record.setUserId(userId);
        record.setOrderId(order.getId());
        record.setPackageId(packageId);
        record.setCredits(credits);
        record.setCreditsRemaining(credits);
        record.setExpireAt(expireAt);
        record.setStatus(CREDIT_STATUS_ACTIVE);
        record.setCreatedAt(LocalDateTime.now());
        creditRecordMapper.insert(record);

        order.setStatus(ORDER_STATUS_PAID);
        order.setThirdPartyNo(thirdPartyNo);
        order.setPaidAt(LocalDateTime.now());
        orderMapper.updateById(order);

        processReferralReward(order, pkg, amount, credits);

        log.info("充值成功: orderNo={}, userId={}, credits={}", order.getOrderNo(), userId, credits);
    }

    /**
     * 邀请返利：仅当充值非首充特惠时才返利。
     * 同一好友只返利一次，后续该好友再充值不再返利（status=3 已返利后直接跳过）。
     */
    private void processReferralReward(Order order, RechargePackage pkg, BigDecimal amount, Integer credits) {
        Long inviteeId = order.getUserId();
        UserInvitation inv = userInvitationMapper.selectOne(
                new LambdaQueryWrapper<UserInvitation>().eq(UserInvitation::getInviteeId, inviteeId));
        if (inv == null || inv.getInviterId() == null) return;
        if (Integer.valueOf(INVITE_STATUS_REWARDED).equals(inv.getStatus())) return;

        boolean isFirstPurchaseOnly = pkg != null && Boolean.TRUE.equals(pkg.getFirstPurchaseOnly());
        if (isFirstPurchaseOnly) {
            inv.setStatus(INVITE_STATUS_FIRST_ORDER);
            userInvitationMapper.updateById(inv);
            log.info("邀请好友购买首充特惠，不返利: inviteeId={}", inviteeId);
            return;
        }

        ReferralRewardConfig config = referralRewardConfigMapper.selectOne(
                new LambdaQueryWrapper<ReferralRewardConfig>().eq(ReferralRewardConfig::getIsActive, 1).last("LIMIT 1"));
        if (config == null) return;

        int rewardCredits = 0;
        if (REWARD_TYPE_FIXED == config.getRewardType()) {
            rewardCredits = config.getRewardValue() != null ? config.getRewardValue().intValue() : 0;
        } else if (REWARD_TYPE_PERCENT == config.getRewardType()) {
            if (amount != null && config.getRewardValue() != null) {
                rewardCredits = amount.multiply(config.getRewardValue()).divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.DOWN).intValue();
            }
        }
        if (rewardCredits <= 0) return;

        Long inviterId = inv.getInviterId();
        UserWallet inviterWallet = walletMapper.selectById(inviterId);
        if (inviterWallet == null) return;

        int updated = userInvitationMapper.update(null,
                new LambdaUpdateWrapper<UserInvitation>()
                        .eq(UserInvitation::getId, inv.getId())
                        .ne(UserInvitation::getStatus, INVITE_STATUS_REWARDED)
                        .set(UserInvitation::getStatus, INVITE_STATUS_REWARDED));
        if (updated == 0) {
            log.info("邀请好友已返利过，跳过: inviteeId={}", inviteeId);
            return;
        }

        int newTotal = java.util.Optional.ofNullable(inviterWallet.getTotalCredits()).orElse(0) + rewardCredits;
        inviterWallet.setTotalCredits(newTotal);
        inviterWallet.setUpdatedAt(LocalDateTime.now());
        walletMapper.updateById(inviterWallet);

        UserCreditRecord inviterRecord = new UserCreditRecord();
        inviterRecord.setUserId(inviterId);
        inviterRecord.setOrderId(null);
        inviterRecord.setPackageId(null);
        inviterRecord.setCredits(rewardCredits);
        inviterRecord.setCreditsRemaining(rewardCredits);
        inviterRecord.setExpireAt(null);
        inviterRecord.setStatus(CREDIT_STATUS_ACTIVE);
        inviterRecord.setCreatedAt(LocalDateTime.now());
        creditRecordMapper.insert(inviterRecord);

        UserReferralCode inviterRef = userReferralCodeMapper.selectById(inviterId);
        if (inviterRef != null) {
            inviterRef.setTotalRewards(java.util.Optional.ofNullable(inviterRef.getTotalRewards()).orElse(0) + rewardCredits);
            inviterRef.setUpdatedAt(LocalDateTime.now());
            userReferralCodeMapper.updateById(inviterRef);
        }

        log.info("邀请返利: inviterId={}, inviteeId={}, rewardCredits={}", inviterId, inviteeId, rewardCredits);
    }
}
