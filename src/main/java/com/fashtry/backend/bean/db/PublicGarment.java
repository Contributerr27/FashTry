package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公共服装库 (fash_try.sql - public_garments)
 */
@Data
@TableName("public_garments")
public class PublicGarment {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;

    @TableField("name_en")
    private String nameEn;

    @TableField("scene_category")
    private String sceneCategory;

    /** male, female, unisex */
    private String gender;

    @TableField("garment_type")
    private String garmentType;

    @TableField("image_url")
    private String imageUrl;

    @TableField("thumb_url")
    private String thumbUrl;

    private String remark;

    /** 购买链接，可选 */
    @TableField("purchase_url")
    private String purchaseUrl;

    @TableField("is_active")
    private Integer isActive;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
