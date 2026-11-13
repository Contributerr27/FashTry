package com.fashtry.backend.controller.publicapi;

import com.fashtry.backend.bean.vo.BrandIntakePublicVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.security.SecurityUtils;
import com.fashtry.backend.service.publicapi.BrandIntakeService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 公开：品牌入驻申请（表单 + Logo 上传）
 */
@RestController
@RequestMapping("/api/public")
public class PublicBrandIntakeController {

    private final BrandIntakeService brandIntakeService;

    public PublicBrandIntakeController(BrandIntakeService brandIntakeService) {
        this.brandIntakeService = brandIntakeService;
    }

    @GetMapping("/brand-intake/status")
    public Result<BrandIntakePublicVO> status(@RequestParam("code") String code) {
        return Result.success(brandIntakeService.getStatusByCode(code));
    }

    @PostMapping(value = "/brand-intake", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<BrandIntakePublicVO> submit(
            @RequestParam("companyName") String companyName,
            @RequestParam("brandName") String brandName,
            @RequestParam(value = "brandNameEn", required = false) String brandNameEn,
            @RequestParam("contactName") String contactName,
            @RequestParam("contactPhone") String contactPhone,
            @RequestPart("logo") MultipartFile logo) {
        Long intakeUserId = SecurityUtils.getCurrentUserIdOrNull();
        return Result.success(brandIntakeService.submit(companyName, brandName, brandNameEn,
                contactName, contactPhone, logo, intakeUserId));
    }
}
