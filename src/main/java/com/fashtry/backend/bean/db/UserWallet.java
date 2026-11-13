package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户钱包表 (fash_try.sql - user_wallets)
 */
@Data
@TableName("user_wallets")
public class UserWallet {

    /** 关联users.id，主键 */
    @TableId(value = "user_id")
    private Long userId;

    /** 当前可用点数 */
    @TableField("total_credits")
    private Integer totalCredits;

    /** 任务进行中冻结的点数 */
    @TableField("frozen_credits")
    private Integer frozenCredits;

    /** 累计充值获得的点数 */
    @TableField("total_recharged")
    private Integer totalRecharged;

    /** 累计充值金额(元) */
    @TableField("total_recharged_amount")
    private java.math.BigDecimal totalRechargedAmount;

    /** 乐观锁版本 */
    private Integer version;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
