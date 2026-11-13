package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户邀请表 (fash_try.sql - user_invitations)
 */
@Data
@TableName("user_invitations")
public class UserInvitation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 邀请人ID */
    @TableField("inviter_id")
    private Long inviterId;

    /** 被邀请人ID */
    @TableField("invitee_id")
    private Long inviteeId;

    /** 使用的邀请码 */
    @TableField("invitation_code")
    private String invitationCode;

    /** 状态: 1-已注册, 2-已完成首单, 3-已返利 */
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
