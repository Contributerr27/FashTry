package com.fashtry.backend.controller.publicapi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fashtry.backend.bean.db.Brand;
import com.fashtry.backend.bean.db.BrandGarment;
import com.fashtry.backend.bean.db.PublicGarment;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.PublicGarmentVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.common.constants.ApiBrandHeaders;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.mapper.BrandGarmentMapper;
import com.fashtry.backend.mapper.PublicGarmentMapper;
import com.fashtry.backend.service.publicapi.BrandPublicQueryService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 公共服饰库（无需登录）。携带 {@link ApiBrandHeaders#X_BRAND_CODE} 时返回该品牌上架服装。
 */
@RestController
@RequestMapping("/api/public")
public class PublicGarmentController {

    private static final int DEFAULT_PAGE_SIZE = 6;
    private static final int MAX_PAGE_SIZE = 50;

    private final PublicGarmentMapper publicGarmentMapper;
    private final BrandGarmentMapper brandGarmentMapper;
    private final BrandPublicQueryService brandPublicQueryService;

    public PublicGarmentController(PublicGarmentMapper publicGarmentMapper,
                                   BrandGarmentMapper brandGarmentMapper,
                                   BrandPublicQueryService brandPublicQueryService) {
        this.publicGarmentMapper = publicGarmentMapper;
        this.brandGarmentMapper = brandGarmentMapper;
        this.brandPublicQueryService = brandPublicQueryService;
    }

    @GetMapping("/garments")
    public Result<PageResult<PublicGarmentVO>> listGarments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "6") int size,
            @RequestParam(required = false) String sceneCategory,
            @RequestParam(required = false) String gender,
            @RequestHeader(value = "X-Locale", required = false, defaultValue = "zh") String locale,
            @RequestHeader(value = ApiBrandHeaders.X_BRAND_CODE, required = false) String brandCodeHeader) {
        int p = Math.max(1, page);
        int rawSize = size <= 0 ? DEFAULT_PAGE_SIZE : size;
        int s = Math.min(Math.max(rawSize, 1), MAX_PAGE_SIZE);
        boolean useEn = "en".equalsIgnoreCase(locale);

        if (StringUtils.hasText(brandCodeHeader)) {
            Brand brand = brandPublicQueryService.findActiveByCode(brandCodeHeader.trim());
            if (brand == null) {
                return Result.error(ResultCode.PARAM_ERROR, "品牌不存在或已下架");
            }
            LambdaQueryWrapper<BrandGarment> w = new LambdaQueryWrapper<>();
            w.eq(BrandGarment::getBrandId, brand.getId())
                    .eq(BrandGarment::getIsActive, 1)
                    .eq(BrandGarment::getAuditStatus, 2)
                    .orderByAsc(BrandGarment::getSortOrder)
                    .orderByAsc(BrandGarment::getId);
            w.select(
                    BrandGarment::getId,
                    BrandGarment::getName,
                    BrandGarment::getNameEn,
                    BrandGarment::getSceneCategory,
                    BrandGarment::getGender,
                    BrandGarment::getGarmentType,
                    BrandGarment::getImageUrl,
                    BrandGarment::getThumbUrl,
                    BrandGarment::getSortOrder
            );
            if (sceneCategory != null && !sceneCategory.isBlank()) {
                w.eq(BrandGarment::getSceneCategory, sceneCategory.trim());
            }
            if (gender != null && !gender.isBlank()) {
                String g = gender.trim().toLowerCase();
                if ("female".equals(g) || "male".equals(g)) {
                    w.and(q -> q.eq(BrandGarment::getGender, g).or().eq(BrandGarment::getGender, "unisex"));
                } else if ("unisex".equals(g)) {
                    w.eq(BrandGarment::getGender, "unisex");
                }
            }
            IPage<BrandGarment> pageResult = brandGarmentMapper.selectPage(new Page<>(p, s), w);
            List<BrandGarment> list = pageResult.getRecords();
            List<PublicGarmentVO> voList = list.stream()
                    .map(g -> toVoFromBrand(g, useEn))
                    .collect(Collectors.toList());
            return Result.success(PageResult.of(
                    voList,
                    pageResult.getTotal(),
                    (int) pageResult.getCurrent(),
                    (int) pageResult.getSize()));
        }

        LambdaQueryWrapper<PublicGarment> w = new LambdaQueryWrapper<>();
        w.eq(PublicGarment::getIsActive, 1)
                .orderByAsc(PublicGarment::getSortOrder)
                .orderByAsc(PublicGarment::getId);
        w.select(
                PublicGarment::getId,
                PublicGarment::getName,
                PublicGarment::getNameEn,
                PublicGarment::getSceneCategory,
                PublicGarment::getGender,
                PublicGarment::getGarmentType,
                PublicGarment::getImageUrl,
                PublicGarment::getThumbUrl,
                PublicGarment::getSortOrder
        );
        if (sceneCategory != null && !sceneCategory.isBlank()) {
            w.eq(PublicGarment::getSceneCategory, sceneCategory.trim());
        }
        if (gender != null && !gender.isBlank()) {
            String g = gender.trim().toLowerCase();
            if ("female".equals(g) || "male".equals(g)) {
                w.and(q -> q.eq(PublicGarment::getGender, g).or().eq(PublicGarment::getGender, "unisex"));
            } else if ("unisex".equals(g)) {
                w.eq(PublicGarment::getGender, "unisex");
            }
        }
        IPage<PublicGarment> pageResult = publicGarmentMapper.selectPage(new Page<>(p, s), w);
        List<PublicGarment> list = pageResult.getRecords();
        List<PublicGarmentVO> voList = list.stream()
                .map(g -> toVoFromPublic(g, useEn))
                .collect(Collectors.toList());
        return Result.success(PageResult.of(
                voList,
                pageResult.getTotal(),
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize()));
    }

    private static PublicGarmentVO toVoFromBrand(BrandGarment g, boolean useEn) {
        return PublicGarmentVO.builder()
                .id(g.getId())
                .name(useEn && g.getNameEn() != null && !g.getNameEn().isBlank() ? g.getNameEn() : g.getName())
                .nameEn(g.getNameEn())
                .sceneCategory(g.getSceneCategory())
                .gender(g.getGender() != null && !g.getGender().isBlank() ? g.getGender() : "unisex")
                .garmentType(g.getGarmentType())
                .imageUrl(g.getImageUrl())
                .thumbUrl(g.getThumbUrl())
                .build();
    }

    private static PublicGarmentVO toVoFromPublic(PublicGarment g, boolean useEn) {
        return PublicGarmentVO.builder()
                .id(g.getId())
                .name(useEn && g.getNameEn() != null && !g.getNameEn().isBlank() ? g.getNameEn() : g.getName())
                .nameEn(g.getNameEn())
                .sceneCategory(g.getSceneCategory())
                .gender(g.getGender() != null && !g.getGender().isBlank() ? g.getGender() : "unisex")
                .garmentType(g.getGarmentType())
                .imageUrl(g.getImageUrl())
                .thumbUrl(g.getThumbUrl())
                .build();
    }
}
