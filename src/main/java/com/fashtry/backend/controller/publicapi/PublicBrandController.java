package com.fashtry.backend.controller.publicapi;

import com.fashtry.backend.bean.db.Brand;
import com.fashtry.backend.bean.vo.PublicBrandVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.common.constants.ApiBrandHeaders;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.service.publicapi.BrandPublicQueryService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开品牌信息（进店落地页展示 logo / 名称）
 */
@RestController
@RequestMapping("/api/public")
public class PublicBrandController {

    private final BrandPublicQueryService brandPublicQueryService;

    public PublicBrandController(BrandPublicQueryService brandPublicQueryService) {
        this.brandPublicQueryService = brandPublicQueryService;
    }

    @GetMapping("/brand")
    public Result<PublicBrandVO> getBrand(
            @RequestHeader(value = ApiBrandHeaders.X_BRAND_CODE, required = false) String headerCode,
            @RequestParam(value = "code", required = false) String queryCode) {
        String code = StringUtils.hasText(headerCode) ? headerCode.trim()
                : (StringUtils.hasText(queryCode) ? queryCode.trim() : null);
        if (!StringUtils.hasText(code)) {
            return Result.error(ResultCode.PARAM_ERROR, "请提供品牌编码");
        }
        Brand b = brandPublicQueryService.findActiveByCode(code);
        if (b == null) {
            return Result.error(ResultCode.PARAM_ERROR, "品牌不存在或已下架");
        }
        PublicBrandVO vo = PublicBrandVO.builder()
                .id(b.getId())
                .code(b.getCode())
                .brandName(b.getBrandName())
                .brandNameEn(b.getBrandNameEn())
                .logoUrl(b.getLogoUrl())
                .build();
        return Result.success(vo);
    }
}
