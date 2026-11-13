package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand_garments")
public class BrandGarment {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("brand_id")
    private Long brandId;

    private String name;

    @TableField("name_en")
    private String nameEn;

    @TableField("scene_category")
    private String sceneCategory;

    private String gender;

    @TableField("garment_type")
    private String garmentType;

    @TableField("image_url")
    private String imageUrl;

    @TableField("thumb_url")
    private String thumbUrl;

    private String remark;

    @TableField("purchase_url")
    private String purchaseUrl;

    @TableField("audit_status")
    private Integer auditStatus;

    @TableField("is_active")
    private Integer isActive;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
