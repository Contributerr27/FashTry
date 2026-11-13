package com.fashtry.backend.pay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashtry.backend.bean.dto.WxPhoneNumberInfo;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信小程序 jscode2session，用于获取用户 openid（JSAPI 支付必填）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxMiniProgramService {

    private static final String JS_CODE2SESSION = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String CGI_BIN_TOKEN = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String GET_USER_PHONE_NUMBER = "https://api.weixin.qq.com/wxa/business/getuserphonenumber";

    private final WxMiniProgramProperties miniProps;
    private final WxPayProperties wxPayProps;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    private volatile String cachedAccessToken;
    private volatile long accessTokenExpireAtMs;

    /**
     * @return openid，失败返回 null
     */
    public String exchangeCodeForOpenId(String jsCode) {
        if (!StringUtils.hasText(jsCode)) {
            return null;
        }
        String secret = miniProps.getSecret();
        if (!StringUtils.hasText(secret)) {
            log.warn("wx.mini-program.secret 未配置，无法换取 openid");
            return null;
        }
        String appId = resolveMiniProgramAppId();
        if (!StringUtils.hasText(appId)) {
            log.warn("小程序 appId 未配置");
            return null;
        }
        try {
            String qs = "appid=" + urlEncode(appId)
                    + "&secret=" + urlEncode(secret)
                    + "&js_code=" + urlEncode(jsCode)
                    + "&grant_type=authorization_code";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(JS_CODE2SESSION + "?" + qs))
                    .GET()
                    .timeout(Duration.ofSeconds(8))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("jscode2session HTTP {} body={}", resp.statusCode(), resp.body());
                return null;
            }
            JsonNode root = objectMapper.readTree(resp.body());
            if (root.hasNonNull("errcode") && root.get("errcode").asInt() != 0) {
                log.warn("jscode2session err: {}", resp.body());
                return null;
            }
            JsonNode oid = root.get("openid");
            if (oid == null || oid.isNull()) {
                return null;
            }
            return oid.asText();
        } catch (Exception e) {
            log.error("jscode2session 异常", e);
            return null;
        }
    }

    /**
     * 与 {@link #exchangeCodeForOpenId}、微信支付 JSAPI 下单中的 appid 必须完全一致；
     * 优先 {@code wx.mini-program.app-id}（建议与 uni-app manifest mp-weixin.appid 一致），未配时退回 {@code wx.pay.app-id}。
     */
    public String resolveMiniProgramAppId() {
        if (StringUtils.hasText(miniProps.getAppId())) {
            return miniProps.getAppId().trim();
        }
        return wxPayProps.getAppId() != null ? wxPayProps.getAppId().trim() : null;
    }

    /**
     * 使用 getPhoneNumber 返回的 code 换取用户手机号（需配置 wx.mini-program.secret）
     */
    public WxPhoneNumberInfo getPhoneNumberByCode(String wxPhoneCode) {
        if (!StringUtils.hasText(wxPhoneCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "缺少手机号授权码");
        }
        String secret = miniProps.getSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "未配置微信小程序 AppSecret");
        }
        try {
            String accessToken = getClientCredentialAccessToken();
            String url = GET_USER_PHONE_NUMBER + "?access_token=" + urlEncode(accessToken);
            Map<String, String> payload = new HashMap<>();
            payload.put("code", wxPhoneCode.trim());
            String jsonBody = objectMapper.writeValueAsString(payload);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("getuserphonenumber HTTP {} body={}", resp.statusCode(), resp.body());
                throw new BusinessException(ResultCode.PARAM_ERROR, "获取手机号失败");
            }
            JsonNode root = objectMapper.readTree(resp.body());
            if (root.hasNonNull("errcode") && root.get("errcode").asInt() != 0) {
                log.warn("getuserphonenumber err: {}", resp.body());
                throw new BusinessException(ResultCode.PARAM_ERROR, "获取手机号失败: " + root.path("errmsg").asText("错误"));
            }
            JsonNode pi = root.get("phone_info");
            if (pi == null || pi.isNull()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "未返回手机号");
            }
            String pure = pi.path("purePhoneNumber").asText(null);
            if (!StringUtils.hasText(pure)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "手机号为空");
            }
            String cc;
            if (pi.hasNonNull("countryCode")) {
                JsonNode ccn = pi.get("countryCode");
                cc = ccn.isNumber() ? String.valueOf(ccn.asInt()) : ccn.asText();
            } else {
                cc = "86";
            }
            return new WxPhoneNumberInfo(pure.trim(), cc.trim());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("getuserphonenumber 异常", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "获取手机号异常");
        }
    }

    private String getClientCredentialAccessToken() throws Exception {
        long now = System.currentTimeMillis();
        if (StringUtils.hasText(cachedAccessToken) && now < accessTokenExpireAtMs - 120_000) {
            return cachedAccessToken;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (StringUtils.hasText(cachedAccessToken) && now < accessTokenExpireAtMs - 120_000) {
                return cachedAccessToken;
            }
            String appId = resolveMiniProgramAppId();
            String secret = miniProps.getSecret();
            if (!StringUtils.hasText(appId) || !StringUtils.hasText(secret)) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "微信小程序 appId/secret 未配置");
            }
            String qs = "grant_type=client_credential&appid=" + urlEncode(appId) + "&secret=" + urlEncode(secret);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(CGI_BIN_TOKEN + "?" + qs))
                    .GET()
                    .timeout(Duration.ofSeconds(8))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("cgi-bin/token HTTP {} body={}", resp.statusCode(), resp.body());
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "获取微信 access_token 失败");
            }
            JsonNode root = objectMapper.readTree(resp.body());
            if (root.hasNonNull("errcode") && root.get("errcode").asInt() != 0) {
                log.warn("cgi-bin/token err: {}", resp.body());
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "获取微信 access_token 失败");
            }
            String token = root.path("access_token").asText(null);
            int expiresIn = root.path("expires_in").asInt(7200);
            if (!StringUtils.hasText(token)) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "access_token 为空");
            }
            cachedAccessToken = token;
            accessTokenExpireAtMs = System.currentTimeMillis() + expiresIn * 1000L;
            return token;
        }
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
