package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * PayPal 捕获请求（用户从 PayPal 返回后调用）
 */
@Data
public class PayPalCaptureRequest {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotBlank(message = "token 不能为空")
    private String token;
}
