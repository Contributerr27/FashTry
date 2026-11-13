package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值套餐表
 */
@Data
@TableName("recharge_packages")
public class RechargePackage {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 套餐代号 */
    private String code;

    /** 套餐名称 */
    private String name;

    /** 套餐英文名称 */
    @TableField("name_en")
    private String nameEn;

    /** 售价(元)，企业版为null表示议价 */
    private BigDecimal price;

    /** 售价(美元)，PayPal 使用 */
    @TableField("price_usd")
    private BigDecimal priceUsd;

    /** 获得点数 */
    private Integer credits;

    /** 单价(元/次) */
    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 总成本(元) */
    private BigDecimal cost;

    /** 是否仅首充 */
    @TableField("is_first_purchase_only")
    private Boolean firstPurchaseOnly;

    /** 点数有效天数，null 表示永久有效 */
    @TableField("expiration_days")
    private Integer expirationDays;

    /** 生效开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 生效结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("is_active")
    private Boolean active;

    @TableField("strategy_tag")
    private String strategyTag;

    /** 套餐英文介绍 */
    @TableField("strategy_tag_en")
    private String strategyTagEn;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
