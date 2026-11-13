package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 返利配置表 (fash_try.sql - referral_reward_configs)
 */
@Data
@TableName("referral_reward_configs")
public class ReferralRewardConfig {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 1-固定点数, 2-充值比例 */
    @TableField("reward_type")
    private Integer rewardType;

    /** 返利数值(点数或百分比) */
    @TableField("reward_value")
    private BigDecimal rewardValue;

    /** 是否启用 */
    @TableField("is_active")
    private Integer isActive;

    /** 策略描述: 如 邀请人获充值金额10%的点数 */
    private String description;

    /** 英文策略描述 */
    @TableField("description_en")
    private String descriptionEn;
}
