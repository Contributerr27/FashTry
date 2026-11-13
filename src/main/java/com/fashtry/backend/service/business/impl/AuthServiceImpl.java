package com.fashtry.backend.service.business.impl;

import com.fashtry.backend.bean.dto.EmailLoginRequest;
import com.fashtry.backend.bean.dto.PhoneLoginRequest;
import com.fashtry.backend.bean.dto.TokenResponse;
import com.fashtry.backend.bean.dto.WxMiniPhoneLoginRequest;
import com.fashtry.backend.bean.dto.WxPhoneNumberInfo;
import com.fashtry.backend.pay.WxMiniProgramService;
import com.fashtry.backend.common.enums.RegisterSource;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.mapper.UserAuthMapper;
import com.fashtry.backend.mapper.UserMapper;
import com.fashtry.backend.mapper.UserWalletMapper;
import com.fashtry.backend.service.business.AuthService;
import com.fashtry.backend.service.business.CaptchaService;
import com.fashtry.backend.service.business.EmailOtpService;
import com.fashtry.backend.service.business.RegisterAbuseGuard;
import com.fashtry.backend.service.business.RegisterGiftService;
import com.fashtry.backend.service.business.SmsService;
import com.fashtry.backend.utils.TxSendSms;
import com.fashtry.backend.bean.db.Brand;
import com.fashtry.backend.bean.db.User;
import com.fashtry.backend.bean.db.UserAuth;
import com.fashtry.backend.bean.db.UserInvitation;
import com.fashtry.backend.bean.db.UserWallet;
import com.fashtry.backend.common.config.AuthProperties;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.bean.db.UserReferralCode;
import com.fashtry.backend.mapper.UserInvitationMapper;
import com.fashtry.backend.mapper.UserReferralCodeMapper;
import com.fashtry.backend.service.publicapi.BrandPublicQueryService;
import com.fashtry.backend.utils.SnowflakeIdGenerator;
import com.fashtry.backend.common.config.JwtProperties;
import com.fashtry.backend.security.JwtTokenProvider;
import com.fashtry.backend.security.TokenStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 认证服务实现
 * - 手机号未注册时自动注册，昵称随机生成(用户+8位随机字符)
 * - 新用户赠送免费试用点数；命中注册爆发规则时注册成功但不送点
 */
