package com.fashtry.backend.bean.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单支付表
 */
@Data
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("user_id")
    private Long userId;

    @TableField("package_id")
    private Integer packageId;

    private BigDecimal amount;

    private String currency;

    @TableField("credits_bought")
    private Integer creditsBought;

    @TableField("pay_channel")
    private String payChannel;

    private Integer status;

    @TableField("third_party_no")
    private String thirdPartyNo;

    @TableField("transaction_id")
    private String transactionId;

    @TableField("code_url")
    private String codeUrl;

    @TableField("open_id")
    private String openId;

    @TableField("buyer_user_id")
    private String buyerUserId;

    @TableField("buyer_logon_id")
    private String buyerLogonId;

    private String description;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("paid_at")
    private LocalDateTime paidAt;
}
