package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户邀请码表 (fash_try.sql - user_referral_codes)
 */
@Data
@TableName("user_referral_codes")
public class UserReferralCode {

    /** 用户ID，主键 */
    @TableId(value = "user_id")
    private Long userId;

    /** 唯一邀请码 */
    @TableField("referral_code")
    private String referralCode;

    /** 累计邀请人数 */
    @TableField("total_invites")
    private Integer totalInvites;

    /** 累计获得的返利点数 */
    @TableField("total_rewards")
    private Integer totalRewards;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