@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final String IDENTITY_PHONE = "phone";
    private static final String IDENTITY_EMAIL = "email";
    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SmsService smsService;
    private final CaptchaService captchaService;
    private final AuthProperties authProperties;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStorageService tokenStorageService;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;
    private final UserAuthMapper userAuthMapper;
    private final UserWalletMapper userWalletMapper;
    private final UserReferralCodeMapper userReferralCodeMapper;
    private final UserInvitationMapper userInvitationMapper;
    private final BrandPublicQueryService brandPublicQueryService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final WxMiniProgramService wxMiniProgramService;
    private final EmailOtpService emailOtpService;
    private final RegisterGiftService registerGiftService;
    private final RegisterAbuseGuard registerAbuseGuard;

    public AuthServiceImpl(SmsService smsService,
                            CaptchaService captchaService,
                            AuthProperties authProperties,
                            JwtTokenProvider jwtTokenProvider,
                            TokenStorageService tokenStorageService,
                            JwtProperties jwtProperties,
                            UserMapper userMapper,
                            UserAuthMapper userAuthMapper,
                            UserWalletMapper userWalletMapper,
                            UserReferralCodeMapper userReferralCodeMapper,
                            UserInvitationMapper userInvitationMapper,
                            BrandPublicQueryService brandPublicQueryService,
                            SnowflakeIdGenerator snowflakeIdGenerator,
                            WxMiniProgramService wxMiniProgramService,
                            EmailOtpService emailOtpService,
                            RegisterGiftService registerGiftService,
                            RegisterAbuseGuard registerAbuseGuard) {
        this.smsService = smsService;
        this.captchaService = captchaService;
        this.authProperties = authProperties;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenStorageService = tokenStorageService;
        this.jwtProperties = jwtProperties;
        this.userMapper = userMapper;
        this.userAuthMapper = userAuthMapper;
        this.userWalletMapper = userWalletMapper;
        this.userReferralCodeMapper = userReferralCodeMapper;
        this.userInvitationMapper = userInvitationMapper;
        this.brandPublicQueryService = brandPublicQueryService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.wxMiniProgramService = wxMiniProgramService;
        this.emailOtpService = emailOtpService;
        this.registerGiftService = registerGiftService;
        this.registerAbuseGuard = registerAbuseGuard;
    }

    @Override
    public void sendLoginCode(String mobile, String captchaKey, String captchaCode) {
        if (!captchaService.validate(captchaKey, captchaCode)) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }
        smsService.sendCode(mobile);
    }

    @Override
    public void sendEmailLoginCode(String email, String captchaKey, String captchaCode) {
        if (!captchaService.validate(captchaKey, captchaCode)) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }
        emailOtpService.sendCode(normalizeEmail(email));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenResponse phoneLogin(PhoneLoginRequest request) {
        if (!smsService.verifyCode(request.getMobile(), request.getCode())) {
            throw new BusinessException(ResultCode.SMS_CODE_ERROR);
        }
        String mobileNorm = TxSendSms.normalizeForStorage(request.getMobile());
        RegisterSource src = parseRegisterSourceOrNull(request.getRegisterSource());
        return loginOrRegisterByMobile(mobileNorm, request.getInvitationCode(), request.getBrandCode(), src);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenResponse emailLogin(EmailLoginRequest request) {
        String emailNorm = normalizeEmail(request.getEmail());
        if (!emailOtpService.verifyCode(emailNorm, request.getCode())) {
            throw new BusinessException(ResultCode.SMS_CODE_ERROR);
        }
        RegisterSource src = parseRegisterSourceOrNull(request.getRegisterSource());
        return loginOrRegisterByEmail(emailNorm, request.getInvitationCode(), request.getBrandCode(), src);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenResponse wxMiniPhoneLogin(WxMiniPhoneLoginRequest request) {
        WxPhoneNumberInfo wxPhone = wxMiniProgramService.getPhoneNumberByCode(request.getWxPhoneCode());
        String e164 = buildE164FromWxPhone(wxPhone);
        String mobileNorm = TxSendSms.normalizeForStorage(e164);
        RegisterSource src = parseRegisterSourceOrNull(request.getRegisterSource());
        return loginOrRegisterByMobile(mobileNorm, request.getInvitationCode(), request.getBrandCode(), src);
    }

    private static String buildE164FromWxPhone(WxPhoneNumberInfo info) {
        String cc = info.getCountryCode() != null ? info.getCountryCode().replaceAll("\\D", "") : "86";
        String pure = info.getPurePhoneNumber() != null ? info.getPurePhoneNumber().replaceAll("\\D", "") : "";
        return "+" + cc + pure;
    }

    /**
     * 未传或非法值时返回 null，不抛异常；新用户落库 register_source 为 null。
     */
    private static RegisterSource parseRegisterSourceOrNull(String raw) {
        if (!org.springframework.util.StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim().toLowerCase();
        for (RegisterSource s : RegisterSource.values()) {
            if (s.getCode().equals(t)) {
                return s;
            }
        }
        return null;
    }

    private TokenResponse loginOrRegisterByMobile(String mobileNorm, String invitationCode, String brandCode,
                                                  RegisterSource registerSource) {
        if (!TxSendSms.isCountryAllowed(mobileNorm)) {
            throw new BusinessException(ResultCode.COUNTRY_NOT_SUPPORTED);
        }
        UserAuth auth = userAuthMapper.selectOne(
                new LambdaQueryWrapper<UserAuth>()
                        .eq(UserAuth::getIdentityType, IDENTITY_PHONE)
                        .eq(UserAuth::getIdentifier, mobileNorm)
        );
        if (auth == null && mobileNorm != null && mobileNorm.startsWith("86") && mobileNorm.length() == 13) {
            String shortForm = mobileNorm.substring(2);
            auth = userAuthMapper.selectOne(
                    new LambdaQueryWrapper<UserAuth>()
                            .eq(UserAuth::getIdentityType, IDENTITY_PHONE)
                            .eq(UserAuth::getIdentifier, shortForm)
            );
        }

        User user;
        boolean newRegistration;
        if (auth == null) {
            String invCode = invitationCode;
            if (org.springframework.util.StringUtils.hasText(invCode)) {
                log.info("新用户注册带邀请码: mobile={}, invitationCode={}", mobileNorm, invCode);
            }
            user = registerNewUser(mobileNorm, invCode, brandCode, registerSource);
            newRegistration = true;
        } else {
            user = userMapper.selectById(auth.getUserId());
            if (user == null || user.getStatus() != 1) {
                throw new BusinessException(ResultCode.USER_STATUS_ERROR);
            }
            ensureInvitationCode(user);
            newRegistration = false;
        }

        return buildTokenResponse(user, newRegistration);
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }

    private TokenResponse loginOrRegisterByEmail(String emailNorm, String invitationCode, String brandCode,
                                                 RegisterSource registerSource) {
        UserAuth auth = userAuthMapper.selectOne(
                new LambdaQueryWrapper<UserAuth>()
                        .eq(UserAuth::getIdentityType, IDENTITY_EMAIL)
                        .eq(UserAuth::getIdentifier, emailNorm)
        );

        User user;
        boolean newRegistration = false;
        if (auth != null) {
            user = userMapper.selectById(auth.getUserId());
            if (user == null || user.getStatus() != 1) {
                throw new BusinessException(ResultCode.USER_STATUS_ERROR);
            }
            ensureInvitationCode(user);
        } else {
            User byEmail = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().apply("LOWER(email) = {0}", emailNorm)
            );
            if (byEmail != null) {
                user = byEmail;
                if (user.getStatus() != 1) {
                    throw new BusinessException(ResultCode.USER_STATUS_ERROR);
                }
                ensureInvitationCode(user);
                ensureEmailAuth(user, emailNorm);
            } else {
                if (org.springframework.util.StringUtils.hasText(invitationCode)) {
                    log.info("新用户邮箱注册带邀请码: email={}, invitationCode={}", emailNorm, invitationCode);
                }
                user = registerNewUserByEmail(emailNorm, invitationCode, brandCode, registerSource);
                newRegistration = true;
            }
        }
        return buildTokenResponse(user, newRegistration);
    }

    private void ensureEmailAuth(User user, String emailNorm) {
        if (!org.springframework.util.StringUtils.hasText(emailNorm)) {
            return;
        }
        UserAuth exist = userAuthMapper.selectOne(
                new LambdaQueryWrapper<UserAuth>()
                        .eq(UserAuth::getIdentityType, IDENTITY_EMAIL)
                        .eq(UserAuth::getIdentifier, emailNorm)
        );
        if (exist != null) {
            return;
        }
        UserAuth ua = new UserAuth();
        ua.setUserId(user.getId());
        ua.setIdentityType(IDENTITY_EMAIL);
        ua.setIdentifier(emailNorm);
        ua.setCredential(null);
        userAuthMapper.insert(ua);
        log.info("已为已有账号补邮箱登录方式: userId={}, email={}", user.getId(), emailNorm);
    }

    private User registerNewUserByEmail(String emailNorm, String usedInviteCode, String brandCode, RegisterSource registerSource) {
        String myCode = generateInvitationCode();
        User user = new User();
        user.setId(snowflakeIdGenerator.nextId());
        user.setNickname(generateRandomNickname());
        user.setAvatarUrl(authProperties.getDefaultAvatarUrl());
        user.setMobile(null);
        user.setEmail(emailNorm);
        user.setInvitationCode(myCode);
        if (org.springframework.util.StringUtils.hasText(brandCode)) {
            Brand brand = brandPublicQueryService.findActiveByCode(brandCode.trim());
            if (brand != null) {
                user.setBrandId(brand.getId());
                if (org.springframework.util.StringUtils.hasText(brand.getLogoUrl())) {
                    user.setAvatarUrl(brand.getLogoUrl().trim());
                }
                log.info("新用户邮箱注册绑定品牌: brandId={}, code={}", brand.getId(), brandCode.trim());
            }
        }
        user.setRegisterSource(registerSource != null ? registerSource.getCode() : null);
        user.setStatus(1);
        user.setUserLevel(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        UserReferralCode referralCode = new UserReferralCode();
        referralCode.setUserId(user.getId());
        referralCode.setReferralCode(myCode);
        referralCode.setTotalInvites(0);
        referralCode.setTotalRewards(0);
        referralCode.setUpdatedAt(LocalDateTime.now());
        userReferralCodeMapper.insert(referralCode);

        UserAuth auth = new UserAuth();
        auth.setUserId(user.getId());
        auth.setIdentityType(IDENTITY_EMAIL);
        auth.setIdentifier(emailNorm);
        auth.setCredential(null);
        userAuthMapper.insert(auth);

        UserWallet wallet = new UserWallet();
        wallet.setUserId(user.getId());
        wallet.setTotalCredits(giftForNewUser(null));
        wallet.setFrozenCredits(0);
        wallet.setTotalRecharged(0);
        wallet.setTotalRechargedAmount(java.math.BigDecimal.ZERO);
        wallet.setVersion(0);
        wallet.setUpdatedAt(LocalDateTime.now());
        userWalletMapper.insert(wallet);

        if (org.springframework.util.StringUtils.hasText(usedInviteCode)) {
            bindInvitation(user.getId(), usedInviteCode);
        }
        return user;
    }

    @Override
    public TokenResponse refreshToken(String refreshToken) {
        return buildTokenResponseFromRefreshToken(refreshToken);
    }

    private TokenResponse buildTokenResponseFromRefreshToken(String refreshToken) {
        io.jsonwebtoken.Claims claims;
        try {
            claims = jwtTokenProvider.parseToken(refreshToken);
        } catch (JwtException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(claims.get("uid").toString());
        String username = claims.get("uname", String.class);
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.USER_STATUS_ERROR);
        }
        return buildTokenResponse(user, false);
    }

    private User registerNewUser(String mobile, String usedInviteCode, String brandCode, RegisterSource registerSource) {
        String myCode = generateInvitationCode();
        User user = new User();
        user.setId(snowflakeIdGenerator.nextId());
        user.setNickname(generateRandomNickname());
        user.setAvatarUrl(authProperties.getDefaultAvatarUrl());
        user.setMobile(mobile);
        user.setInvitationCode(myCode);
        if (org.springframework.util.StringUtils.hasText(brandCode)) {
            Brand brand = brandPublicQueryService.findActiveByCode(brandCode.trim());
            if (brand != null) {
                user.setBrandId(brand.getId());
                if (org.springframework.util.StringUtils.hasText(brand.getLogoUrl())) {
                    user.setAvatarUrl(brand.getLogoUrl().trim());
                }
                log.info("新用户注册绑定品牌: brandId={}, code={}", brand.getId(), brandCode.trim());
            }
        }
        user.setRegisterSource(registerSource != null ? registerSource.getCode() : null);
        user.setStatus(1);
        user.setUserLevel(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        UserReferralCode referralCode = new UserReferralCode();
        referralCode.setUserId(user.getId());
        referralCode.setReferralCode(myCode);
        referralCode.setTotalInvites(0);
        referralCode.setTotalRewards(0);
        referralCode.setUpdatedAt(LocalDateTime.now());
        userReferralCodeMapper.insert(referralCode);

        UserAuth auth = new UserAuth();
        auth.setUserId(user.getId());
        auth.setIdentityType(IDENTITY_PHONE);
        auth.setIdentifier(mobile);
        auth.setCredential(null);
        userAuthMapper.insert(auth);

        UserWallet wallet = new UserWallet();
        wallet.setUserId(user.getId());
        wallet.setTotalCredits(giftForNewUser(mobile));
        wallet.setFrozenCredits(0);
        wallet.setTotalRecharged(0);
        wallet.setTotalRechargedAmount(java.math.BigDecimal.ZERO);
        wallet.setVersion(0);
        wallet.setUpdatedAt(LocalDateTime.now());
        userWalletMapper.insert(wallet);

        if (org.springframework.util.StringUtils.hasText(usedInviteCode)) {
            bindInvitation(user.getId(), usedInviteCode);
        }

        return user;
    }

    /** 命中爆发规则时返回 0，注册照常完成，不把原因返回给客户端。 */
    private int giftForNewUser(String normalizedPhone) {
        if (!registerAbuseGuard.allowGift(normalizedPhone)) {
            return 0;
        }
        return registerGiftService.claimForNewUser();
    }

    /** 绑定邀请关系：创建 user_invitations，邀请人 total_invites +1 */
    private void bindInvitation(Long inviteeId, String invitationCode) {
        UserReferralCode inviterRef = userReferralCodeMapper.selectOne(
                new LambdaQueryWrapper<UserReferralCode>().eq(UserReferralCode::getReferralCode, invitationCode.trim())
        );
        if (inviterRef == null || inviterRef.getUserId() == null) {
            log.warn("邀请码无效或未找到邀请人: code={}", invitationCode);
            return;
        }
        if (inviterRef.getUserId().equals(inviteeId)) return;
        if (userInvitationMapper.selectOne(
                new LambdaQueryWrapper<UserInvitation>().eq(UserInvitation::getInviteeId, inviteeId)) != null) {
            return;
        }
        UserInvitation inv = new UserInvitation();
        inv.setInviterId(inviterRef.getUserId());
        inv.setInviteeId(inviteeId);
        inv.setInvitationCode(invitationCode.trim());
        inv.setStatus(1);
        inv.setCreatedAt(LocalDateTime.now());
        userInvitationMapper.insert(inv);
        inviterRef.setTotalInvites(Optional.ofNullable(inviterRef.getTotalInvites()).orElse(0) + 1);
        inviterRef.setUpdatedAt(LocalDateTime.now());
        userReferralCodeMapper.updateById(inviterRef);
        log.info("邀请关系已绑定: inviterId={}, inviteeId={}, code={}", inviterRef.getUserId(), inviteeId, invitationCode.trim());
    }

    private String generateRandomNickname() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "用户" + suffix;
    }

    /** 老用户无邀请码时补全 */
    private void ensureInvitationCode(User user) {
        if (user.getInvitationCode() != null && !user.getInvitationCode().isEmpty()) {
            return;
        }
        String code = generateInvitationCode();
        user.setInvitationCode(code);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        UserReferralCode referralCode = new UserReferralCode();
        referralCode.setUserId(user.getId());
        referralCode.setReferralCode(code);
        referralCode.setTotalInvites(0);
        referralCode.setTotalRewards(0);
        referralCode.setUpdatedAt(LocalDateTime.now());
        userReferralCodeMapper.insert(referralCode);
    }

    /** 生成8位英文+数字邀请码，排除易混淆字符 0O1lI */
    private String generateInvitationCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
        }
        String code = sb.toString();
        if (userReferralCodeMapper.selectOne(new LambdaQueryWrapper<UserReferralCode>().eq(UserReferralCode::getReferralCode, code)) != null) {
            return generateInvitationCode();
        }
        return code;
    }

    private TokenResponse buildTokenResponse(User user, boolean newRegistration) {
        String mobile = user.getMobile();
        String uname = (mobile != null && !mobile.isEmpty())
                ? mobile
                : (user.getEmail() != null && !user.getEmail().isEmpty())
                ? user.getEmail()
                : String.valueOf(user.getId());
        String role = "USER";
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), uname, role);
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), uname, role);
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
