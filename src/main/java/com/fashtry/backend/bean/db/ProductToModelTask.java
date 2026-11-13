package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品图转模特图任务表
 * <p>
 * 对应 product_to_model_tasks 表
 * 状态流转：0 排队 → 1 生成中 → 2 完成 / 3 失败
 * </p>
 */
@Data
@TableName("product_to_model_tasks")
public class ProductToModelTask {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("prediction_id")
    private String predictionId;

    @TableField("product_url")
    private String productUrl;

    @TableField("model_url")
    private String modelUrl;

    @TableField("image_prompt_url")
    private String imagePromptUrl;

    @TableField("face_reference_url")
    private String faceReferenceUrl;

    @TableField("background_reference_url")
    private String backgroundReferenceUrl;

    @TableField("face_reference_mode")
    private String faceReferenceMode;

    private String prompt;

    @TableField("aspect_ratio")
    private String aspectRatio;

    private String resolution;

    private Integer seed;

    @TableField("num_images")
    private Integer numImages;

    @TableField("output_format")
    private String outputFormat;

    @TableField("result_url")
    private String resultUrl;

    @TableField("result_urls")
    private String resultUrls;

    @TableField("result_cos_url")
    private String resultCosUrl;

    @TableField("result_cos_urls")
    private String resultCosUrls;

    @TableField("thumb_url")
    private String thumbUrl;

    private Integer status;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("consume_credits")
    private Integer consumeCredits;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;
}
