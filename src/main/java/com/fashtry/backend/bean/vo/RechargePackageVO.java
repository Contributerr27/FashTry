package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 充值套餐 VO（前端展示，不含成本等内部字段）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RechargePackageVO {

    private Integer id;
    private String code;
    private String name;

    /** 售价(元)，企业版为null表示议价 */
    private BigDecimal price;

    /** 售价(美元)，英文/ PayPal 使用 */
    private BigDecimal priceUsd;

    /** 币种：CNY / USD */
    private String currency;

    /** 获得点数 */
    private Integer credits;

    /** 单价(元/次) */
    private BigDecimal unitPrice;

    /** 是否仅首充 */
    private Boolean firstPurchaseOnly;

    /** 策略定位（可选展示） */
    private String strategyTag;

    /** 当前用户是否可购买（首充套餐需校验） */
    private Boolean purchasable;

    /**
     * USDT：是否展示 TRON 选项；应付低于约 2 美元等值时仅 BSC（与下单规则一致）
     */
    private Boolean usdtTronAvailable;
}
