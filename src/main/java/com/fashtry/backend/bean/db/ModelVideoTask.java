package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Veo 图生视频任务（起始帧：试穿成片 / 局部改款成片 / 用户上传 business 图）
 */
@Data
@TableName("model_video_tasks")
public class ModelVideoTask {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("source_tryon_task_id")
    private Long sourceTryonTaskId;

    @TableField("source_image_edit_task_id")
    private Long sourceImageEditTaskId;

    private String prompt;

    @TableField("source_image_url")
    private String sourceImageUrl;

    @TableField("duration_seconds")
    private Integer durationSeconds;

    /** 720p / 1080p */
    private String resolution;

    /** 16:9 / 9:16 */
    @TableField("aspect_ratio")
    private String aspectRatio;

    /** 0 排队 1 生成中 2 完成 3 失败 */
    private Integer status;

    @TableField("result_video_url")
    private String resultVideoUrl;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("consume_credits")
    private Integer consumeCredits;

    @TableField("processing_started_at")
    private LocalDateTime processingStartedAt;

    @TableField("generation_duration_ms")
    private Long generationDurationMs;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;
}
