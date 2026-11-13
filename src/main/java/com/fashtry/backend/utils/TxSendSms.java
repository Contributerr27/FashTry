package com.fashtry.backend.utils;

import com.fashtry.backend.common.config.TencentSmsProperties;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 腾讯云短信发送（国内 + 国际）
 * 国内：sms.tencentcloudapi.com，手机号 E.164 格式需带 +
 * 国际：sms.intl.tencentcloudapi.com，region 需 ap-singapore 或 eu-frankfurt
 */
@Component
public class TxSendSms {

    private static final Logger log = LoggerFactory.getLogger(TxSendSms.class);

    private final TencentSmsProperties smsProperties;

    public TxSendSms(TencentSmsProperties smsProperties) {
        this.smsProperties = smsProperties;
    }

    /** 中国大陆手机号：必须以 86 开头，且 86 后跟 11 位 1[3-9]xxx（避免与美加 1+10 位混淆） */
    private static final java.util.regex.Pattern CN_MOBILE = java.util.regex.Pattern.compile("^861[3-9]\\d{9}$");

    public boolean sendVc(String code, String phone) {
        String normalized = normalizePhone(phone);
        if (!isCountryAllowed(normalized)) {
            throw new BusinessException(ResultCode.COUNTRY_NOT_SUPPORTED);
        }
        boolean isCn = isChineseMobile(normalized);
        String apiPhone = "+" + normalized;

        try {
            Credential cred = new Credential(smsProperties.getSecretId(), smsProperties.getSecretKey());
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("sms.tencentcloudapi.com");
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            SmsClient client = new SmsClient(cred, smsProperties.getRegion(), clientProfile);

            SendSmsRequest req = new SendSmsRequest();
            req.setPhoneNumberSet(new String[]{apiPhone});
            req.setSmsSdkAppId(smsProperties.getSmsSdkAppId());
            req.setSignName(isCn ? smsProperties.getSignName() : "");
            req.setTemplateId(isCn ? smsProperties.getTemplateId() : (smsProperties.getTemplateIdIntl() != null ? smsProperties.getTemplateIdIntl() : smsProperties.getTemplateId()));
            req.setTemplateParamSet(new String[]{code});

            SendSmsResponse resp = client.SendSms(req);
            SendStatus[] sendStatusSet = resp.getSendStatusSet();
            if (sendStatusSet != null && sendStatusSet.length > 0 && "Ok".equals(sendStatusSet[0].getCode())) {
                return true;
            }
            if (sendStatusSet != null && sendStatusSet.length > 0) {
                SendStatus st = sendStatusSet[0];
                log.error("[SMS] 腾讯云返回失败 phone={} code={} message={}", apiPhone, st.getCode(), st.getMessage());
            }
        } catch (Exception e) {
            log.error("[SMS] 发送异常 phone={} isCn={}", apiPhone, isCn, e);
        }
        return false;
    }

    /** 校验区号是否在支持列表（单价≤0.5元/条） */
    public static boolean isCountryAllowed(String normalizedPhone) {
        if (normalizedPhone == null || normalizedPhone.isEmpty()) return false;
        String[] codes = {"1671", "886", "853", "852", "389", "372", "371", "370", "357", "264", "351", "90", "82", "81", "66", "65", "61", "56", "55", "52", "48", "46", "45", "44", "41", "39", "34", "30", "86", "1"};
        for (String cc : codes) {
            if (normalizedPhone.equals(cc) || normalizedPhone.startsWith(cc)) return true;
        }
        return false;
    }

    /** 判断是否为中国大陆手机号（仅当 normalized 以 86 开头且符合 861[3-9]xxx 格式，美加 1+10 位走国际模板） */
    private boolean isChineseMobile(String normalized) {
        return normalized != null && CN_MOBILE.matcher(normalized).matches();
    }

    /** 规范化为腾讯云 API 所需格式：国家码+号码（无 + 号） */
    private String normalizePhone(String phone) {
        return normalizeForStorage(phone);
    }

    /**
     * 规范化为统一存储格式（E.164 数字，无 + 号），用于 Redis key、UserAuth identifier 等
     * 例：13800138000 -> 8613800138000，+8613800138000 -> 8613800138000，+14013446480 -> 14013446480
     * 注：14x 与美加 1+401 重叠，不自动加 86；仅 13/15/16/17/18/19 开头的 11 位视为中国
     */
    public static String normalizeForStorage(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.matches("^86[1-9]\\d{10}$")) {
            return digits;
        }
        if (digits.matches("^1[356789]\\d{9}$")) {
            return "86" + digits;
        }
        return digits;
    }
}
