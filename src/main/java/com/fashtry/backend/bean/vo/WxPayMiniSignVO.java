package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小程序调起微信支付所需参数（对应 wx.requestPayment / uni.requestPayment）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WxPayMiniSignVO {

    private String timeStamp;

    private String nonceStr;

    /** 固定为 prepay_id=xxx */
    @JsonProperty("package")
    private String packageValue;

    /** 微信支付 API v3：RSA */
    private String signType;

    private String paySign;
}
