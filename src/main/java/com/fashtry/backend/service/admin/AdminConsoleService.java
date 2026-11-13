package com.fashtry.backend.service.admin;

import com.fashtry.backend.bean.db.Order;
import com.fashtry.backend.bean.db.PublicGarment;
import com.fashtry.backend.bean.db.PublicModel;
import com.fashtry.backend.bean.db.RechargePackage;
import com.fashtry.backend.bean.db.UserCreditRecord;
import com.fashtry.backend.bean.vo.AdminTaskDetailWrapper;
import com.fashtry.backend.bean.vo.AdminTryonTaskVO;
import com.fashtry.backend.bean.vo.AdminUnifiedTaskListItemVO;
import com.fashtry.backend.bean.vo.AdminUserUploadVO;
import com.fashtry.backend.bean.dto.AdminPublicGarmentUpsertRequest;
import com.fashtry.backend.bean.dto.AdminPublicModelUpsertRequest;
import com.fashtry.backend.bean.dto.AdminRechargePackageUpsertRequest;
import com.fashtry.backend.bean.vo.AdminUserCreditsVO;
import com.fashtry.backend.bean.vo.AdminUserListVO;
import com.fashtry.backend.bean.vo.PageResult;

public interface AdminConsoleService {

    PageResult<AdminUserListVO> pageUsers(int page, int pageSize, String keyword);

    AdminUserCreditsVO setUserTotalCredits(long userId, int targetTotalCredits);

    PageResult<AdminUnifiedTaskListItemVO> pageUnifiedTasks(int page, int pageSize, Long userId);

    AdminTaskDetailWrapper getAdminTaskDetail(String recordSource, Long id);

    PageResult<AdminUserUploadVO> pageUserUploads(int page, int pageSize, Long userId, String category);

    PageResult<Order> pageOrders(int page, int pageSize, Long userId);

    PageResult<UserCreditRecord> pageCreditRecords(int page, int pageSize, Long userId);

    PageResult<RechargePackage> pageRechargePackages(int page, int pageSize);

    RechargePackage getRechargePackage(Integer id);

    void createRechargePackage(AdminRechargePackageUpsertRequest req);

    void updateRechargePackage(Integer id, AdminRechargePackageUpsertRequest req);

    void deleteRechargePackage(Integer id);

    PageResult<PublicModel> pagePublicModels(int page, int pageSize, String keyword);

    PublicModel getPublicModel(Integer id);

    void createPublicModel(AdminPublicModelUpsertRequest req);

    void updatePublicModel(Integer id, AdminPublicModelUpsertRequest req);

    void deletePublicModel(Integer id);

    PageResult<PublicGarment> pagePublicGarments(int page, int pageSize, String keyword, String sceneCategory, String gender);

    PublicGarment getPublicGarment(Integer id);

    void createPublicGarment(AdminPublicGarmentUpsertRequest req);

    void updatePublicGarment(Integer id, AdminPublicGarmentUpsertRequest req);

    void deletePublicGarment(Integer id);
}
