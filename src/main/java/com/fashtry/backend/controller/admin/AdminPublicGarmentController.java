package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.db.PublicGarment;
import com.fashtry.backend.bean.dto.AdminPublicGarmentUpsertRequest;
import com.fashtry.backend.bean.dto.UploadPresignRequest;
import com.fashtry.backend.bean.vo.AdminImagePresignVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.PublicModelImageUploadVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.bean.vo.UploadPresignVO;
import com.fashtry.backend.service.admin.AdminConsoleService;
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
@RequestMapping("/api/admin/public-garments")
@Validated
public class AdminPublicGarmentController {

    private final AdminConsoleService adminConsoleService;
    private final CosService cosService;

    public AdminPublicGarmentController(AdminConsoleService adminConsoleService, CosService cosService) {
        this.adminConsoleService = adminConsoleService;
        this.cosService = cosService;
    }

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<PublicModelImageUploadVO> uploadImage(@RequestPart("file") MultipartFile file) {
        return Result.success(cosService.uploadPublicGarmentImage(file));
    }

    /**
     * 直传 COS：获取公共服装 原图+缩略图 的预签名 PUT URL。
     * 缩略图建议前端生成 JPEG（宽 200px 等比）。
     */
    @PostMapping("/presign-image")
    public Result<AdminImagePresignVO> presignImage(@Valid @RequestBody UploadPresignRequest body) {
        UploadPresignVO full = cosService.presignPublicGarmentPut(body.getFileName(), body.getContentType(), body.getFileSize());
        UploadPresignVO thumb = cosService.presignPublicGarmentThumbPut("thumb.jpg", "image/jpeg", 1024);
        return Result.success(AdminImagePresignVO.builder()
                .uploadUrl(full.getUploadUrl())
                .imageUrl(full.getFileUrl())
                .uploadThumbUrl(thumb.getUploadUrl())
                .thumbUrl(thumb.getFileUrl())
                .expireAt(full.getExpireAt())
                .build());
    }

    @GetMapping
    public Result<PageResult<PublicGarment>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sceneCategory,
            @RequestParam(required = false) String gender) {
        return Result.success(adminConsoleService.pagePublicGarments(page, pageSize, keyword, sceneCategory, gender));
    }

    @GetMapping("/{id}")
    public Result<PublicGarment> get(@PathVariable Integer id) {
        return Result.success(adminConsoleService.getPublicGarment(id));
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody AdminPublicGarmentUpsertRequest body) {
        adminConsoleService.createPublicGarment(body);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Integer id, @Valid @RequestBody AdminPublicGarmentUpsertRequest body) {
        adminConsoleService.updatePublicGarment(id, body);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        adminConsoleService.deletePublicGarment(id);
        return Result.success();
    }
}
