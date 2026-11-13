package com.fashtry.backend.controller.business;

import com.fashtry.backend.bean.db.ReferralRewardConfig;
import com.fashtry.backend.bean.db.User;
import com.fashtry.backend.bean.db.UserReferralCode;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.mapper.ReferralRewardConfigMapper;
import com.fashtry.backend.mapper.UserMapper;
import com.fashtry.backend.mapper.UserReferralCodeMapper;
import com.fashtry.backend.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 邀请奖励接口
 */
@RestController
@RequestMapping("/api/invite")
public class InviteController {

    private static final int REWARD_TYPE_FIXED = 1;
    private static final int REWARD_TYPE_PERCENT = 2;

    private final UserMapper userMapper;
    private final UserReferralCodeMapper userReferralCodeMapper;
    private final ReferralRewardConfigMapper referralRewardConfigMapper;

    public InviteController(UserMapper userMapper,
                            UserReferralCodeMapper userReferralCodeMapper,
                            ReferralRewardConfigMapper referralRewardConfigMapper) {
        this.userMapper = userMapper;
        this.userReferralCodeMapper = userReferralCodeMapper;
        this.referralRewardConfigMapper = referralRewardConfigMapper;
    }

    /**
     * 获取邀请信息：邀请码、邀请链接、累计邀请人数、累计返利、奖励规则描述
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> getInviteInfo(
            @RequestHeader(value = "X-Locale", required = false, defaultValue = "zh") String locale) {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.success(Map.of());
        }

        String code = user.getInvitationCode();
        UserReferralCode ref = userReferralCodeMapper.selectById(userId);
        int totalInvites = ref != null && ref.getTotalInvites() != null ? ref.getTotalInvites() : 0;
        int totalRewards = ref != null && ref.getTotalRewards() != null ? ref.getTotalRewards() : 0;

        ReferralRewardConfig config = referralRewardConfigMapper.selectOne(
                new LambdaQueryWrapper<ReferralRewardConfig>()
                        .eq(ReferralRewardConfig::getIsActive, 1)
                        .last("LIMIT 1"));

        boolean useEn = "en".equalsIgnoreCase(locale);
        String rewardRule = buildRewardRule(config, useEn);

        Map<String, Object> data = new HashMap<>();
        data.put("invitationCode", code != null ? code : "");
        data.put("totalInvites", totalInvites);
        data.put("totalRewards", totalRewards);
        data.put("rewardRule", rewardRule);
        return Result.success(data);
    }

    private String buildRewardRule(ReferralRewardConfig config, boolean useEn) {
        if (config == null) {
            return useEn ? "Invite friends to register. When they recharge (non-first-purchase), you get credits." : "邀请好友注册，好友充值（非首充特惠）后您将获得点数奖励";
        }
        if (useEn && config.getDescriptionEn() != null && !config.getDescriptionEn().isEmpty()) {
            return config.getDescriptionEn();
        }
        if (!useEn && config.getDescription() != null && !config.getDescription().isEmpty()) {
            return config.getDescription();
        }
        if (REWARD_TYPE_FIXED == config.getRewardType()) {
            int val = config.getRewardValue() != null ? config.getRewardValue().intValue() : 0;
            return useEn ? "Get " + val + " credits when friends register and recharge (non-first-purchase)" : "邀请好友注册，好友充值（非首充特惠）后您将获得 " + val + " 点数奖励";
        }
        if (REWARD_TYPE_PERCENT == config.getRewardType()) {
            int pct = config.getRewardValue() != null ? config.getRewardValue().intValue() : 0;
            return useEn ? "Get " + pct + "% of friend's recharge amount as credits" : "邀请好友注册，好友充值（非首充特惠）后您将获得充值金额 " + pct + "% 的点数奖励";
        }
        return useEn ? "Invite friends to register. When they recharge (non-first-purchase), you get credits." : "邀请好友注册，好友充值（非首充特惠）后您将获得点数奖励";
    }
}
