package com.fashtry.backend.controller.business;

import com.fashtry.backend.bean.dto.FavoriteAddRequest;
import com.fashtry.backend.bean.dto.FavoriteCheckRequest;
import com.fashtry.backend.bean.vo.FavoriteItemVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.security.SecurityUtils;
import com.fashtry.backend.service.business.FavoriteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 我的作品 → 收藏
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public Result<PageResult<FavoriteItemVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String sourceType) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(favoriteService.list(userId, page, size, sourceType));
    }

    @PostMapping
    public Result<FavoriteItemVO> add(@Valid @RequestBody FavoriteAddRequest body) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(favoriteService.add(userId, body));
    }

    @DeleteMapping
    public Result<Void> remove(@RequestParam String imageUrl) {
        Long userId = SecurityUtils.getCurrentUserId();
        favoriteService.removeByUrl(userId, imageUrl);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> removeById(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "id 无效");
        }
        favoriteService.removeById(userId, id);
        return Result.success();
    }

    @PostMapping("/check")
    public Result<List<String>> check(@Valid @RequestBody FavoriteCheckRequest body) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(favoriteService.check(userId, body.getImageUrls()));
    }
}
