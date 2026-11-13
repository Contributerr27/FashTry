package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 虚拟试穿任务表
 * <p>
 * 对应 fash_try.sql 中 tryon_tasks 表
 * 状态流转：0 排队 → 1 生成中 → 2 完成 / 3 失败
 * </p>
 */
@Data
@TableName("tryon_tasks")
public class TryonTask {

    /** 分布式唯一ID(雪花ID)，由 SnowflakeIdGenerator 在提交时生成，Consumer 入库时需保留该 ID，故用 INPUT 避免被覆盖 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** Fashn.AI返回的任务ID */
    @TableField("prediction_id")
    private String predictionId;

    /** FASHN | GEMINI；Gemini 试穿走本表由 {@link com.fashtry.backend.task.GeminiTryonTaskScheduler} 处理 */
    private String provider;

    /** 服装类别: tops, bottoms, one-pieces */
    private String category;

    /** 原始模特图URL */
    @TableField("model_url")
    private String modelUrl;

    /** 衣服图URL */
    @TableField("garment_url")
    private String garmentUrl;

    /** FASHN 生成结果 URL（原始 CDN 链接） */
    @TableField("result_url")
    private String resultUrl;

    /** 转存到腾讯云 COS 的结果图 URL（优先使用） */
    @TableField("result_cos_url")
    private String resultCosUrl;

    /** 结果图缩略图 URL */
    @TableField("thumb_url")
    private String thumbUrl;



    /** 状态: 0-排队, 1-生成中, 2-完成, 3-失败 */
    private Integer status;

    /** 失败原因描述 */
    @TableField("error_msg")
    private String errorMsg;

    /** 进入生成中的时间（与 status=1 对齐，供进度与耗时） */
    @TableField("processing_started_at")
    private LocalDateTime processingStartedAt;

    /** 从 processing_started_at 到结束的毫秒数 */
    @TableField("generation_duration_ms")
    private Long generationDurationMs;

    /** GEMINI：instruction、modelId、aspectRatio、imageSize 的 JSON（模特/服装 URL 见 model_url、garment_url） */
    @TableField("gemini_payload_json")
    private String geminiPayloadJson;

    /** 本次消耗点数 */
    @TableField("consume_credits")
    private Integer consumeCredits;

    /** 成功扣点时间；NULL 表示尚未扣点 */
    @TableField("credits_deducted_at")
    private LocalDateTime creditsDeductedAt;

    /** 任务创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 任务完成/失败时间 */
    @TableField("completed_at")
    private LocalDateTime completedAt;
}
