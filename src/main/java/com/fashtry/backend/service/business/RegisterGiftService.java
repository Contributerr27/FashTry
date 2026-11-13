package com.fashtry.backend.service.business;

import com.fashtry.backend.bean.db.RegisterGiftDaily;
import com.fashtry.backend.bean.db.RegisterGiftPolicy;
import com.fashtry.backend.bean.vo.RegisterGiftSettingsVO;
import com.fashtry.backend.common.config.AuthProperties;
import com.fashtry.backend.mapper.RegisterGiftDailyMapper;
import com.fashtry.backend.mapper.RegisterGiftPolicyMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 注册赠送：每人额度受每天总额度限制，后台可配。
 */
@Service
@Slf4j
public class RegisterGiftService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int POLICY_ID = 1;

    private final RegisterGiftPolicyMapper policyMapper;
    private final RegisterGiftDailyMapper dailyMapper;
    private final AuthProperties authProperties;

    public RegisterGiftService(
            RegisterGiftPolicyMapper policyMapper,
            RegisterGiftDailyMapper dailyMapper,
            AuthProperties authProperties) {
        this.policyMapper = policyMapper;
        this.dailyMapper = dailyMapper;
        this.authProperties = authProperties;
    }

    /**
     * 为新用户占用当日额度。总额度不足时返回 0，用户仍可注册。
     */
    @Transactional(rollbackFor = Exception.class)
    public int claimForNewUser() {
        try {
            RegisterGiftPolicy policy = ensurePolicy();
            int perUser = nz(policy.getPerUserCredits());
            int dailyCap = nz(policy.getDailyTotalCredits());
            if (perUser <= 0 || dailyCap <= 0) {
                return 0;
            }
            LocalDate today = LocalDate.now(ZONE);
            dailyMapper.insertIgnore(today);
            RegisterGiftDaily daily = dailyMapper.selectForUpdate(today);
            int used = daily == null ? 0 : nz(daily.getUsedCredits());
            int remaining = Math.max(0, dailyCap - used);
            int grant = Math.min(perUser, remaining);
            if (grant <= 0) {
                log.info("注册赠送额度已用尽: date={}, used={}, cap={}, perUser={}", today, used, dailyCap, perUser);
                return 0;
            }
            if (daily == null) {
                daily = new RegisterGiftDaily();
                daily.setGiftDate(today);
                daily.setUsedCredits(grant);
                daily.setUpdatedAt(LocalDateTime.now(ZONE));
                dailyMapper.insert(daily);
            } else {
                daily.setUsedCredits(used + grant);
                daily.setUpdatedAt(LocalDateTime.now(ZONE));
                dailyMapper.updateById(daily);
            }
            log.info("注册赠送占用: date={}, grant={}, used={}/{}", today, grant, used + grant, dailyCap);
            return grant;
        } catch (DataAccessException e) {
            int fallback = nz(authProperties.getFreeTrialCredits());
            log.error("注册赠送额度扣减失败，回退配置文件额度 {}: {}", fallback, e.getMessage());
            return fallback;
        }
    }

    public RegisterGiftSettingsVO settings() {
        RegisterGiftPolicy policy = ensurePolicy();
        int perUser = nz(policy.getPerUserCredits());
        int dailyCap = nz(policy.getDailyTotalCredits());
        LocalDate today = LocalDate.now(ZONE);
        RegisterGiftDaily daily = dailyMapper.selectById(today);
        int used = daily == null ? 0 : nz(daily.getUsedCredits());
        return RegisterGiftSettingsVO.builder()
                .perUserCredits(perUser)
                .dailyTotalCredits(dailyCap)
                .todayUsedCredits(used)
                .todayRemainingCredits(Math.max(0, dailyCap - used))
                .today(today.toString())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePolicy(int perUserCredits, int dailyTotalCredits) {
        ensurePolicy();
        RegisterGiftPolicy row = new RegisterGiftPolicy();
        row.setId(POLICY_ID);
        row.setPerUserCredits(perUserCredits);
        row.setDailyTotalCredits(dailyTotalCredits);
        row.setUpdatedAt(LocalDateTime.now(ZONE));
        policyMapper.updateById(row);
    }

    private RegisterGiftPolicy ensurePolicy() {
        int perUser = nz(authProperties.getFreeTrialCredits());
        if (perUser <= 0) {
            perUser = 1;
        }
        int dailyTotal = nz(authProperties.getDailyGiftTotalCredits());
        if (dailyTotal <= 0) {
            dailyTotal = 20;
        }
        policyMapper.insertIgnore(perUser, dailyTotal);
        RegisterGiftPolicy row = policyMapper.selectById(POLICY_ID);
        if (row != null) {
            return row;
        }
        row = new RegisterGiftPolicy();
        row.setId(POLICY_ID);
        row.setPerUserCredits(perUser);
        row.setDailyTotalCredits(dailyTotal);
        row.setUpdatedAt(LocalDateTime.now(ZONE));
        policyMapper.insert(row);
        return row;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
