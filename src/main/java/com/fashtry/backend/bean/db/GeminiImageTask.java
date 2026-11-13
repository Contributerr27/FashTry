package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Gemini 异步生成任务，对应表 {@code gemini_image_tasks}
 */
@Data
@TableName("gemini_image_tasks")
public class GeminiImageTask {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("task_type")
    private String taskType;

    /** 业务提交用户（可选；仅部分入口写入） */
    @TableField("user_id")
    private Long userId;

    /** 成功完成后扣除点数（提交时写入；失败不扣） */
    @TableField("consume_credits")
    private Integer consumeCredits;

    /** 成功扣点时间；NULL 表示尚未扣点 */
    @TableField("credits_deducted_at")
    private LocalDateTime creditsDeductedAt;

    /** 0-待处理 1-执行中 2-成功 3-失败 */
    private Integer status;

    @TableField("payload_json")
    private String payloadJson;

    @TableField("result_json")
    private String resultJson;

    @TableField("error_msg")
    private String errorMsg;

    /** 定时任务 claim 成功、开始拉图+Gemini+COS 的时间 */
    @TableField("processing_started_at")
    private LocalDateTime processingStartedAt;

    /** 从 processing_started_at 到结束（成功或失败）的毫秒数，供前端按预计总时长算百分比 */
    @TableField("generation_duration_ms")
    private Long generationDurationMs;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;
}
