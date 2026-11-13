package com.fashtry.backend.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fashtry.backend.bean.db.GeminiImageTask;
import com.fashtry.backend.bean.db.ModelVideoTask;
import com.fashtry.backend.bean.db.Order;
import com.fashtry.backend.bean.db.PublicGarment;
import com.fashtry.backend.bean.db.PublicModel;
import com.fashtry.backend.bean.db.RechargePackage;
import com.fashtry.backend.bean.db.TryonTask;
import com.fashtry.backend.bean.db.User;
import com.fashtry.backend.bean.db.UserCreditRecord;
import com.fashtry.backend.bean.db.UserUploadRecord;
import com.fashtry.backend.bean.db.UserWallet;
import com.fashtry.backend.bean.dto.AdminPublicGarmentUpsertRequest;
import com.fashtry.backend.bean.dto.AdminPublicModelUpsertRequest;
import com.fashtry.backend.bean.dto.AdminRechargePackageUpsertRequest;
import com.fashtry.backend.bean.vo.*;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.AdminUnifiedTaskMapper;
import com.fashtry.backend.mapper.GeminiImageTaskMapper;
import com.fashtry.backend.mapper.ModelVideoTaskMapper;
import com.fashtry.backend.mapper.OrderMapper;
import com.fashtry.backend.mapper.PublicGarmentMapper;
import com.fashtry.backend.mapper.PublicModelMapper;
import com.fashtry.backend.mapper.RechargePackageMapper;
import com.fashtry.backend.mapper.TryonTaskMapper;
import com.fashtry.backend.mapper.UserUploadRecordMapper;
import com.fashtry.backend.mapper.UserCreditRecordMapper;
import com.fashtry.backend.mapper.UserMapper;
import com.fashtry.backend.mapper.UserWalletMapper;
import com.fashtry.backend.service.admin.AdminConsoleService;
import com.fashtry.backend.service.admin.AdminTaskPreviewImageResolver;
import com.fashtry.backend.service.storage.UserUploadRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminConsoleServiceImpl implements AdminConsoleService {

    private static final Logger log = LoggerFactory.getLogger(AdminConsoleServiceImpl.class);
    private static final long USER_UPLOAD_SYNC_INTERVAL_MS = 60_000L;
    private static volatile long lastUserUploadSyncMs = 0L;

    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> PUBLIC_GARMENT_SCENES = Set.of(
            "wedding", "workplace", "campus", "gu_feng", "trendy", "western");
    private static final Set<String> PUBLIC_GARMENT_TYPES = Set.of("tops", "bottoms", "one-pieces");
    private static final Set<String> PUBLIC_GARMENT_GENDERS = Set.of("male", "female", "unisex");

    private static final Set<String> USER_UPLOAD_CATEGORIES = Set.of("business", "other");

    /** 点数记录状态：0-有效 */
    private static final int CREDIT_RECORD_ACTIVE = 0;

    private final UserMapper userMapper;
    private final UserWalletMapper userWalletMapper;
    private final TryonTaskMapper tryonTaskMapper;
    private final OrderMapper orderMapper;
    private final UserCreditRecordMapper userCreditRecordMapper;
    private final RechargePackageMapper rechargePackageMapper;
    private final PublicModelMapper publicModelMapper;
    private final PublicGarmentMapper publicGarmentMapper;
    private final UserUploadRecordMapper userUploadRecordMapper;
    private final AdminUnifiedTaskMapper adminUnifiedTaskMapper;
    private final GeminiImageTaskMapper geminiImageTaskMapper;
    private final ModelVideoTaskMapper modelVideoTaskMapper;
    private final AdminTaskPreviewImageResolver taskPreviewImageResolver;
    private final UserUploadRecordService userUploadRecordService;

    public AdminConsoleServiceImpl(UserMapper userMapper,
                                   UserWalletMapper userWalletMapper,
                                   TryonTaskMapper tryonTaskMapper,
                                   OrderMapper orderMapper,
                                   UserCreditRecordMapper userCreditRecordMapper,
                                   RechargePackageMapper rechargePackageMapper,
                                   PublicModelMapper publicModelMapper,
                                   PublicGarmentMapper publicGarmentMapper,
                                   UserUploadRecordMapper userUploadRecordMapper,
                                   AdminUnifiedTaskMapper adminUnifiedTaskMapper,
                                   GeminiImageTaskMapper geminiImageTaskMapper,
                                   ModelVideoTaskMapper modelVideoTaskMapper,
                                   AdminTaskPreviewImageResolver taskPreviewImageResolver,
                                   UserUploadRecordService userUploadRecordService) {
        this.userMapper = userMapper;
        this.userWalletMapper = userWalletMapper;
        this.tryonTaskMapper = tryonTaskMapper;
        this.orderMapper = orderMapper;
        this.userCreditRecordMapper = userCreditRecordMapper;
        this.rechargePackageMapper = rechargePackageMapper;
        this.publicModelMapper = publicModelMapper;
        this.publicGarmentMapper = publicGarmentMapper;
        this.userUploadRecordMapper = userUploadRecordMapper;
        this.adminUnifiedTaskMapper = adminUnifiedTaskMapper;
        this.geminiImageTaskMapper = geminiImageTaskMapper;
        this.modelVideoTaskMapper = modelVideoTaskMapper;
        this.taskPreviewImageResolver = taskPreviewImageResolver;
        this.userUploadRecordService = userUploadRecordService;
    }

    private int normSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }

    private int normPage(int page) {
        return Math.max(page, 1);
    }

    @Override
    public PageResult<AdminUserListVO> pageUsers(int page, int pageSize, String keyword) {
        Page<User> p = new Page<>(normPage(page), normSize(pageSize));
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(q -> {
                q.like(User::getNickname, k)
                        .or().like(User::getMobile, k)
                        .or().like(User::getEmail, k);
                if (k.matches("^\\d{10,20}$")) {
                    try {
                        q.or().eq(User::getId, Long.parseLong(k));
                    } catch (NumberFormatException ignored) {
                        // skip
                    }
                }
            });
        }
        w.orderByDesc(User::getCreatedAt);
        IPage<User> result = userMapper.selectPage(p, w);
        List<Long> ids = result.getRecords().stream().map(User::getId).toList();
        Map<Long, UserWallet> walletMap;
        if (ids.isEmpty()) {
            walletMap = Collections.emptyMap();
        } else {
            walletMap = userWalletMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(UserWallet::getUserId, Function.identity(), (a, b) -> a));
        }
        List<AdminUserListVO> rows = result.getRecords().stream()
                .map(u -> AdminUserListVO.from(u, walletMap.get(u.getId())))
                .toList();
        return PageResult.of(rows, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserCreditsVO setUserTotalCredits(long userId, int targetTotalCredits) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户不存在");
        }
        UserWallet wallet = userWalletMapper.selectById(userId);
        if (wallet == null) {
            wallet = new UserWallet();
            wallet.setUserId(userId);
            wallet.setTotalCredits(0);
            wallet.setFrozenCredits(0);
            wallet.setTotalRecharged(0);
            wallet.setTotalRechargedAmount(BigDecimal.ZERO);
            wallet.setVersion(0);
            wallet.setUpdatedAt(LocalDateTime.now());
            userWalletMapper.insert(wallet);
        }
        int frozen = wallet.getFrozenCredits() != null ? wallet.getFrozenCredits() : 0;
        if (targetTotalCredits < frozen) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "目标点数不能小于冻结点数（当前冻结 " + frozen + "）");
        }
        int prev = wallet.getTotalCredits() != null ? wallet.getTotalCredits() : 0;
        int delta = targetTotalCredits - prev;
        if (delta == 0) {
            return AdminUserCreditsVO.builder()
                    .userId(Long.toString(userId))
                    .previousTotalCredits(prev)
                    .totalCredits(targetTotalCredits)
                    .delta(0)
                    .frozenCredits(frozen)
                    .build();
        }
        wallet.setTotalCredits(targetTotalCredits);
        wallet.setUpdatedAt(LocalDateTime.now());
        userWalletMapper.updateById(wallet);
        if (delta > 0) {
            UserCreditRecord record = new UserCreditRecord();
            record.setUserId(userId);
            record.setOrderId(null);
            record.setPackageId(null);
            record.setCredits(delta);
            record.setCreditsRemaining(delta);
            record.setExpireAt(null);
            record.setStatus(CREDIT_RECORD_ACTIVE);
            record.setCreatedAt(LocalDateTime.now());
            userCreditRecordMapper.insert(record);
        }
        return AdminUserCreditsVO.builder()
                .userId(Long.toString(userId))
                .previousTotalCredits(prev)
                .totalCredits(targetTotalCredits)
                .delta(delta)
                .frozenCredits(frozen)
                .build();
    }

    @Override
    public PageResult<AdminUnifiedTaskListItemVO> pageUnifiedTasks(int page, int pageSize, Long userId) {
        int p = normPage(page);
        int size = normSize(pageSize);
        long offset = (long) (p - 1) * size;
        long total = adminUnifiedTaskMapper.countUnified(userId);
        List<AdminUnifiedTaskListItemVO> rows =
                adminUnifiedTaskMapper.selectUnifiedPage(userId, offset, size);
        Map<Long, User> userMap =
                loadUsersByIds(
                        rows.stream()
                                .map(AdminUnifiedTaskListItemVO::getUserId)
                                .filter(id -> id != null)
                                .distinct()
                                .toList());
        for (AdminUnifiedTaskListItemVO row : rows) {
            row.setUserNickname(nicknameOf(userMap, row.getUserId()));
        }
        return PageResult.of(rows, total, p, size);
    }

    @Override
    public AdminTaskDetailWrapper getAdminTaskDetail(String recordSource, Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务 ID 不能为空");
        }
        if (!StringUtils.hasText(recordSource)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "recordSource 不能为空");
        }
        String src = recordSource.trim().toLowerCase();
        AdminTaskDetailWrapper wrapper = new AdminTaskDetailWrapper();
        wrapper.setRecordSource(src);
        switch (src) {
            case "tryon" -> {
                TryonTask t = tryonTaskMapper.selectById(id);
                if (t == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "试穿任务不存在");
                }
                AdminTryonTaskVO vo = new AdminTryonTaskVO();
                BeanUtils.copyProperties(t, vo);
                vo.setUserNickname(nicknameOf(loadUsersByIds(userIdsOf(t.getUserId())), t.getUserId()));
                vo.setUploadImages(
                        taskPreviewImageResolver.uploadImagesFromTryon(
                                t.getModelUrl(), t.getGarmentUrl(), t.getGeminiPayloadJson()));
                vo.setResultImages(
                        taskPreviewImageResolver.resultImagesFromTryon(
                                t.getResultCosUrl(), t.getResultUrl(), t.getThumbUrl()));
                wrapper.setTryon(vo);
            }
            case "gemini_image" -> {
                GeminiImageTask t = geminiImageTaskMapper.selectById(id);
                if (t == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "Gemini 图任务不存在");
                }
                AdminGeminiImageTaskDetailVO vo = new AdminGeminiImageTaskDetailVO();
                BeanUtils.copyProperties(t, vo);
                vo.setUserNickname(nicknameOf(loadUsersByIds(userIdsOf(t.getUserId())), t.getUserId()));
                vo.setUploadImages(resolveGeminiUploadImages(t));
                vo.setResultImages(taskPreviewImageResolver.resultImagesFromGeminiResultJson(t.getResultJson()));
                wrapper.setGeminiImage(vo);
            }
            case "model_video" -> {
                ModelVideoTask t = modelVideoTaskMapper.selectById(id);
                if (t == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "模特视频任务不存在");
                }
                AdminModelVideoTaskDetailVO vo = new AdminModelVideoTaskDetailVO();
                BeanUtils.copyProperties(t, vo);
                vo.setUserNickname(nicknameOf(loadUsersByIds(userIdsOf(t.getUserId())), t.getUserId()));
                vo.setUploadImages(taskPreviewImageResolver.uploadImagesFromVideoSource(t.getSourceImageUrl()));
                wrapper.setModelVideo(vo);
            }
            default -> throw new BusinessException(ResultCode.PARAM_INVALID, "无效的 recordSource: " + recordSource);
        }
        return wrapper;
    }

    private static List<Long> userIdsOf(Long userId) {
        return userId != null ? List.of(userId) : List.of();
    }

    /** Gemini 任务：解析当前 payload，若有 parentTaskId 则合并父任务上传图 */
    private List<AdminTaskPreviewImageVO> resolveGeminiUploadImages(GeminiImageTask task) {
        List<AdminTaskPreviewImageVO> uploads =
                taskPreviewImageResolver.uploadImagesFromGeminiPayload(task.getPayloadJson());
        Long parentId = taskPreviewImageResolver.parentTaskIdFromPayload(task.getPayloadJson());
        if (parentId == null) {
            return uploads;
        }
        GeminiImageTask parent = geminiImageTaskMapper.selectById(parentId);
        if (parent == null || !StringUtils.hasText(parent.getPayloadJson())) {
            return uploads;
        }
        return taskPreviewImageResolver.mergeUploadImages(
                uploads, taskPreviewImageResolver.uploadImagesFromGeminiPayload(parent.getPayloadJson()));
    }

    @Override
    public PageResult<AdminUserUploadVO> pageUserUploads(int page, int pageSize, Long userId, String category) {
        maybeSyncUserUploadsFromTasks();
        Page<UserUploadRecord> p = new Page<>(normPage(page), normSize(pageSize));
        LambdaQueryWrapper<UserUploadRecord> w = new LambdaQueryWrapper<>();
        if (userId != null) {
            w.eq(UserUploadRecord::getUserId, userId);
        }
        if (StringUtils.hasText(category)) {
            String c = category.trim().toLowerCase();
            if (!USER_UPLOAD_CATEGORIES.contains(c)) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "category 仅支持 business 或 other");
            }
            w.eq(UserUploadRecord::getCategory, c);
        }
        w.orderByDesc(UserUploadRecord::getCreatedAt);
        IPage<UserUploadRecord> result = userUploadRecordMapper.selectPage(p, w);
        Map<Long, User> userMap = loadUsersByIds(
                result.getRecords().stream()
                        .map(UserUploadRecord::getUserId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList());
        List<AdminUserUploadVO> rows = result.getRecords().stream()
                .map(rec -> {
                    AdminUserUploadVO vo = new AdminUserUploadVO();
                    BeanUtils.copyProperties(rec, vo);
                    User u = userMap.get(rec.getUserId());
                    if (u != null) {
                        vo.setUserNickname(u.getNickname());
                        vo.setUserMobile(u.getMobile());
                    }
                    return vo;
                })
                .toList();
        return PageResult.of(rows, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    /** 从试穿 / Gemini / 视频 / 商品转模特等任务补录缺失的上传记录（幂等，限频） */
    private void maybeSyncUserUploadsFromTasks() {
        long now = System.currentTimeMillis();
        if (now - lastUserUploadSyncMs < USER_UPLOAD_SYNC_INTERVAL_MS) {
            return;
        }
        lastUserUploadSyncMs = now;
        try {
            userUploadRecordService.syncMissingFromTasks();
        } catch (Exception e) {
            log.warn("补录用户上传记录失败: {}", e.getMessage());
        }
    }

    private Map<Long, User> loadUsersByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    private static String nicknameOf(Map<Long, User> userMap, Long userId) {
        if (userId == null) {
            return "";
        }
        User u = userMap.get(userId);
        return u != null && StringUtils.hasText(u.getNickname()) ? u.getNickname() : "—";
    }

    @Override
    public PageResult<Order> pageOrders(int page, int pageSize, Long userId) {
        Page<Order> p = new Page<>(normPage(page), normSize(pageSize));
        LambdaQueryWrapper<Order> w = new LambdaQueryWrapper<>();
        if (userId != null) {
            w.eq(Order::getUserId, userId);
        }
        w.orderByDesc(Order::getCreatedAt);
        IPage<Order> result = orderMapper.selectPage(p, w);
        return PageResult.of(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public PageResult<UserCreditRecord> pageCreditRecords(int page, int pageSize, Long userId) {
        Page<UserCreditRecord> p = new Page<>(normPage(page), normSize(pageSize));
        LambdaQueryWrapper<UserCreditRecord> w = new LambdaQueryWrapper<>();
        if (userId != null) {
            w.eq(UserCreditRecord::getUserId, userId);
        }
        w.orderByDesc(UserCreditRecord::getCreatedAt);
        IPage<UserCreditRecord> result = userCreditRecordMapper.selectPage(p, w);
        return PageResult.of(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public PageResult<RechargePackage> pageRechargePackages(int page, int pageSize) {
        Page<RechargePackage> p = new Page<>(normPage(page), normSize(pageSize));
        LambdaQueryWrapper<RechargePackage> w = new LambdaQueryWrapper<>();
        w.orderByAsc(RechargePackage::getSortOrder).orderByAsc(RechargePackage::getId);
        IPage<RechargePackage> result = rechargePackageMapper.selectPage(p, w);
        return PageResult.of(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public RechargePackage getRechargePackage(Integer id) {
        RechargePackage pkg = rechargePackageMapper.selectById(id);
        if (pkg == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "套餐不存在");
        }
        return pkg;
    }

    @Override
    public void createRechargePackage(AdminRechargePackageUpsertRequest req) {
        RechargePackage e = toEntity(req, null);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        rechargePackageMapper.insert(e);
    }

    @Override
    public void updateRechargePackage(Integer id, AdminRechargePackageUpsertRequest req) {
        RechargePackage existing = rechargePackageMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "套餐不存在");
        }
        RechargePackage e = toEntity(req, existing);
        e.setId(id);
        e.setUpdatedAt(LocalDateTime.now());
        rechargePackageMapper.updateById(e);
    }

    @Override
    public void deleteRechargePackage(Integer id) {
        if (rechargePackageMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "套餐不存在");
        }
        rechargePackageMapper.deleteById(id);
    }

    private static RechargePackage toEntity(AdminRechargePackageUpsertRequest req, RechargePackage existing) {
        RechargePackage e = existing != null ? existing : new RechargePackage();
        e.setCode(req.getCode());
        e.setName(req.getName());
        e.setNameEn(req.getNameEn());
        e.setPrice(req.getPrice());
        e.setPriceUsd(req.getPriceUsd());
        e.setCredits(req.getCredits());
        e.setUnitPrice(req.getUnitPrice());
        e.setCost(req.getCost());
        e.setFirstPurchaseOnly(req.getFirstPurchaseOnly());
        e.setExpirationDays(req.getExpirationDays());
        e.setStartTime(req.getStartTime());
        e.setEndTime(req.getEndTime());
        e.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        e.setActive(req.getActive());
        e.setStrategyTag(req.getStrategyTag());
        e.setStrategyTagEn(req.getStrategyTagEn());
        return e;
    }

    @Override
    public PageResult<PublicModel> pagePublicModels(int page, int pageSize, String keyword) {
        Page<PublicModel> p = new Page<>(normPage(page), normSize(pageSize));
        LambdaQueryWrapper<PublicModel> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(q -> q.like(PublicModel::getName, k)
                    .or().like(PublicModel::getGender, k)
                    .or().like(PublicModel::getRegion, k));
        }
        w.orderByAsc(PublicModel::getSortOrder).orderByAsc(PublicModel::getId);
        IPage<PublicModel> result = publicModelMapper.selectPage(p, w);
        return PageResult.of(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public PublicModel getPublicModel(Integer id) {
        PublicModel m = publicModelMapper.selectById(id);
        if (m == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模特不存在");
        }
        return m;
    }

    @Override
    public void createPublicModel(AdminPublicModelUpsertRequest req) {
        PublicModel e = toModelEntity(req, null);
        e.setCreatedAt(LocalDateTime.now());
        publicModelMapper.insert(e);
    }

    @Override
    public void updatePublicModel(Integer id, AdminPublicModelUpsertRequest req) {
        PublicModel existing = publicModelMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模特不存在");
        }
        PublicModel e = toModelEntity(req, existing);
        e.setId(id);
        publicModelMapper.updateById(e);
    }

    @Override
    public void deletePublicModel(Integer id) {
        if (publicModelMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模特不存在");
        }
        publicModelMapper.deleteById(id);
    }

    private static PublicModel toModelEntity(AdminPublicModelUpsertRequest req, PublicModel existing) {
        PublicModel e = existing != null ? existing : new PublicModel();
        e.setName(req.getName());
        e.setGender(req.getGender());
        e.setRegion(req.getRegion());
        e.setAgeGroup(req.getAgeGroup());
        e.setStyle(req.getStyle());
        e.setImageUrl(req.getImageUrl());
        e.setThumbUrl(req.getThumbUrl());
        e.setIsActive(req.getIsActive());
        e.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        return e;
    }

    @Override
    public PageResult<PublicGarment> pagePublicGarments(
            int page, int pageSize, String keyword, String sceneCategory, String gender) {
        Page<PublicGarment> p = new Page<>(normPage(page), normSize(pageSize));
        LambdaQueryWrapper<PublicGarment> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(sceneCategory)) {
            w.eq(PublicGarment::getSceneCategory, sceneCategory.trim());
        }
        if (StringUtils.hasText(gender)) {
            w.eq(PublicGarment::getGender, gender.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(q -> q.like(PublicGarment::getName, k)
                    .or().like(PublicGarment::getNameEn, k)
                    .or().like(PublicGarment::getRemark, k));
        }
        w.orderByAsc(PublicGarment::getSceneCategory).orderByAsc(PublicGarment::getSortOrder).orderByAsc(PublicGarment::getId);
        IPage<PublicGarment> result = publicGarmentMapper.selectPage(p, w);
        return PageResult.of(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public PublicGarment getPublicGarment(Integer id) {
        PublicGarment g = publicGarmentMapper.selectById(id);
        if (g == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "服装不存在");
        }
        return g;
    }

    @Override
    public void createPublicGarment(AdminPublicGarmentUpsertRequest req) {
        validatePublicGarmentUpsert(req);
        PublicGarment e = toGarmentEntity(req, null);
        LocalDateTime now = LocalDateTime.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        publicGarmentMapper.insert(e);
    }

    @Override
    public void updatePublicGarment(Integer id, AdminPublicGarmentUpsertRequest req) {
        PublicGarment existing = publicGarmentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "服装不存在");
        }
        validatePublicGarmentUpsert(req);
        PublicGarment e = toGarmentEntity(req, existing);
        e.setId(id);
        e.setUpdatedAt(LocalDateTime.now());
        publicGarmentMapper.updateById(e);
    }

    @Override
    public void deletePublicGarment(Integer id) {
        if (publicGarmentMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "服装不存在");
        }
        publicGarmentMapper.deleteById(id);
    }

    private void validatePublicGarmentUpsert(AdminPublicGarmentUpsertRequest req) {
        String scene = req.getSceneCategory() != null ? req.getSceneCategory().trim() : "";
        if (!PUBLIC_GARMENT_SCENES.contains(scene)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无效的场景分类");
        }
        String gt = req.getGarmentType() != null ? req.getGarmentType().trim() : "";
        if (StringUtils.hasText(gt) && !PUBLIC_GARMENT_TYPES.contains(gt)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无效的服装类型");
        }
        String gen = req.getGender() != null ? req.getGender().trim() : "";
        if (!PUBLIC_GARMENT_GENDERS.contains(gen)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无效的性别");
        }
        String pu = req.getPurchaseUrl() != null ? req.getPurchaseUrl().trim() : "";
        if (StringUtils.hasText(pu) && pu.length() > 512) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "购买链接过长");
        }
    }

    private static PublicGarment toGarmentEntity(AdminPublicGarmentUpsertRequest req, PublicGarment existing) {
        PublicGarment e = existing != null ? existing : new PublicGarment();
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
        e.setIsActive(req.getIsActive());
        e.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        return e;
    }
}
