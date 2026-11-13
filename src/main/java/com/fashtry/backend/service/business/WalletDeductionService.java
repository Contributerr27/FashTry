package com.fashtry.backend.service.business;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fashtry.backend.bean.db.UserWallet;
import com.fashtry.backend.mapper.UserWalletMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 钱包扣点：原子 UPDATE，避免「读-改-写」竞态导致成功任务未扣点。
 */
@Service
public class WalletDeductionService {

    private static final Logger log = LoggerFactory.getLogger(WalletDeductionService.class);

    private final UserWalletMapper userWalletMapper;

    public WalletDeductionService(UserWalletMapper userWalletMapper) {
        this.userWalletMapper = userWalletMapper;
    }

    /**
     * 提交任务时预占点数（frozen_credits），防止排队期间重复提交超支。
     *
     * @return true 已预占；false 可用余额不足
     */
    public boolean freezeCredits(Long userId, int credits, String scene, Long refTaskId) {
        if (userId == null || userId <= 0 || credits <= 0) {
            return false;
        }
        LambdaUpdateWrapper<UserWallet> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserWallet::getUserId, userId)
                .apply("total_credits - IFNULL(frozen_credits, 0) >= {0}", credits)
                .setSql("frozen_credits = IFNULL(frozen_credits, 0) + " + credits)
                .set(UserWallet::getUpdatedAt, LocalDateTime.now());
        if (userWalletMapper.update(null, uw) == 1) {
            log.info("预占点数 scene={} userId={} taskId={} freeze={}", scene, userId, refTaskId, credits);
            return true;
        }
        log.warn("预占失败 scene={} userId={} taskId={} need={}", scene, userId, refTaskId, credits);
        return false;
    }

    /** 任务成功：从 total 扣减并释放等量预占 */
    public boolean settleFrozenCredits(Long userId, int credits, String scene, Long refTaskId) {
        if (userId == null || userId <= 0 || credits <= 0) {
            return false;
        }
        LambdaUpdateWrapper<UserWallet> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserWallet::getUserId, userId)
                .ge(UserWallet::getTotalCredits, credits)
                .apply("IFNULL(frozen_credits, 0) >= {0}", credits)
                .setSql(
                        "total_credits = total_credits - "
                                + credits
                                + ", frozen_credits = IFNULL(frozen_credits, 0) - "
                                + credits)
                .set(UserWallet::getUpdatedAt, LocalDateTime.now());
        if (userWalletMapper.update(null, uw) == 1) {
            log.info("结算扣点 scene={} userId={} taskId={} deduct={}", scene, userId, refTaskId, credits);
            return true;
        }
        return false;
    }

    /** 任务失败/取消：仅释放预占，不扣 total */
    public void releaseFrozenCredits(Long userId, int credits, String scene, Long refTaskId) {
        if (userId == null || userId <= 0 || credits <= 0) {
            return;
        }
        LambdaUpdateWrapper<UserWallet> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserWallet::getUserId, userId)
                .apply("IFNULL(frozen_credits, 0) >= {0}", credits)
                .setSql("frozen_credits = IFNULL(frozen_credits, 0) - " + credits)
                .set(UserWallet::getUpdatedAt, LocalDateTime.now());
        if (userWalletMapper.update(null, uw) == 1) {
            log.info("释放预占 scene={} userId={} taskId={} release={}", scene, userId, refTaskId, credits);
        }
    }

    /**
     * 无预占时的兜底扣点（历史任务）
     *
     * @return true 已扣减；false 用户不存在或余额不足
     */
    public boolean deductIfEnough(Long userId, int credits, String scene, Long refTaskId) {
        if (userId == null || credits <= 0) {
            return false;
        }
        LambdaUpdateWrapper<UserWallet> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserWallet::getUserId, userId)
                .ge(UserWallet::getTotalCredits, credits)
                .setSql("total_credits = total_credits - " + credits)
                .set(UserWallet::getUpdatedAt, LocalDateTime.now());
        int rows = userWalletMapper.update(null, uw);
        if (rows == 1) {
            log.info("扣点成功 scene={} userId={} taskId={} deduct={}", scene, userId, refTaskId, credits);
            return true;
        }
        UserWallet wallet = userWalletMapper.selectById(userId);
        int have = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
        log.warn("扣点失败 scene={} userId={} taskId={} need={} have={}", scene, userId, refTaskId, credits, have);
        return false;
    }
}
