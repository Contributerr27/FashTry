package com.fashtry.backend.service.business.impl;

import com.fashtry.backend.bean.db.Brand;
import com.fashtry.backend.bean.db.User;
import com.fashtry.backend.bean.db.UserAuth;
import com.fashtry.backend.bean.db.UserReferralCode;
import com.fashtry.backend.bean.db.UserWallet;
import com.fashtry.backend.bean.dto.TokenResponse;
import com.fashtry.backend.common.config.AuthProperties;
import com.fashtry.backend.common.config.JwtProperties;
import com.fashtry.backend.common.enums.RegisterSource;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.UserAuthMapper;
import com.fashtry.backend.mapper.UserMapper;
import com.fashtry.backend.mapper.UserWalletMapper;
import com.fashtry.backend.security.JwtTokenProvider;
import com.fashtry.backend.service.business.GoogleAuthService;
import com.fashtry.backend.service.business.RegisterAbuseGuard;
import com.fashtry.backend.service.business.RegisterGiftService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashtry.backend.common.config.GoogleAuthProperties;
import com.fashtry.backend.mapper.UserReferralCodeMapper;
import com.fashtry.backend.service.publicapi.BrandPublicQueryService;
import com.fashtry.backend.security.TokenStorageService;
import com.fashtry.backend.utils.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

