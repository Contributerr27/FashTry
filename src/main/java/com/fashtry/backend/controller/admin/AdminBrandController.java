package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.db.Brand;
import com.fashtry.backend.bean.dto.AdminBrandAuditRequest;
import com.fashtry.backend.bean.dto.AdminBrandUpsertRequest;
import com.fashtry.backend.bean.vo.BrandEntryVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.PublicModelImageUploadVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.admin.BrandAdminService;
import com.fashtry.backend.service.admin.BrandEntryService;
import com.fashtry.backend.service.storage.CosService;
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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/brands")
@Validated
public class AdminBrandController {

    private final BrandAdminService brandAdminService;
    private final BrandEntryService brandEntryService;
    private final CosService cosService;

    public AdminBrandController(BrandAdminService brandAdminService,
                                BrandEntryService brandEntryService,
                                CosService cosService) {
        this.brandAdminService = brandAdminService;
        this.brandEntryService = brandEntryService;
        this.cosService = cosService;
    }

    @PostMapping(value = "/upload-logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<PublicModelImageUploadVO> uploadLogo(@RequestPart("file") MultipartFile file) {
        String url = cosService.uploadBrandLogo(file);
        return Result.success(new PublicModelImageUploadVO(url, null));
    }

    @PostMapping(value = "/upload-business-license", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<PublicModelImageUploadVO> uploadBusinessLicense(@RequestPart("file") MultipartFile file) {
        String url = cosService.uploadBrandBusinessLicense(file);
        return Result.success(new PublicModelImageUploadVO(url, null));
    }

    @PostMapping(value = "/upload-legal-id-front", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<PublicModelImageUploadVO> uploadLegalIdFront(@RequestPart("file") MultipartFile file) {
        String url = cosService.uploadBrandLegalIdFront(file);
        return Result.success(new PublicModelImageUploadVO(url, null));
    }

    @PostMapping(value = "/upload-legal-id-back", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<PublicModelImageUploadVO> uploadLegalIdBack(@RequestPart("file") MultipartFile file) {
        String url = cosService.uploadBrandLegalIdBack(file);
        return Result.success(new PublicModelImageUploadVO(url, null));
    }

    @GetMapping
    public Result<PageResult<Brand>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) Integer status) {
        return Result.success(brandAdminService.pageBrands(page, pageSize, keyword, auditStatus, status));
    }

    /** 品牌统一进店链接与二维码（PC/H5 同链；小程序预留见 app.brand-entry.mini-program-url-template） */
    @GetMapping("/{id}/brand-entry")
    public Result<BrandEntryVO> brandEntry(@PathVariable Long id) {
        return Result.success(brandEntryService.getBrandEntry(id));
    }

    @GetMapping("/{id}")
    public Result<Brand> get(@PathVariable Long id) {
        return Result.success(brandAdminService.getBrand(id));
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody AdminBrandUpsertRequest body) {
        brandAdminService.createBrand(body);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AdminBrandUpsertRequest body) {
        brandAdminService.updateBrand(id, body);
        return Result.success();
    }

    /** 入驻审核：通过或驳回 */
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody AdminBrandAuditRequest body) {
        brandAdminService.auditBrand(id, body);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        brandAdminService.deleteBrand(id);
        return Result.success();
    }
}
