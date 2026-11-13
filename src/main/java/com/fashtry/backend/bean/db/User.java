package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表 (fash_try.sql - users)
 */
@Data
@TableName("users")
public class User {

    /** 雪花ID，由 SnowflakeIdGenerator 生成，入库时需保留故用 INPUT */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 用户昵称，默认 新用户 */
    private String nickname;

    /** 头像地址 */
    @TableField("avatar_url")
    private String avatarUrl;

    /** 手机号 */
    private String mobile;

    /** 备用邮箱 */
    private String email;

    /** 状态: 1-启用, 0-禁用 */
    private Integer status;

    /** 等级: 0-普通, 1-Pro, 2-企业 */
    @TableField("user_level")
    private Integer userLevel;

    /** 邀请码 */
    @TableField("invitation_code")
    private String invitationCode;

    /** 扫码进店来源品牌（brands.id） */
    @TableField("brand_id")
    private Long brandId;

    /** 注册来源：web / mp_weixin / app */
    @TableField("register_source")
    private String registerSource;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
