package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("xhs_card_tasks")
public class XhsCardTask {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String topic;

    @TableField("selling_points")
    private String sellingPoints;

    private String audience;

    @TableField("brand_name")
    private String brandName;

    @TableField("template_style")
    private String templateStyle;

    @TableField("background_color")
    private String backgroundColor;

    @TableField("text_color")
    private String textColor;

    /** 用户上传的参考模板图 COS URL */
    @TableField("reference_image_url")
    private String referenceImageUrl;

    /** 0待处理 1执行中 2成功 3失败 */
    private Integer status;

    @TableField("consume_credits")
    private Integer consumeCredits;

    @TableField("result_json")
    private String resultJson;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;
}
