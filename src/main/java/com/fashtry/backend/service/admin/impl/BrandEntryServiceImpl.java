package com.fashtry.backend.service.admin.impl;

import com.fashtry.backend.bean.db.Brand;
import com.fashtry.backend.bean.vo.BrandEntryVO;
import com.fashtry.backend.common.config.BrandEntryProperties;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.common.util.QrCodePngUtil;
import com.fashtry.backend.service.admin.BrandAdminService;
import com.fashtry.backend.service.admin.BrandEntryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class BrandEntryServiceImpl implements BrandEntryService {

    private final BrandAdminService brandAdminService;
    private final BrandEntryProperties brandEntryProperties;

    public BrandEntryServiceImpl(BrandAdminService brandAdminService, BrandEntryProperties brandEntryProperties) {
        this.brandAdminService = brandAdminService;
        this.brandEntryProperties = brandEntryProperties;
    }

    @Override
    public BrandEntryVO getBrandEntry(Long brandId) {
        Brand b = brandAdminService.getBrand(brandId);
        if (!StringUtils.hasText(b.getCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请先填写品牌唯一编码（code），再生成进店二维码");
        }
        String enc = URLEncoder.encode(b.getCode().trim(), StandardCharsets.UTF_8);
        String landingUrl = replaceCode(brandEntryProperties.getLandingUrlTemplate(), enc);
        int size = Math.min(512, Math.max(120, brandEntryProperties.getQrSize()));

        byte[] png;
        try {
            png = QrCodePngUtil.encodePng(landingUrl, size);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "生成二维码失败");
        }

        BrandEntryVO vo = new BrandEntryVO();
        vo.setBrandId(b.getId());
        vo.setBrandCode(b.getCode().trim());
        vo.setBrandName(b.getBrandName());
        vo.setLandingUrl(landingUrl);
        vo.setQrPngBase64(Base64.getEncoder().encodeToString(png));

        String mpTpl = brandEntryProperties.getMiniProgramUrlTemplate();
        if (StringUtils.hasText(mpTpl) && mpTpl.contains("{code}")) {
            vo.setMiniProgramLandingUrl(replaceCode(mpTpl, enc));
        }

        return vo;
    }

    private static String replaceCode(String template, String urlEncodedCode) {
        if (!StringUtils.hasText(template) || !template.contains("{code}")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "进店链接模板未配置或缺少 {code} 占位符");
        }
        return template.replace("{code}", urlEncodedCode);
    }
}
