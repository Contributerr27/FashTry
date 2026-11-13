package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand_users")
public class BrandUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("brand_id")
    private Long brandId;

    private String username;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("display_name")
    private String displayName;

    private String mobile;

    private String role;

    private Integer status;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
