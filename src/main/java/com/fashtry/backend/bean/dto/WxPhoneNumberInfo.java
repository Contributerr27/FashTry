package com.fashtry.backend.bean.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信「手机号快速验证」接口返回的号码信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WxPhoneNumberInfo {

    /** 无区号的手机号 */
    private String purePhoneNumber;

    /** 国家码，如 86 */
    private String countryCode;
}
