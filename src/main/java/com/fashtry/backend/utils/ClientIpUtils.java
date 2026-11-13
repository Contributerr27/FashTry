package com.fashtry.backend.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 从 {@link HttpServletRequest} 解析客户端 IP（兼容常见代理头）。
 */
public final class ClientIpUtils {

    private ClientIpUtils() {}

    /**
     * 依次读取代理相关头，取第一个有效 IP；多 IP（如 X-Forwarded-For）时取第一段。
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (isBlankOrUnknown(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (isBlankOrUnknown(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (isBlankOrUnknown(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (isBlankOrUnknown(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (isBlankOrUnknown(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (isBlankOrUnknown(ip)) {
            ip = request.getRemoteAddr();
        }
        if (StringUtils.hasText(ip) && ip.contains(",")) {
            String[] ipArray = ip.split(",");
            if (ipArray.length > 0 && StringUtils.hasText(ipArray[0])) {
                ip = ipArray[0].trim();
            }
        }
        return ip != null ? ip.trim() : "";
    }

    private static boolean isBlankOrUnknown(String ip) {
        return !StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip.trim());
    }
}
