package com.fashtry.backend.service.business;

import com.fashtry.backend.bean.dto.TokenResponse;

/**
 * Google OAuth 2.0 登录服务
 */
public interface GoogleAuthService {

    /**
     * 生成 Google 授权 URL，前端跳转用
     *
     * @param state 防 CSRF，可选，建议传 returnUrl
     * @return 授权 URL，未配置时返回 null
     */
    String buildAuthorizationUrl(String state);

    /**
     * 用授权码换取 token，并登录/注册用户，返回 JWT
     *
     * @param code   Google 回调的 authorization code
     * @param state  回调时的 state
     * @return TokenResponse，失败抛异常
     */
    TokenResponse loginWithCode(String code, String state);
}
