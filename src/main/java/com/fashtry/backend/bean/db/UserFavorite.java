package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_favorites")
public class UserFavorite {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("image_url")
    private String imageUrl;

    @TableField("url_hash")
    private String urlHash;

    @TableField("thumb_url")
    private String thumbUrl;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_task_id")
    private String sourceTaskId;

    private String title;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
