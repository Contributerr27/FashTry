package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户点数记录（支持过期扣除）
 */
@Data
@TableName("user_credit_records")
public class UserCreditRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("order_id")
    private Long orderId;

    @TableField("package_id")
    private Integer packageId;

    /** 获得点数 */
    private Integer credits;

    /** 剩余未消耗点数 */
    @TableField("credits_remaining")
    private Integer creditsRemaining;

    /** 过期时间，null 表示永久 */
    @TableField("expire_at")
    private LocalDateTime expireAt;

    /** 0-有效, 1-已过期 */
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("expired_at")
    private LocalDateTime expiredAt;
}
