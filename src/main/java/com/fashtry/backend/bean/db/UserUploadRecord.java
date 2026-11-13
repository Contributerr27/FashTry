package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户上传记录表
 */
@Data
@TableName("user_upload_records")
public class UserUploadRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("file_url")
    private String fileUrl;

    @TableField("file_path")
    private String filePath;

    /** business-业务文件, other-其他 */
    private String category;

    @TableField("file_size")
    private Long fileSize;

    @TableField("original_name")
    private String originalName;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
