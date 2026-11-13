package com.fashtry.backend.common.enums;

import lombok.Getter;

/**
 * 用户注册来源（落库 users.register_source）
 */
@Getter
public enum RegisterSource {

    /** 网页 / H5 */
    WEB("web"),
    /** 微信小程序 */
    MP_WEIXIN("mp_weixin"),
    /** 原生 App（预留） */
    APP("app");

    private final String code;

    RegisterSource(String code) {
        this.code = code;
    }
}
