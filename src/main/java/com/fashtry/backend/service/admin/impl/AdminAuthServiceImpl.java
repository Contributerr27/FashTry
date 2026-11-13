package com.fashtry.backend.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fashtry.backend.bean.db.AdminUser;
import com.fashtry.backend.bean.dto.AdminChangePasswordRequest;
import com.fashtry.backend.bean.dto.AdminLoginRequest;
import com.fashtry.backend.bean.vo.AdminLoginVO;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.security.LoginUser;
import com.fashtry.backend.service.admin.AdminAuthService;
import com.fashtry.backend.common.config.JwtProperties;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.AdminUserMapper;
import com.fashtry.backend.security.JwtTokenProvider;
import com.fashtry.backend.security.TokenStorageService;
import com.fashtry.backend.service.business.CaptchaService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private final AdminUserMapper adminUserMapper;
    private final CaptchaService captchaService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStorageService tokenStorageService;
    private final JwtProperties jwtProperties;

    public AdminAuthServiceImpl(AdminUserMapper adminUserMapper,
                                CaptchaService captchaService,
                                PasswordEncoder passwordEncoder,
                                JwtTokenProvider jwtTokenProvider,
                                TokenStorageService tokenStorageService,
                                JwtProperties jwtProperties) {
        this.adminUserMapper = adminUserMapper;
        this.captchaService = captchaService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenStorageService = tokenStorageService;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public AdminLoginVO login(AdminLoginRequest request) {
        if (!captchaService.validate(request.getCaptchaKey(), request.getCaptchaCode())) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }
        String username = request.getUsername().trim();
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名不能为空");
        }
        AdminUser admin = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, username));
        if (admin == null || !Integer.valueOf(1).equals(admin.getStatus())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        String accessToken = jwtTokenProvider.createAdminAccessToken(admin.getId(), admin.getUsername());
        // 后台 token 使用更长有效期（默认 7 天）
        tokenStorageService.saveAdminToken(accessToken, admin.getId(), jwtProperties.getRefreshTokenExpireSeconds());
        return AdminLoginVO.builder()
                .accessToken(accessToken)
                .username(admin.getUsername())
                .build();
    }

    @Override
    public void logout(String bearerToken) {
        if (!StringUtils.hasText(bearerToken)) {
            return;
        }
        String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7) : bearerToken;
        tokenStorageService.deleteAdminToken(token);
    }

    @Override
    public void changePassword(LoginUser loginUser, AdminChangePasswordRequest request) {
        if (!captchaService.validate(request.getCaptchaKey(), request.getCaptchaCode())) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        AdminUser admin = adminUserMapper.selectById(loginUser.getUserId());
        if (admin == null || !Integer.valueOf(1).equals(admin.getStatus())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), admin.getPasswordHash())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "原密码错误");
        }
        admin.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        adminUserMapper.updateById(admin);
    }
}
