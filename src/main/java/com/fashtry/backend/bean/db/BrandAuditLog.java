package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand_audit_logs")
public class BrandAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("brand_id")
    private Long brandId;

    @TableField("actor_type")
    private String actorType;

    @TableField("actor_id")
    private Long actorId;

    private String action;

    private String detail;

    private String ip;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
