package com.fashtry.backend.service.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fashtry.backend.bean.db.GeminiImageTask;
import com.fashtry.backend.bean.db.ModelVideoTask;
import com.fashtry.backend.bean.db.ProductToModelTask;
import com.fashtry.backend.bean.db.TryonTask;
import com.fashtry.backend.bean.db.UserUploadRecord;
import com.fashtry.backend.bean.vo.AdminTaskPreviewImageVO;
import com.fashtry.backend.mapper.GeminiImageTaskMapper;
import com.fashtry.backend.mapper.ModelVideoTaskMapper;
import com.fashtry.backend.mapper.ProductToModelTaskMapper;
import com.fashtry.backend.mapper.TryonTaskMapper;
import com.fashtry.backend.mapper.UserUploadRecordMapper;
import com.fashtry.backend.service.admin.AdminTaskPreviewImageResolver;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 用户上传图统一落库（试穿模特/服装、Gemini 输入图、直传 COS 等） */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserUploadRecordService {

    private static final int SYNC_BATCH = 500;

    private final UserUploadRecordMapper userUploadRecordMapper;
    private final TryonTaskMapper tryonTaskMapper;
    private final GeminiImageTaskMapper geminiImageTaskMapper;
    private final ModelVideoTaskMapper modelVideoTaskMapper;
    private final ProductToModelTaskMapper productToModelTaskMapper;
    private final AdminTaskPreviewImageResolver taskPreviewImageResolver;

    /**
     * 写入用户上传记录（同一 userId + fileUrl 已存在则跳过）。
     *
     * @return true 表示新插入
     */
    public boolean registerIfAbsent(
            Long userId, String fileUrl, String category, String originalName, Long fileSize) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(fileUrl)) {
            return false;
        }
        String url = fileUrl.trim();
        if (!isHttpUrl(url)) {
            return false;
        }
        String cat = normalizeCategory(category);
        Long exists =
                userUploadRecordMapper.selectCount(
                        new LambdaQueryWrapper<UserUploadRecord>()
                                .eq(UserUploadRecord::getUserId, userId)
                                .eq(UserUploadRecord::getFileUrl, url));
        if (exists != null && exists > 0) {
            return false;
        }
        UserUploadRecord record = new UserUploadRecord();
        record.setUserId(userId);
        record.setFileUrl(url);
        record.setFilePath(resolveFilePath(url));
        record.setCategory(cat);
        record.setFileSize(fileSize);
        record.setOriginalName(StringUtils.hasText(originalName) ? originalName.trim() : null);
        record.setCreatedAt(LocalDateTime.now());
        userUploadRecordMapper.insert(record);
        return true;
    }

    public boolean registerIfAbsent(Long userId, String fileUrl, String category, String originalName) {
        return registerIfAbsent(userId, fileUrl, category, originalName, null);
    }

    public void registerTryonInputs(Long userId, String modelUrl, String garmentUrl) {
        registerIfAbsent(userId, modelUrl, "business", "试穿-模特图");
        registerIfAbsent(userId, garmentUrl, "business", "试穿-服装图");
    }

    public void registerGeminiPayload(Long userId, String payloadJson) {
        if (userId == null || userId <= 0) {
            return;
        }
        for (AdminTaskPreviewImageVO img : taskPreviewImageResolver.uploadImagesFromGeminiPayload(payloadJson)) {
            registerIfAbsent(userId, img.getUrl(), "business", img.getLabel());
        }
    }

    public void registerProductToModelInputs(
            Long userId,
            String productUrl,
            String modelUrl,
            String imagePromptUrl,
            String faceReferenceUrl,
            String backgroundReferenceUrl) {
        registerIfAbsent(userId, productUrl, "business", "商品图");
        registerIfAbsent(userId, modelUrl, "business", "模特图");
        registerIfAbsent(userId, imagePromptUrl, "business", "场景参考图");
        registerIfAbsent(userId, faceReferenceUrl, "business", "脸部参考图");
        registerIfAbsent(userId, backgroundReferenceUrl, "business", "背景参考图");
    }

    /** 从各业务任务表补录尚未进入 user_upload_records 的用户输入图（幂等） */
    public int syncMissingFromTasks() {
        int added = 0;
        added += syncTryonTasks();
        added += syncGeminiTasks();
        added += syncModelVideoTasks();
        added += syncProductToModelTasks();
        if (added > 0) {
            log.info("用户上传记录补录完成，新增 {} 条", added);
        }
        return added;
    }

    private int syncTryonTasks() {
        int added = 0;
        List<TryonTask> rows =
                tryonTaskMapper.selectList(
                        new LambdaQueryWrapper<TryonTask>()
                                .isNotNull(TryonTask::getUserId)
                                .orderByDesc(TryonTask::getCreatedAt)
                                .last("LIMIT " + SYNC_BATCH));
        for (TryonTask t : rows) {
            if (registerIfAbsent(t.getUserId(), t.getModelUrl(), "business", "试穿-模特图")) {
                added++;
            }
            if (registerIfAbsent(t.getUserId(), t.getGarmentUrl(), "business", "试穿-服装图")) {
                added++;
            }
        }
        return added;
    }

    private int syncGeminiTasks() {
        int added = 0;
        List<GeminiImageTask> rows =
                geminiImageTaskMapper.selectList(
                        new LambdaQueryWrapper<GeminiImageTask>()
                                .isNotNull(GeminiImageTask::getUserId)
                                .isNotNull(GeminiImageTask::getPayloadJson)
                                .orderByDesc(GeminiImageTask::getCreatedAt)
                                .last("LIMIT " + SYNC_BATCH));
        for (GeminiImageTask t : rows) {
            List<AdminTaskPreviewImageVO> imgs =
                    taskPreviewImageResolver.uploadImagesFromGeminiPayload(t.getPayloadJson());
            Long parentId = taskPreviewImageResolver.parentTaskIdFromPayload(t.getPayloadJson());
            if (parentId != null) {
                GeminiImageTask parent = geminiImageTaskMapper.selectById(parentId);
                if (parent != null && StringUtils.hasText(parent.getPayloadJson())) {
                    imgs =
                            taskPreviewImageResolver.mergeUploadImages(
                                    imgs,
                                    taskPreviewImageResolver.uploadImagesFromGeminiPayload(
                                            parent.getPayloadJson()));
                }
            }
            for (AdminTaskPreviewImageVO img : imgs) {
                if (registerIfAbsent(t.getUserId(), img.getUrl(), "business", img.getLabel())) {
                    added++;
                }
            }
        }
        return added;
    }

    private int syncModelVideoTasks() {
        int added = 0;
        List<ModelVideoTask> rows =
                modelVideoTaskMapper.selectList(
                        new LambdaQueryWrapper<ModelVideoTask>()
                                .isNotNull(ModelVideoTask::getUserId)
                                .isNotNull(ModelVideoTask::getSourceImageUrl)
                                .orderByDesc(ModelVideoTask::getCreatedAt)
                                .last("LIMIT " + SYNC_BATCH));
        for (ModelVideoTask t : rows) {
            if (registerIfAbsent(t.getUserId(), t.getSourceImageUrl(), "business", "视频-起始帧图")) {
                added++;
            }
        }
        return added;
    }

    private int syncProductToModelTasks() {
        int added = 0;
        List<ProductToModelTask> rows =
                productToModelTaskMapper.selectList(
                        new LambdaQueryWrapper<ProductToModelTask>()
                                .isNotNull(ProductToModelTask::getUserId)
                                .orderByDesc(ProductToModelTask::getCreatedAt)
                                .last("LIMIT " + SYNC_BATCH));
        for (ProductToModelTask t : rows) {
            if (registerIfAbsent(t.getUserId(), t.getProductUrl(), "business", "商品图")) {
                added++;
            }
            if (registerIfAbsent(t.getUserId(), t.getModelUrl(), "business", "模特图")) {
                added++;
            }
            if (registerIfAbsent(t.getUserId(), t.getImagePromptUrl(), "business", "场景参考图")) {
                added++;
            }
            if (registerIfAbsent(t.getUserId(), t.getFaceReferenceUrl(), "business", "脸部参考图")) {
                added++;
            }
            if (registerIfAbsent(t.getUserId(), t.getBackgroundReferenceUrl(), "business", "背景参考图")) {
                added++;
            }
        }
        return added;
    }

    private String resolveFilePath(String fileUrl) {
        try {
            URI uri = URI.create(fileUrl.trim());
            String path = uri.getPath();
            if (StringUtils.hasText(path)) {
                return path.startsWith("/") ? path.substring(1) : path;
            }
        } catch (Exception ignored) {
            /* fall through */
        }
        return fileUrl.trim();
    }

    private static String normalizeCategory(String category) {
        if (StringUtils.hasText(category) && "other".equalsIgnoreCase(category.trim())) {
            return "other";
        }
        return "business";
    }

    private static boolean isHttpUrl(String s) {
        if (!StringUtils.hasText(s)) {
            return false;
        }
        String u = s.trim().toLowerCase();
        return u.startsWith("http://") || u.startsWith("https://");
    }
}
