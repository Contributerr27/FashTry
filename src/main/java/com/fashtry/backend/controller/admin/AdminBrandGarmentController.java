package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.db.BrandGarment;
import com.fashtry.backend.bean.dto.AdminBrandGarmentUpsertRequest;
import com.fashtry.backend.bean.dto.UploadPresignRequest;
import com.fashtry.backend.bean.vo.AdminImagePresignVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.PublicModelImageUploadVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.bean.vo.UploadPresignVO;
import com.fashtry.backend.service.admin.BrandAdminService;
import com.fashtry.backend.service.storage.CosService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/brands/{brandId}/garments")
@Validated
public class AdminBrandGarmentController {

    private final BrandAdminService brandAdminService;
    private final CosService cosService;

    public AdminBrandGarmentController(BrandAdminService brandAdminService, CosService cosService) {
        this.brandAdminService = brandAdminService;
        this.cosService = cosService;
    }

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<PublicModelImageUploadVO> uploadImage(@PathVariable Long brandId, @RequestPart("file") MultipartFile file) {
        brandAdminService.getBrand(brandId);
        return Result.success(cosService.uploadBrandGarmentImage(file));
    }

    @PostMapping("/presign-image")
    public Result<AdminImagePresignVO> presignImage(@PathVariable Long brandId, @Valid @RequestBody UploadPresignRequest body) {
        brandAdminService.getBrand(brandId);
        UploadPresignVO full = cosService.presignBrandGarmentPut(body.getFileName(), body.getContentType(), body.getFileSize());
        UploadPresignVO thumb = cosService.presignBrandGarmentThumbPut("thumb.jpg", "image/jpeg", 1024);
        return Result.success(AdminImagePresignVO.builder()
                .uploadUrl(full.getUploadUrl())
                .imageUrl(full.getFileUrl())
                .uploadThumbUrl(thumb.getUploadUrl())
                .thumbUrl(thumb.getFileUrl())
                .expireAt(full.getExpireAt())
                .build());
    }

    @GetMapping
    public Result<PageResult<BrandGarment>> page(
            @PathVariable Long brandId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sceneCategory,
            @RequestParam(required = false) String gender) {
        return Result.success(brandAdminService.pageBrandGarments(brandId, page, pageSize, keyword, sceneCategory, gender));
    }

    @GetMapping("/{id}")
    public Result<BrandGarment> get(@PathVariable Long brandId, @PathVariable Integer id) {
        return Result.success(brandAdminService.getBrandGarment(brandId, id));
    }

    @PostMapping
    public Result<Void> create(@PathVariable Long brandId, @Valid @RequestBody AdminBrandGarmentUpsertRequest body) {
        brandAdminService.createBrandGarment(brandId, body);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long brandId, @PathVariable Integer id,
                               @Valid @RequestBody AdminBrandGarmentUpsertRequest body) {
        brandAdminService.updateBrandGarment(brandId, id, body);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long brandId, @PathVariable Integer id) {
        brandAdminService.deleteBrandGarment(brandId, id);
        return Result.success();
    }
}
