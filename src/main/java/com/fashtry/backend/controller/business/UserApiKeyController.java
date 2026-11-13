package com.fashtry.backend.controller.business;

import com.fashtry.backend.bean.dto.UserApiKeyCreateRequest;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.bean.vo.UserApiKeyCreatedVO;
import com.fashtry.backend.bean.vo.UserApiKeyVO;
import com.fashtry.backend.security.SecurityUtils;
import com.fashtry.backend.service.openapi.UserApiKeyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户 API 密钥管理（需 JWT 登录） */
@RestController
@RequestMapping("/api/user/api-keys")
public class UserApiKeyController {

    private final UserApiKeyService userApiKeyService;

    public UserApiKeyController(UserApiKeyService userApiKeyService) {
        this.userApiKeyService = userApiKeyService;
    }

    @GetMapping
    public Result<List<UserApiKeyVO>> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(userApiKeyService.listForUser(userId));
    }

    @PostMapping
    public Result<UserApiKeyCreatedVO> create(@Valid @RequestBody(required = false) UserApiKeyCreateRequest body) {
        Long userId = SecurityUtils.getCurrentUserId();
        String name = body != null ? body.getName() : null;
        return Result.success(userApiKeyService.create(userId, name));
    }

    @DeleteMapping("/{id}")
    public Result<Void> revoke(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        userApiKeyService.revoke(userId, id);
        return Result.success();
    }
}
