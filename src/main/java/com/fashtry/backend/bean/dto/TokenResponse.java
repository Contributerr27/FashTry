package com.fashtry.backend.bean.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String nickname;
    private String mobile;
    /** 用户邀请码，8位英文+数字 */
    private String invitationCode;
    /** 本次请求是否为新用户注册（首次创建账号），用于前端首次回首页展示说明等 */
    private Boolean newRegistration;
}
