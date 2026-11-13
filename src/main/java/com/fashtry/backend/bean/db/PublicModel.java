package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模特表 (fash_try.sql - public_models)
 */
@Data
@TableName("public_models")
public class PublicModel {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 模特代号, 如: Asian_Female_01 */
    private String name;

    /** 性别: male, female, unisex */
    private String gender;

    /** 地区/肤色: Asian, Caucasian, African, Latino */
    private String region;

    /** 年龄段: Teen, Adult, Senior */
    @TableField("age_group")
    private String ageGroup;

    /** 风格标签: Casual, Formal, Sport, Streetwear */
    private String style;

    /** 高清原图(用于Fashn.AI输入) */
    @TableField("image_url")
    private String imageUrl;

    /** 缩略图(前端展示) */
    @TableField("thumb_url")
    private String thumbUrl;

    /** 是否启用 */
    @TableField("is_active")
    private Integer isActive;

    /** 排序权重 */
    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
