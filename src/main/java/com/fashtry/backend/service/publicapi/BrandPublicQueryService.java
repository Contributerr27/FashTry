package com.fashtry.backend.service.publicapi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fashtry.backend.bean.db.Brand;
import com.fashtry.backend.mapper.BrandMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * C 端品牌查询：仅返回已审核通过且启用中的品牌。
 */
@Service
public class BrandPublicQueryService {

    private final BrandMapper brandMapper;

    public BrandPublicQueryService(BrandMapper brandMapper) {
        this.brandMapper = brandMapper;
    }

    /** 入驻审核通过且品牌启用 */
    public Brand findActiveByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return brandMapper.selectOne(
                new LambdaQueryWrapper<Brand>()
                        .eq(Brand::getCode, code.trim())
                        .eq(Brand::getAuditStatus, 2)
                        .eq(Brand::getStatus, 1));
    }

    public Long findActiveBrandId(String code) {
        Brand b = findActiveByCode(code);
        return b != null ? b.getId() : null;
    }
}
