package com.fashtry.backend.bean.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisterAbuseSettingsVO {

    private int ipWindowMinutes;
    private int ipDenyFrom;
    private int prefixWindowMinutes;
    private int prefixDenyFrom;
    private int globalWindowMinutes;
    private int globalAllow;
    private int smsWindowMinutes;
    private int smsMaxPerIp;
}
