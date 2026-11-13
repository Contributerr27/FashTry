package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gemini_api_key")
public class GeminiApiKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("api_key")
    private String apiKey;

    private Integer enabled;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("cooldown_until")
    private LocalDateTime cooldownUntil;

    @TableField("last_error")
    private String lastError;

    @TableField("last_error_at")
    private LocalDateTime lastErrorAt;

    @TableField("last_ok_at")
    private LocalDateTime lastOkAt;

    @TableField("fail_count")
    private Integer failCount;

    @TableField("success_count")
    private Integer successCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
