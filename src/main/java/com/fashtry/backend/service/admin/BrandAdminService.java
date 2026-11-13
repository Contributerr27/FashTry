package com.fashtry.backend.service.admin;

import com.fashtry.backend.bean.db.Brand;
import com.fashtry.backend.bean.db.BrandGarment;
import com.fashtry.backend.bean.db.BrandUser;
import com.fashtry.backend.bean.dto.AdminBrandAuditRequest;
import com.fashtry.backend.bean.dto.AdminBrandGarmentUpsertRequest;
import com.fashtry.backend.bean.dto.AdminBrandUpsertRequest;
import com.fashtry.backend.bean.dto.AdminBrandUserCreateRequest;
import com.fashtry.backend.bean.dto.AdminBrandUserPasswordRequest;
import com.fashtry.backend.bean.dto.AdminBrandUserUpdateRequest;
import com.fashtry.backend.bean.vo.PageResult;

public interface BrandAdminService {

    PageResult<Brand> pageBrands(int page, int pageSize, String keyword, Integer auditStatus, Integer status);

    Brand getBrand(Long id);

    void createBrand(AdminBrandUpsertRequest req);

    void updateBrand(Long id, AdminBrandUpsertRequest req);

    void auditBrand(Long id, AdminBrandAuditRequest req);

    void deleteBrand(Long id);

    PageResult<BrandGarment> pageBrandGarments(Long brandId, int page, int pageSize, String keyword, String sceneCategory, String gender);

    BrandGarment getBrandGarment(Long brandId, Integer garmentId);

    void createBrandGarment(Long brandId, AdminBrandGarmentUpsertRequest req);

    void updateBrandGarment(Long brandId, Integer garmentId, AdminBrandGarmentUpsertRequest req);

    void deleteBrandGarment(Long brandId, Integer garmentId);

    PageResult<BrandUser> pageBrandUsers(Long brandId, int page, int pageSize, String keyword);

    BrandUser getBrandUser(Long brandId, Long userId);

    void createBrandUser(Long brandId, AdminBrandUserCreateRequest req);

    void updateBrandUser(Long brandId, Long userId, AdminBrandUserUpdateRequest req);

    void resetBrandUserPassword(Long brandId, Long userId, AdminBrandUserPasswordRequest req);

    void deleteBrandUser(Long brandId, Long userId);
}
