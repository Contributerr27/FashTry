package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户授权表 (fash_try.sql - user_auths)
 */
@Data
@TableName("user_auths")
public class UserAuth {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联users.id */
    @TableField("user_id")
    private Long userId;

    /** 登录类型: phone, wechat, google, email */
    @TableField("identity_type")
    private String identityType;

    /** 手机号或OpenID/Sub */
    private String identifier;

    /** 密码或Token(OAuth无需) */
    private String credential;
}
