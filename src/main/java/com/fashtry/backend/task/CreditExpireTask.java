package com.fashtry.backend.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fashtry.backend.bean.db.UserCreditRecord;
import com.fashtry.backend.bean.db.UserWallet;
import com.fashtry.backend.mapper.UserCreditRecordMapper;
import com.fashtry.backend.mapper.UserWalletMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 套餐包过期扣除点数定时任务
 * <p>
 * 每日执行，查询 expire_at 已过期的有效记录，
 * 按用户汇总需扣除点数，从钱包扣减并标记记录为已过期
 * </p>
 */
@Component
public class CreditExpireTask {

    private static final Logger log = LoggerFactory.getLogger(CreditExpireTask.class);

    /** 状态：0-有效, 1-已过期 */
    private static final int STATUS_ACTIVE = 0;
    private static final int STATUS_EXPIRED = 1;

    private final UserCreditRecordMapper creditRecordMapper;
    private final UserWalletMapper walletMapper;

    public CreditExpireTask(UserCreditRecordMapper creditRecordMapper, UserWalletMapper walletMapper) {
        this.creditRecordMapper = creditRecordMapper;
        this.walletMapper = walletMapper;
    }

    /**
     * 每日 0 点 5 分执行过期扣除
     */
    @Scheduled(cron = "0 5 0 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void expireCredits() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<UserCreditRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCreditRecord::getStatus, STATUS_ACTIVE)
                .isNotNull(UserCreditRecord::getExpireAt)
                .lt(UserCreditRecord::getExpireAt, now);

        List<UserCreditRecord> records = creditRecordMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return;
        }

        // 按用户汇总需扣除点数（取 credits_remaining，用户可能已消耗部分）
        Map<Long, Integer> userDeductMap = records.stream()
                .collect(Collectors.groupingBy(UserCreditRecord::getUserId,
                        Collectors.summingInt(UserCreditRecord::getCreditsRemaining)));

        for (Map.Entry<Long, Integer> entry : userDeductMap.entrySet()) {
            Long userId = entry.getKey();
            int toDeduct = entry.getValue();
            if (toDeduct <= 0) {
                continue;
            }
            UserWallet wallet = walletMapper.selectById(userId);
            if (wallet == null) {
                continue;
            }
            int current = wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
            int actualDeduct = Math.min(toDeduct, current);
            if (actualDeduct > 0) {
                wallet.setTotalCredits(current - actualDeduct);
                walletMapper.updateById(wallet);
                log.info("套餐过期扣除点数: userId={}, deduct={}", userId, actualDeduct);
            }
        }

        // 标记记录为已过期
        LambdaUpdateWrapper<UserCreditRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserCreditRecord::getStatus, STATUS_ACTIVE)
                .isNotNull(UserCreditRecord::getExpireAt)
                .lt(UserCreditRecord::getExpireAt, now)
                .set(UserCreditRecord::getStatus, STATUS_EXPIRED)
                .set(UserCreditRecord::getExpiredAt, now)
                .set(UserCreditRecord::getCreditsRemaining, 0);
        creditRecordMapper.update(null, updateWrapper);

        log.info("套餐过期处理完成: 共 {} 条记录", records.size());
    }
}
