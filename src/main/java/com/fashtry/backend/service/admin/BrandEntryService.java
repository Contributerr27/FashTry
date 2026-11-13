package com.fashtry.backend.service.admin;

import com.fashtry.backend.bean.vo.BrandEntryVO;

public interface BrandEntryService {

    /**
     * 根据品牌 ID 生成 PC / 移动端进店链接与二维码（基于 {@code brands.code}）。
     */
    BrandEntryVO getBrandEntry(Long brandId);
}