@Service
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthServiceImpl.class);
    private static final String IDENTITY_GOOGLE = "google";
    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SCOPES = "openid email profile";

    private final GoogleAuthProperties googleProperties;
    private final AuthProperties authProperties;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStorageService tokenStorageService;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;
    private final UserAuthMapper userAuthMapper;
    private final UserWalletMapper userWalletMapper;
    private final UserReferralCodeMapper userReferralCodeMapper;
    private final BrandPublicQueryService brandPublicQueryService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RegisterGiftService registerGiftService;
    private final RegisterAbuseGuard registerAbuseGuard;

    public GoogleAuthServiceImpl(GoogleAuthProperties googleProperties,
                                 AuthProperties authProperties,
                                 JwtTokenProvider jwtTokenProvider,
                                 TokenStorageService tokenStorageService,
                                 JwtProperties jwtProperties,
                                 UserMapper userMapper,
                                 UserAuthMapper userAuthMapper,
                                 UserWalletMapper userWalletMapper,
                                 UserReferralCodeMapper userReferralCodeMapper,
                                 BrandPublicQueryService brandPublicQueryService,
                                 SnowflakeIdGenerator snowflakeIdGenerator,
                                 RestTemplate restTemplate,
                                 ObjectMapper objectMapper,
                                 RegisterGiftService registerGiftService,
                                 RegisterAbuseGuard registerAbuseGuard) {
        this.googleProperties = googleProperties;
        this.authProperties = authProperties;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenStorageService = tokenStorageService;
        this.jwtProperties = jwtProperties;
        this.userMapper = userMapper;
        this.userAuthMapper = userAuthMapper;
        this.userWalletMapper = userWalletMapper;
        this.userReferralCodeMapper = userReferralCodeMapper;
        this.brandPublicQueryService = brandPublicQueryService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.registerGiftService = registerGiftService;
        this.registerAbuseGuard = registerAbuseGuard;
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        if (!googleProperties.isEnabled()) {
            return null;
        }
        String redirectUri = buildRedirectUri();
        String s = state != null ? state : "";
        return AUTH_URL + "?client_id=" + googleProperties.getClientId()
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + URLEncoder.encode(SCOPES, StandardCharsets.UTF_8)
                + "&state=" + URLEncoder.encode(s, StandardCharsets.UTF_8)
                + "&access_type=offline&prompt=consent";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenResponse loginWithCode(String code, String state) {
        if (!googleProperties.isEnabled()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Google 登录未配置");
        }
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "授权码不能为空");
        }

        String redirectUri = buildRedirectUri();
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", googleProperties.getClientId());
        params.add("client_secret", googleProperties.getClientSecret());
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);

        ResponseEntity<String> resp = restTemplate.exchange(TOKEN_URL, HttpMethod.POST, entity, String.class);
        if (resp.getStatusCode() != HttpStatusCode.valueOf(200) || resp.getBody() == null) {
            log.warn("Google token 交换失败: status={}", resp.getStatusCode());
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Google 授权失败");
        }

        try {
            JsonNode json = objectMapper.readTree(resp.getBody());
            String idToken = json.has("id_token") ? json.get("id_token").asText() : null;
            if (!StringUtils.hasText(idToken)) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "Google 未返回用户信息");
            }

            JsonNode payload = decodeIdTokenPayload(idToken);
            String sub = payload.has("sub") ? payload.get("sub").asText() : null;
            String email = payload.has("email") ? payload.get("email").asText() : null;
            String name = payload.has("name") ? payload.get("name").asText() : null;

            if (!StringUtils.hasText(sub)) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "Google 用户标识无效");
            }

            String brandCodeForSignup = null;
            String stateClean = state != null ? state.trim() : "";
            int bcIdx = stateClean.indexOf("|bc:");
            if (bcIdx >= 0) {
                brandCodeForSignup = stateClean.substring(bcIdx + 4).trim();
                stateClean = stateClean.substring(0, bcIdx);
            }
            GoogleAuthOutcome outcome = findOrCreateUser(sub, email, name, brandCodeForSignup);
            return buildTokenResponse(outcome.user(), outcome.newRegistration());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google 登录解析失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Google 登录失败");
        }
    }

    private String buildRedirectUri() {
        String base = googleProperties.getRedirectBaseUrl().trim();
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return base + "/api/auth/google/callback";
    }

    private JsonNode decodeIdTokenPayload(String idToken) throws Exception {
        String[] parts = idToken.split(Pattern.quote("."));
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid JWT");
        }
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        return objectMapper.readTree(payloadJson);
    }

    private GoogleAuthOutcome findOrCreateUser(String sub, String email, String name, String brandCodeForSignup) {
        UserAuth auth = userAuthMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserAuth>()
                        .eq(UserAuth::getIdentityType, IDENTITY_GOOGLE)
                        .eq(UserAuth::getIdentifier, sub));
        if (auth != null) {
            User user = userMapper.selectById(auth.getUserId());
            if (user != null && (user.getStatus() == null || user.getStatus() == 1)) {
                ensureInvitationCode(user);
                return new GoogleAuthOutcome(user, false);
            }
        }

        return new GoogleAuthOutcome(createGoogleUser(sub, email, name, brandCodeForSignup), true);
    }

    private record GoogleAuthOutcome(User user, boolean newRegistration) {}

    private User createGoogleUser(String sub, String email, String name, String brandCodeForSignup) {
        String invitationCode = generateInvitationCode();
        String nickname = StringUtils.hasText(name) ? name : ("用户" + sub.substring(0, Math.min(8, sub.length())));
        if (nickname.length() > 50) nickname = nickname.substring(0, 50);

        User user = new User();
        user.setId(snowflakeIdGenerator.nextId());
        user.setNickname(nickname);
        user.setAvatarUrl(authProperties.getDefaultAvatarUrl());
        user.setMobile(null);
        user.setEmail(email);
        user.setInvitationCode(invitationCode);
        if (StringUtils.hasText(brandCodeForSignup)) {
            Brand brand = brandPublicQueryService.findActiveByCode(brandCodeForSignup.trim());
            if (brand != null) {
                user.setBrandId(brand.getId());
                if (StringUtils.hasText(brand.getLogoUrl())) {
                    user.setAvatarUrl(brand.getLogoUrl().trim());
                }
                log.info("Google 新用户绑定品牌: brandId={}, code={}", brand.getId(), brandCodeForSignup.trim());
            }
        }
        user.setRegisterSource(RegisterSource.WEB.getCode());
        user.setStatus(1);
        user.setUserLevel(0);
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userMapper.insert(user);

        UserReferralCode ref = new UserReferralCode();
        ref.setUserId(user.getId());
        ref.setReferralCode(invitationCode);
        ref.setTotalInvites(0);
        ref.setTotalRewards(0);
        ref.setUpdatedAt(java.time.LocalDateTime.now());
        userReferralCodeMapper.insert(ref);

        UserAuth auth = new UserAuth();
        auth.setUserId(user.getId());
        auth.setIdentityType(IDENTITY_GOOGLE);
        auth.setIdentifier(sub);
        auth.setCredential(null);
        userAuthMapper.insert(auth);

        UserWallet wallet = new UserWallet();
        wallet.setUserId(user.getId());
        int gift = registerAbuseGuard.allowGift(null) ? registerGiftService.claimForNewUser() : 0;
        wallet.setTotalCredits(gift);
        wallet.setFrozenCredits(0);
        wallet.setTotalRecharged(0);
        wallet.setTotalRechargedAmount(java.math.BigDecimal.ZERO);
        wallet.setVersion(0);
        wallet.setUpdatedAt(java.time.LocalDateTime.now());
        userWalletMapper.insert(wallet);

        log.info("Google 新用户注册: userId={}, sub={}, email={}", user.getId(), sub, email);
        return user;
    }

    private void ensureInvitationCode(User user) {
        if (user.getInvitationCode() != null && !user.getInvitationCode().isEmpty()) return;
        String code = generateInvitationCode();
        user.setInvitationCode(code);
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userMapper.updateById(user);
        UserReferralCode ref = new UserReferralCode();
        ref.setUserId(user.getId());
        ref.setReferralCode(code);
        ref.setTotalInvites(0);
        ref.setTotalRewards(0);
        ref.setUpdatedAt(java.time.LocalDateTime.now());
        userReferralCodeMapper.insert(ref);
    }

    private String generateInvitationCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(new java.security.SecureRandom().nextInt(chars.length())));
        }
        String code = sb.toString();
        if (userReferralCodeMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserReferralCode>()
                        .eq(UserReferralCode::getReferralCode, code)) != null) {
            return generateInvitationCode();
        }
        return code;
    }

    private TokenResponse buildTokenResponse(User user, boolean newRegistration) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail() != null ? user.getEmail() : user.getMobile(), "USER");
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail() != null ? user.getEmail() : user.getMobile(), "USER");
        tokenStorageService.saveToken(accessToken, user.getId(), jwtProperties.getAccessTokenExpireSeconds());
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .nickname(user.getNickname())
                .mobile(user.getMobile())
                .invitationCode(user.getInvitationCode())
                .newRegistration(newRegistration)
                .build();
    }
}
