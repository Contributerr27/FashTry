package com.fashtry.backend.common.enums;

import lombok.Getter;

/**
 * 统一状态码枚举
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),

    PARAM_INVALID(4001, "参数校验失败"),
    PARAM_ERROR(4001, "参数错误"),
    COUNTRY_NOT_SUPPORTED(4004, "该国家/地区暂不支持"),
    CAPTCHA_ERROR(4001, "图形验证码错误或已过期"),
    SMS_CODE_ERROR(4002, "验证码错误或已过期"),
    USER_STATUS_ERROR(4003, "用户状态异常"),

    UNAUTHORIZED(4010, "未认证或token已失效"),
    FORBIDDEN(4030, "无权限访问"),

    RATE_LIMIT(4290, "发送过于频繁，请稍后再试"),

    SYSTEM_ERROR(5000, "系统异常，请稍后重试"),
    SMS_SEND_FAIL(5001, "短信发送失败，请稍后重试"),
    UPLOAD_FAIL(5002, "文件上传失败，请稍后重试"),
    AI_TRYON_FAIL(5003, "AI 试穿服务异常，请稍后重试"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
