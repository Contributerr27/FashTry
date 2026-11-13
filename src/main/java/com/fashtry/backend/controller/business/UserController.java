package com.fashtry.backend.controller.business;

import com.fashtry.backend.bean.db.User;
import com.fashtry.backend.bean.db.UserWallet;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.bean.vo.BrandIntakePublicVO;
import com.fashtry.backend.mapper.UserMapper;
import com.fashtry.backend.bean.dto.UpdateProfileRequest;
import com.fashtry.backend.mapper.UserWalletMapper;
import com.fashtry.backend.security.SecurityUtils;
import com.fashtry.backend.service.publicapi.BrandIntakeService;
import com.fashtry.backend.security.TokenStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户信息接口（需登录，JWT 拦截器校验，Token 存 Redis）
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserMapper userMapper;
    private final UserWalletMapper userWalletMapper;
    private final TokenStorageService tokenStorageService;
    private final BrandIntakeService brandIntakeService;

    public UserController(UserMapper userMapper, UserWalletMapper userWalletMapper, TokenStorageService tokenStorageService,
                          BrandIntakeService brandIntakeService) {
        this.userMapper = userMapper;
        this.userWalletMapper = userWalletMapper;
        this.tokenStorageService = tokenStorageService;
        this.brandIntakeService = brandIntakeService;
    }

    /**
     * 当前账号是否已提交 C 端品牌入驻（含审核中/已通过/已驳回），用于进入入驻页提前展示状态。
     */
    @GetMapping("/brand-intake")
    public Result<BrandIntakePublicVO> myBrandIntake() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(brandIntakeService.getIntakeByUserId(userId));
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userMapper.selectById(userId);
        UserWallet wallet = userWalletMapper.selectById(userId);
        int totalCredits = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;

        if (user == null) {
            var loginUser = SecurityUtils.getCurrentLoginUser();
            String uname = loginUser != null ? loginUser.getUsername() : "";
            String mobile = (uname != null && !uname.contains("@")) ? uname : "";
            String email = (uname != null && uname.contains("@")) ? uname : "";
            return Result.success(Map.of(
                    "userId", userId,
                    "mobile", mobile != null ? mobile : "",
                    "email", email != null ? email : "",
                    "nickname", loginUser != null && loginUser.getNickname() != null ? loginUser.getNickname() : "未知",
                    "totalCredits", totalCredits
            ));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getId());
        body.put("mobile", user.getMobile() != null ? user.getMobile() : "");
        body.put("email", user.getEmail() != null ? user.getEmail() : "");
        body.put("nickname", user.getNickname() != null ? user.getNickname() : "新用户");
        body.put("avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
        body.put("userLevel", user.getUserLevel() != null ? user.getUserLevel() : 0);
        body.put("invitationCode", user.getInvitationCode() != null ? user.getInvitationCode() : "");
        body.put("brandId", user.getBrandId());
        body.put("registerSource", user.getRegisterSource() != null ? user.getRegisterSource() : "");
        body.put("totalCredits", totalCredits);
        return Result.success(body);
    }

    /**
     * 更新用户资料（昵称、头像）
     */
    @PutMapping("/profile")
    public Result<Map<String, Object>> updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.success(Map.of());
        }
        if (StringUtils.hasText(req.getNickname())) {
            user.setNickname(req.getNickname().trim());
        }
        if (req.getAvatarUrl() != null) {
            user.setAvatarUrl(req.getAvatarUrl().trim().isEmpty() ? null : req.getAvatarUrl().trim());
        }
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userMapper.updateById(user);
        UserWallet wallet = userWalletMapper.selectById(userId);
        int totalCredits = wallet != null && wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getId());
        body.put("mobile", user.getMobile() != null ? user.getMobile() : "");
        body.put("email", user.getEmail() != null ? user.getEmail() : "");
        body.put("nickname", user.getNickname() != null ? user.getNickname() : "新用户");
        body.put("avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
        body.put("userLevel", user.getUserLevel() != null ? user.getUserLevel() : 0);
        body.put("invitationCode", user.getInvitationCode() != null ? user.getInvitationCode() : "");
        body.put("brandId", user.getBrandId());
        body.put("registerSource", user.getRegisterSource() != null ? user.getRegisterSource() : "");
        body.put("totalCredits", totalCredits);
        return Result.success(body);
    }

    /**
     * 登出：从 Redis 移除 token
     */
    @PostMapping("")
    public Result<Void> logout(HttpServletRequest request) {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(auth)) {
            String token = auth.startsWith("Bearer ") ? auth.substring(7) : auth;
            tokenStorageService.deleteToken(token);
        }
        return Result.success();
    }
}
