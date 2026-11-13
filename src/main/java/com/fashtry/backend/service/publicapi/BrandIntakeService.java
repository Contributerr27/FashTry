package com.fashtry.backend.service.publicapi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fashtry.backend.bean.db.Brand;
import com.fashtry.backend.bean.vo.BrandIntakePublicVO;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.BrandMapper;
import com.fashtry.backend.service.storage.CosService;
import com.fashtry.backend.utils.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/**
 * C 端品牌入驻：直接写入 {@code brands}，待后台审核（audit_status=1，status=0）。
 */
@Service
public class BrandIntakeService {

    private static final int AUDIT_PENDING = 1;
    private static final int STATUS_DISABLED = 0;
    /** legal_mobile 字段长度上限（与表结构一致） */
    private static final int LEGAL_MOBILE_MAX = 20;

    private final BrandMapper brandMapper;
    private final CosService cosService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public BrandIntakeService(BrandMapper brandMapper, CosService cosService, SnowflakeIdGenerator snowflakeIdGenerator) {
        this.brandMapper = brandMapper;
        this.cosService = cosService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /**
     * 仅允许查询 C 端入驻生成的 {@code join-*} 编码，避免通过公开接口遍历其他品牌。
     */
    public BrandIntakePublicVO getStatusByCode(String code) {
        String c = code != null ? code.trim() : "";
        if (!StringUtils.hasText(c) || !c.startsWith("join-")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无效的入驻编号");
        }
        Brand b = brandMapper.selectOne(new LambdaQueryWrapper<Brand>().eq(Brand::getCode, c));
        if (b == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "未找到入驻记录");
        }
        return toPublicVo(b);
    }

    /**
     * 当前登录用户最近一次 C 端入驻申请（code 以 join- 开头），无则返回 null。
     */
    public BrandIntakePublicVO getIntakeByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        Brand b = brandMapper.selectOne(new LambdaQueryWrapper<Brand>()
                .eq(Brand::getIntakeUserId, userId)
                .likeRight(Brand::getCode, "join-")
                .orderByDesc(Brand::getCreatedAt)
                .last("LIMIT 1"));
        if (b == null) {
            return null;
        }
        return toPublicVo(b);
    }

    @Transactional(rollbackFor = Exception.class)
    public BrandIntakePublicVO submit(String companyName, String brandName, String brandNameEn,
                                      String contactName, String contactPhone, MultipartFile logo,
                                      Long intakeUserIdOrNull) {
        String cn = companyName != null ? companyName.trim() : "";
        String bn = brandName != null ? brandName.trim() : "";
        String ben = brandNameEn != null ? brandNameEn.trim() : "";
        String name = contactName != null ? contactName.trim() : "";
        String phone = contactPhone != null ? contactPhone.trim() : "";
        if (!StringUtils.hasText(cn) || cn.length() > 256) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请填写公司名称（不超过 256 字）");
        }
        if (!StringUtils.hasText(bn) || bn.length() > 128) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请填写品牌名称（不超过 128 字）");
        }
        if (ben.length() > 128) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "英文品牌名称不超过 128 字");
        }
        if (!StringUtils.hasText(ben)) {
            ben = null;
        }
        if (!StringUtils.hasText(name) || name.length() > 64) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请填写联系人姓名（不超过 64 字）");
        }
        if (!StringUtils.hasText(phone) || phone.length() > 32) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请填写联系电话（不超过 32 字）");
        }
        if (logo == null || logo.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请上传品牌 Logo");
        }
        String logoUrl = cosService.uploadBrandLogo(logo);
        String code = "join-" + snowflakeIdGenerator.nextId();
        Brand b = new Brand();
        b.setCode(code);
        b.setBrandName(bn);
        b.setBrandNameEn(ben);
        b.setCompanyName(cn);
        b.setLogoUrl(logoUrl);
        if (intakeUserIdOrNull != null) {
            b.setIntakeUserId(intakeUserIdOrNull);
        }
        b.setLegalName(name);
        if (phone.length() <= LEGAL_MOBILE_MAX) {
            b.setLegalMobile(phone);
            b.setRemark("C端品牌入驻申请");
        } else {
            b.setLegalMobile(phone.substring(0, LEGAL_MOBILE_MAX));
            b.setRemark("C端品牌入驻申请；完整联系电话：" + phone);
        }
        b.setAuditStatus(AUDIT_PENDING);
        b.setStatus(STATUS_DISABLED);
        LocalDateTime now = LocalDateTime.now();
        b.setCreatedAt(now);
        b.setUpdatedAt(now);
        brandMapper.insert(b);
        return toPublicVo(b);
    }

    private static BrandIntakePublicVO toPublicVo(Brand b) {
        BrandIntakePublicVO vo = new BrandIntakePublicVO();
        vo.setId(b.getId());
        vo.setCode(b.getCode());
        vo.setBrandName(b.getBrandName());
        vo.setBrandNameEn(b.getBrandNameEn());
        vo.setCompanyName(b.getCompanyName());
        vo.setAuditStatus(b.getAuditStatus());
        vo.setStatus(b.getStatus());
        vo.setRejectReason(rejectReasonForPublic(b));
        return vo;
    }

    /**
     * 仅驳回时返回可读原因；仍为 C 端入驻默认备注时视为未填写驳回说明。
     */
    private static String rejectReasonForPublic(Brand b) {
        if (b.getAuditStatus() == null || b.getAuditStatus() != 3) {
            return null;
        }
        String r = b.getRemark();
        if (!StringUtils.hasText(r)) {
            return null;
        }
        r = r.trim();
        if ("C端品牌入驻申请".equals(r)) {
            return null;
        }
        if (r.startsWith("C端品牌入驻申请；完整联系电话：")) {
            return null;
        }
        return r;
    }
}
