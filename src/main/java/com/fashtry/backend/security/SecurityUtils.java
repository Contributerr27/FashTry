package com.fashtry.backend.security;

import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全工具类：从当前请求上下文获取登录用户信息
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户 ID，未登录抛出异常
     */
    public static Long getCurrentUserId() {
        LoginUser loginUser = getCurrentLoginUser();
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return loginUser.getUserId();
    }

    /**
     * 获取当前登录用户 ID，未登录返回 null
     */
    public static Long getCurrentUserIdOrNull() {
        LoginUser loginUser = getCurrentLoginUser();
        return loginUser != null ? loginUser.getUserId() : null;
    }

    /**
     * 获取当前登录用户，未登录返回 null
     */
    public static LoginUser getCurrentLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser)) {
            return null;
        }
        return (LoginUser) authentication.getPrincipal();
    }
}
