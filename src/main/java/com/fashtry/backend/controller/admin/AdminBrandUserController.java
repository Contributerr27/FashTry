package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.db.BrandUser;
import com.fashtry.backend.bean.dto.AdminBrandUserCreateRequest;
import com.fashtry.backend.bean.dto.AdminBrandUserPasswordRequest;
import com.fashtry.backend.bean.dto.AdminBrandUserUpdateRequest;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.admin.BrandAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/brands/{brandId}/users")
@Validated
public class AdminBrandUserController {

    private final BrandAdminService brandAdminService;

    public AdminBrandUserController(BrandAdminService brandAdminService) {
        this.brandAdminService = brandAdminService;
    }

    @GetMapping
    public Result<PageResult<BrandUser>> page(
            @PathVariable Long brandId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(brandAdminService.pageBrandUsers(brandId, page, pageSize, keyword));
    }

    @GetMapping("/{userId}")
    public Result<BrandUser> get(@PathVariable Long brandId, @PathVariable Long userId) {
        return Result.success(brandAdminService.getBrandUser(brandId, userId));
    }

    @PostMapping
    public Result<Void> create(@PathVariable Long brandId, @Valid @RequestBody AdminBrandUserCreateRequest body) {
        brandAdminService.createBrandUser(brandId, body);
        return Result.success();
    }

    @PutMapping("/{userId}")
    public Result<Void> update(@PathVariable Long brandId, @PathVariable Long userId,
                               @Valid @RequestBody AdminBrandUserUpdateRequest body) {
        brandAdminService.updateBrandUser(brandId, userId, body);
        return Result.success();
    }

    @PostMapping("/{userId}/password")
    public Result<Void> resetPassword(@PathVariable Long brandId, @PathVariable Long userId,
                                    @Valid @RequestBody AdminBrandUserPasswordRequest body) {
        brandAdminService.resetBrandUserPassword(brandId, userId, body);
        return Result.success();
    }

    @DeleteMapping("/{userId}")
    public Result<Void> delete(@PathVariable Long brandId, @PathVariable Long userId) {
        brandAdminService.deleteBrandUser(brandId, userId);
        return Result.success();
    }
}
