package com.fashtry.backend.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fashtry.backend.bean.db.Brand;
import com.fashtry.backend.bean.db.BrandAuditLog;
import com.fashtry.backend.bean.db.BrandGarment;
import com.fashtry.backend.bean.db.BrandUser;
import com.fashtry.backend.bean.dto.AdminBrandAuditRequest;
import com.fashtry.backend.bean.dto.AdminBrandGarmentUpsertRequest;
import com.fashtry.backend.bean.dto.AdminBrandUpsertRequest;
import com.fashtry.backend.bean.dto.AdminBrandUserCreateRequest;
import com.fashtry.backend.bean.dto.AdminBrandUserPasswordRequest;
import com.fashtry.backend.bean.dto.AdminBrandUserUpdateRequest;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.BrandAuditLogMapper;
import com.fashtry.backend.mapper.BrandGarmentMapper;
import com.fashtry.backend.mapper.BrandMapper;
import com.fashtry.backend.mapper.BrandUserMapper;
import com.fashtry.backend.security.SecurityUtils;
import com.fashtry.backend.service.admin.BrandAdminService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class BrandAdminServiceImpl implements BrandAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> GARMENT_SCENES = Set.of(
            "wedding", "workplace", "campus", "gu_feng", "trendy", "western");
    private static final Set<String> GARMENT_TYPES = Set.of("tops", "bottoms", "one-pieces");
    private static final Set<String> GARMENT_GENDERS = Set.of("male", "female", "unisex");
    private static final Set<String> BRAND_USER_ROLES = Set.of("owner", "admin", "editor", "viewer");

    private final BrandMapper brandMapper;
    private final BrandGarmentMapper brandGarmentMapper;
    private final BrandUserMapper brandUserMapper;
    private final BrandAuditLogMapper brandAuditLogMapper;
    private final PasswordEncoder passwordEncoder;

    public BrandAdminServiceImpl(BrandMapper brandMapper,
                                 BrandGarmentMapper brandGarmentMapper,
                                 BrandUserMapper brandUserMapper,
                                 BrandAuditLogMapper brandAuditLogMapper,
                                 PasswordEncoder passwordEncoder) {
        this.brandMapper = brandMapper;
        this.brandGarmentMapper = brandGarmentMapper;
        this.brandUserMapper = brandUserMapper;
        this.brandAuditLogMapper = brandAuditLogMapper;
        this.passwordEncoder = passwordEncoder;
    }

    private int normSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }

    private int normPage(int page) {
        return Math.max(page, 1);
    }

    private void logAudit(Long brandId, String action, String detail) {
        BrandAuditLog log = new BrandAuditLog();
        log.setBrandId(brandId);
        log.setActorType("platform_admin");
        log.setActorId(SecurityUtils.getCurrentUserIdOrNull());
        log.setAction(action);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        brandAuditLogMapper.insert(log);
    }

    private Brand loadBrandOrThrow(Long id) {
        Brand b = brandMapper.selectById(id);
        if (b == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "品牌不存在");
        }
        return b;
    }

    @Override
    public PageResult<Brand> pageBrands(int page, int pageSize, String keyword, Integer auditStatus, Integer status) {
        Page<Brand> p = new Page<>(normPage(page), normSize(pageSize));
        LambdaQueryWrapper<Brand> w = new LambdaQueryWrapper<>();
        if (auditStatus != null) {
            w.eq(Brand::getAuditStatus, auditStatus);
        }
        if (status != null) {
            w.eq(Brand::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(q -> q.like(Brand::getCode, k)
                    .or().like(Brand::getBrandName, k)
                    .or().like(Brand::getCompanyName, k)
                    .or().like(Brand::getLegalMobile, k));
        }
        w.orderByDesc(Brand::getUpdatedAt).orderByDesc(Brand::getId);
        IPage<Brand> result = brandMapper.selectPage(p, w);
        return PageResult.of(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public Brand getBrand(Long id) {
        return loadBrandOrThrow(id);
    }

    @Override
    public void createBrand(AdminBrandUpsertRequest req) {
        String code = req.getCode().trim();
        if (brandMapper.selectCount(new LambdaQueryWrapper<Brand>().eq(Brand::getCode, code)) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "品牌编码已存在");
        }
        Brand e = toBrandEntity(req, null);
        e.setCode(code);
        if (e.getAuditStatus() == null) {
            e.setAuditStatus(1);
        }
        if (e.getStatus() == null) {
            e.setStatus(1);
        }
        LocalDateTime now = LocalDateTime.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        brandMapper.insert(e);
        logAudit(e.getId(), "brand.create", "code=" + code);
    }

    @Override
    public void updateBrand(Long id, AdminBrandUpsertRequest req) {
        Brand existing = loadBrandOrThrow(id);
        String code = req.getCode().trim();
        if (!code.equals(existing.getCode())) {
            if (brandMapper.selectCount(new LambdaQueryWrapper<Brand>().eq(Brand::getCode, code)) > 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "品牌编码已存在");
            }
        }
        Brand e = toBrandEntity(req, existing);
        e.setId(id);
        e.setCode(code);
        e.setUpdatedAt(LocalDateTime.now());
        brandMapper.updateById(e);
        logAudit(id, "brand.update", "code=" + code);
    }

    @Override
    public void auditBrand(Long id, AdminBrandAuditRequest req) {
        Brand existing = loadBrandOrThrow(id);
        int st = req.getAuditStatus();
        if (st != 2 && st != 3) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "审核状态仅可为已通过(2)或已驳回(3)");
        }
        existing.setAuditStatus(st);
        if (StringUtils.hasText(req.getRemark())) {
            existing.setRemark(req.getRemark().trim());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        brandMapper.updateById(existing);
        logAudit(id, "brand.audit", "auditStatus=" + st + ",note=" + (req.getRemark() != null ? req.getRemark() : ""));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBrand(Long id) {
        loadBrandOrThrow(id);
        Long cntG = brandGarmentMapper.selectCount(new LambdaQueryWrapper<BrandGarment>().eq(BrandGarment::getBrandId, id));
        if (cntG != null && cntG > 0) {
            brandGarmentMapper.delete(new LambdaQueryWrapper<BrandGarment>().eq(BrandGarment::getBrandId, id));
        }
        Long cntU = brandUserMapper.selectCount(new LambdaQueryWrapper<BrandUser>().eq(BrandUser::getBrandId, id));
        if (cntU != null && cntU > 0) {
            brandUserMapper.delete(new LambdaQueryWrapper<BrandUser>().eq(BrandUser::getBrandId, id));
        }
        brandMapper.deleteById(id);
    }

    private static Brand toBrandEntity(AdminBrandUpsertRequest req, Brand existing) {
        Brand e = existing != null ? existing : new Brand();
        e.setBrandName(req.getBrandName().trim());
        e.setBrandNameEn(trimOrNull(req.getBrandNameEn()));
        e.setCompanyName(trimOrNull(req.getCompanyName()));
        e.setCompanyNameEn(trimOrNull(req.getCompanyNameEn()));
        e.setLogoUrl(trimOrNull(req.getLogoUrl()));
        e.setUnifiedSocialCreditCode(trimOrNull(req.getUnifiedSocialCreditCode()));
        e.setBusinessLicenseUrl(trimOrNull(req.getBusinessLicenseUrl()));
        e.setLegalName(trimOrNull(req.getLegalName()));
        e.setLegalIdFrontUrl(trimOrNull(req.getLegalIdFrontUrl()));
        e.setLegalIdBackUrl(trimOrNull(req.getLegalIdBackUrl()));
        e.setLegalIdNoMasked(trimOrNull(req.getLegalIdNoMasked()));
        e.setLegalMobile(trimOrNull(req.getLegalMobile()));
        e.setContactEmail(trimOrNull(req.getContactEmail()));
        e.setCompanyAddress(trimOrNull(req.getCompanyAddress()));
        e.setLongitude(req.getLongitude());
        e.setLatitude(req.getLatitude());
        e.setLocationPrecision(trimOrNull(req.getLocationPrecision()));
        if (req.getAuditStatus() != null) {
            e.setAuditStatus(req.getAuditStatus());
        }
        if (req.getStatus() != null) {
            e.setStatus(req.getStatus());
        }
        e.setRemark(trimOrNull(req.getRemark()));
        return e;
    }

    private static String trimOrNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    @Override
    public PageResult<BrandGarment> pageBrandGarments(Long brandId, int page, int pageSize, String keyword,
                                                      String sceneCategory, String gender) {
        loadBrandOrThrow(brandId);
        Page<BrandGarment> p = new Page<>(normPage(page), normSize(pageSize));
        LambdaQueryWrapper<BrandGarment> w = new LambdaQueryWrapper<>();
        w.eq(BrandGarment::getBrandId, brandId);
        if (StringUtils.hasText(sceneCategory)) {
            w.eq(BrandGarment::getSceneCategory, sceneCategory.trim());
        }
        if (StringUtils.hasText(gender)) {
            w.eq(BrandGarment::getGender, gender.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(q -> q.like(BrandGarment::getName, k).or().like(BrandGarment::getNameEn, k).or().like(BrandGarment::getRemark, k));
        }
        w.orderByAsc(BrandGarment::getSortOrder).orderByAsc(BrandGarment::getId);
        IPage<BrandGarment> result = brandGarmentMapper.selectPage(p, w);
        return PageResult.of(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public BrandGarment getBrandGarment(Long brandId, Integer garmentId) {
        loadBrandOrThrow(brandId);
        BrandGarment g = brandGarmentMapper.selectById(garmentId);
        if (g == null || !brandId.equals(g.getBrandId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "服装不存在");
        }
        return g;
    }

    @Override
    public void createBrandGarment(Long brandId, AdminBrandGarmentUpsertRequest req) {
        loadBrandOrThrow(brandId);
        validateGarment(req);
        BrandGarment e = toGarmentEntity(req, null);
        e.setBrandId(brandId);
        if (e.getAuditStatus() == null) {
            e.setAuditStatus(2);
        }
        LocalDateTime now = LocalDateTime.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        brandGarmentMapper.insert(e);
        logAudit(brandId, "garment.create", "garmentId=" + e.getId());
    }

    @Override
    public void updateBrandGarment(Long brandId, Integer garmentId, AdminBrandGarmentUpsertRequest req) {
        BrandGarment existing = getBrandGarment(brandId, garmentId);
        validateGarment(req);
        BrandGarment e = toGarmentEntity(req, existing);
        e.setId(garmentId);
        e.setBrandId(brandId);
        e.setUpdatedAt(LocalDateTime.now());
        brandGarmentMapper.updateById(e);
        logAudit(brandId, "garment.update", "garmentId=" + garmentId);
    }

    @Override
    public void deleteBrandGarment(Long brandId, Integer garmentId) {
        getBrandGarment(brandId, garmentId);
        brandGarmentMapper.deleteById(garmentId);
        logAudit(brandId, "garment.delete", "garmentId=" + garmentId);
    }

    private void validateGarment(AdminBrandGarmentUpsertRequest req) {
        String scene = req.getSceneCategory() != null ? req.getSceneCategory().trim() : "";
        if (!GARMENT_SCENES.contains(scene)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无效的场景分类");
        }
        String gt = req.getGarmentType() != null ? req.getGarmentType().trim() : "";
        if (StringUtils.hasText(gt) && !GARMENT_TYPES.contains(gt)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无效的服装类型");
        }
        String gen = req.getGender() != null ? req.getGender().trim() : "";
        if (!GARMENT_GENDERS.contains(gen)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无效的性别");
        }
    }

    private static BrandGarment toGarmentEntity(AdminBrandGarmentUpsertRequest req, BrandGarment existing) {
        BrandGarment e = existing != null ? existing : new BrandGarment();
        e.setName(req.getName().trim());
        String en = req.getNameEn() != null ? req.getNameEn().trim() : "";
        e.setNameEn(StringUtils.hasText(en) ? en : null);
        e.setSceneCategory(req.getSceneCategory().trim());
        e.setGender(req.getGender().trim());
        String gt = req.getGarmentType() != null ? req.getGarmentType().trim() : "";
        e.setGarmentType(StringUtils.hasText(gt) ? gt : null);
        String image = req.getImageUrl().trim();
        e.setImageUrl(image);
        String thumb = req.getThumbUrl() != null ? req.getThumbUrl().trim() : "";
        e.setThumbUrl(StringUtils.hasText(thumb) ? thumb : image);
        e.setRemark(req.getRemark() != null ? req.getRemark().trim() : null);
        String purchase = req.getPurchaseUrl() != null ? req.getPurchaseUrl().trim() : "";
        e.setPurchaseUrl(StringUtils.hasText(purchase) ? purchase : null);
        if (req.getAuditStatus() != null) {
            e.setAuditStatus(req.getAuditStatus());
        }
        e.setIsActive(req.getIsActive());
        e.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        return e;
    }

    @Override
    public PageResult<BrandUser> pageBrandUsers(Long brandId, int page, int pageSize, String keyword) {
        loadBrandOrThrow(brandId);
        Page<BrandUser> p = new Page<>(normPage(page), normSize(pageSize));
        LambdaQueryWrapper<BrandUser> w = new LambdaQueryWrapper<>();
        w.eq(BrandUser::getBrandId, brandId);
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(q -> q.like(BrandUser::getUsername, k)
                    .or().like(BrandUser::getDisplayName, k)
                    .or().like(BrandUser::getMobile, k));
        }
        w.orderByDesc(BrandUser::getUpdatedAt);
        IPage<BrandUser> result = brandUserMapper.selectPage(p, w);
        List<BrandUser> records = result.getRecords();
        for (BrandUser u : records) {
            u.setPasswordHash(null);
        }
        return PageResult.of(records, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public BrandUser getBrandUser(Long brandId, Long userId) {
        loadBrandOrThrow(brandId);
        BrandUser u = brandUserMapper.selectById(userId);
        if (u == null || !brandId.equals(u.getBrandId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "账号不存在");
        }
        u.setPasswordHash(null);
        return u;
    }

    @Override
    public void createBrandUser(Long brandId, AdminBrandUserCreateRequest req) {
        loadBrandOrThrow(brandId);
        String username = req.getUsername().trim();
        if (brandUserMapper.selectCount(new LambdaQueryWrapper<BrandUser>()
                .eq(BrandUser::getBrandId, brandId)
                .eq(BrandUser::getUsername, username)) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "登录名已存在");
        }
        String role = StringUtils.hasText(req.getRole()) ? req.getRole().trim() : "editor";
        if (!BRAND_USER_ROLES.contains(role)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无效的角色");
        }
        BrandUser u = new BrandUser();
        u.setBrandId(brandId);
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setDisplayName(trimOrNull(req.getDisplayName()));
        u.setMobile(trimOrNull(req.getMobile()));
        u.setRole(role);
        u.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        LocalDateTime now = LocalDateTime.now();
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        brandUserMapper.insert(u);
        logAudit(brandId, "user.create", "userId=" + u.getId() + ",username=" + username);
    }

    @Override
    public void updateBrandUser(Long brandId, Long userId, AdminBrandUserUpdateRequest req) {
        BrandUser existing = brandUserMapper.selectById(userId);
        if (existing == null || !brandId.equals(existing.getBrandId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "账号不存在");
        }
        if (StringUtils.hasText(req.getRole())) {
            String role = req.getRole().trim();
            if (!BRAND_USER_ROLES.contains(role)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "无效的角色");
            }
            existing.setRole(role);
        }
        if (req.getDisplayName() != null) {
            existing.setDisplayName(trimOrNull(req.getDisplayName()));
        }
        if (req.getMobile() != null) {
            existing.setMobile(trimOrNull(req.getMobile()));
        }
        if (req.getStatus() != null) {
            existing.setStatus(req.getStatus());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        brandUserMapper.updateById(existing);
        logAudit(brandId, "user.update", "userId=" + userId);
    }

    @Override
    public void resetBrandUserPassword(Long brandId, Long userId, AdminBrandUserPasswordRequest req) {
        BrandUser existing = brandUserMapper.selectById(userId);
        if (existing == null || !brandId.equals(existing.getBrandId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "账号不存在");
        }
        existing.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        existing.setUpdatedAt(LocalDateTime.now());
        brandUserMapper.updateById(existing);
        logAudit(brandId, "user.reset_password", "userId=" + userId);
    }

    @Override
    public void deleteBrandUser(Long brandId, Long userId) {
        getBrandUser(brandId, userId);
        brandUserMapper.deleteById(userId);
        logAudit(brandId, "user.delete", "userId=" + userId);
    }
}
