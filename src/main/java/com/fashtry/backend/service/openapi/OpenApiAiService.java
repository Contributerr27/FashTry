package com.fashtry.backend.service.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashtry.backend.bean.dto.TryonSubmitBatchRequest;
import com.fashtry.backend.bean.dto.TryonSubmitRequest;
import com.fashtry.backend.bean.vo.GeminiTaskQueryVO;
import com.fashtry.backend.bean.vo.GeminiTaskSubmitVO;
import com.fashtry.backend.bean.vo.OpenApiSubmitResultVO;
import com.fashtry.backend.bean.vo.OpenApiUnifiedTaskVO;
import com.fashtry.backend.bean.vo.TryonSubmitBatchVO;
import com.fashtry.backend.bean.vo.TryonSubmitVO;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.security.SecurityUtils;
import com.fashtry.backend.service.ai.GeminiImageTaskService;
import com.fashtry.backend.service.business.TryonService;
import com.fashtry.backend.service.storage.CosService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OpenApiAiService {

    private final TryonService tryonService;
    private final GeminiImageTaskService geminiImageTaskService;
    private final CosService cosService;
    private final ObjectMapper objectMapper;

    public OpenApiAiService(
            TryonService tryonService,
            GeminiImageTaskService geminiImageTaskService,
            CosService cosService,
            ObjectMapper objectMapper) {
        this.tryonService = tryonService;
        this.geminiImageTaskService = geminiImageTaskService;
        this.cosService = cosService;
        this.objectMapper = objectMapper;
    }

    public OpenApiSubmitResultVO submit(
            String modelRaw,
            String payloadJson,
            MultipartFile singleFile,
            MultipartFile[] files,
            MultipartFile faceReference) {
        OpenApiAiModel model =
                OpenApiAiModel.tryParse(modelRaw)
                        .orElseThrow(
                                () -> new BusinessException(
                                        ResultCode.PARAM_INVALID,
                                        "未知 model，可选：virtual_tryon, batch_tryon, garment_extract, portrait_studio, model_shot, garment_suite（及中文别名）"));
        JsonNode root = parsePayload(payloadJson);
        Long uid = SecurityUtils.getCurrentUserId();

        return switch (model) {
            case VIRTUAL_TRYON -> submitVirtualTryon(model, root);
            case BATCH_TRYON -> submitBatchTryon(model, root);
            case MODEL_SHOT -> submitModelShot(model, root, singleFile, files, faceReference, uid);
            case GARMENT_SUITE -> submitGarmentSuite(model, root, singleFile, files, uid);
            case GARMENT_EXTRACT -> submitGarmentExtract(model, root, files, uid);
            case PORTRAIT_STUDIO -> submitPortraitStudio(model, root, files, uid);
        };
    }

    private OpenApiSubmitResultVO submitVirtualTryon(OpenApiAiModel model, JsonNode root) {
        TryonSubmitRequest req = objectMapper.convertValue(root, TryonSubmitRequest.class);
        TryonSubmitVO vo = tryonService.submit(req);
        return OpenApiSubmitResultVO.builder()
                .model(model.canonicalId())
                .taskId(String.valueOf(vo.getTaskId()))
                .build();
    }

    private OpenApiSubmitResultVO submitBatchTryon(OpenApiAiModel model, JsonNode root) {
        TryonSubmitBatchRequest req = objectMapper.convertValue(root, TryonSubmitBatchRequest.class);
        TryonSubmitBatchVO vo = tryonService.submitBatch(req);
        return OpenApiSubmitResultVO.builder().model(model.canonicalId()).taskIds(vo.getTaskIds()).build();
    }

    private OpenApiSubmitResultVO submitModelShot(
            OpenApiAiModel model,
            JsonNode root,
            MultipartFile singleFile,
            MultipartFile[] files,
            MultipartFile faceReference,
            Long uid) {
        Integer sceneVariantCount =
                root.hasNonNull("sceneVariantCount") && root.get("sceneVariantCount").isNumber()
                        ? root.get("sceneVariantCount").asInt()
                        : null;
        String imageUrl = text(root, "imageUrl");
        if (hasAnyMultipart(singleFile, files)) {
            MultipartFile main = resolveOneImage(singleFile, files, true);
            MultipartFile face = faceReference != null && !faceReference.isEmpty() ? faceReference : null;
            GeminiTaskSubmitVO vo =
                    geminiImageTaskService.submitClothingPackshot(
                            main,
                            text(root, "artDirection"),
                            text(root, "modelGender"),
                            text(root, "ageGroup"),
                            text(root, "bodyType"),
                            text(root, "modelStyleNote"),
                            text(root, "faceMode"),
                            text(root, "facePreset"),
                            face,
                            sceneVariantCount,
                            text(root, "framingMode"),
                            text(root, "modelId"),
                            text(root, "aspectRatio"),
                            text(root, "imageSize"),
                            uid);
            return OpenApiSubmitResultVO.builder()
                    .model(model.canonicalId())
                    .taskId(String.valueOf(vo.getTaskId()))
                    .build();
        }
        if (StringUtils.hasText(imageUrl)) {
            String faceRefUrl = text(root, "faceReferenceUrl");
            if (!StringUtils.hasText(faceRefUrl) && faceReference != null && !faceReference.isEmpty()) {
                faceRefUrl = cosService.uploadBusiness(faceReference);
            }
            GeminiTaskSubmitVO vo =
                    geminiImageTaskService.submitClothingPackshotFromImageUrls(
                            imageUrl.trim(),
                            faceRefUrl,
                            text(root, "artDirection"),
                            text(root, "modelGender"),
                            text(root, "ageGroup"),
                            text(root, "bodyType"),
                            text(root, "modelStyleNote"),
                            text(root, "faceMode"),
                            text(root, "facePreset"),
                            sceneVariantCount,
                            text(root, "framingMode"),
                            text(root, "modelId"),
                            text(root, "aspectRatio"),
                            text(root, "imageSize"),
                            uid);
            return OpenApiSubmitResultVO.builder()
                    .model(model.canonicalId())
                    .taskId(String.valueOf(vo.getTaskId()))
                    .build();
        }
        throw new BusinessException(
                ResultCode.PARAM_INVALID, "请上传平铺图（file/files），或在 payload 中提供 imageUrl（http(s)）");
    }

    private OpenApiSubmitResultVO submitGarmentSuite(
            OpenApiAiModel model, JsonNode root, MultipartFile singleFile, MultipartFile[] files, Long uid) {
        String imageUrl = text(root, "imageUrl");
        if (hasAnyMultipart(singleFile, files)) {
            MultipartFile main = resolveOneImage(singleFile, files, true);
            GeminiTaskSubmitVO vo =
                    geminiImageTaskService.submitClothingProductSuite(
                            main,
                            text(root, "modelId"),
                            text(root, "aspectRatio"),
                            text(root, "imageSize"),
                            text(root, "outputLanguage"),
                            text(root, "modelGender"),
                            uid);
            return OpenApiSubmitResultVO.builder()
                    .model(model.canonicalId())
                    .taskId(String.valueOf(vo.getTaskId()))
                    .build();
        }
        if (StringUtils.hasText(imageUrl)) {
            GeminiTaskSubmitVO vo =
                    geminiImageTaskService.submitClothingProductSuiteFromImageUrl(
                            imageUrl.trim(),
                            text(root, "modelId"),
                            text(root, "aspectRatio"),
                            text(root, "imageSize"),
                            text(root, "outputLanguage"),
                            text(root, "modelGender"),
                            uid);
            return OpenApiSubmitResultVO.builder()
                    .model(model.canonicalId())
                    .taskId(String.valueOf(vo.getTaskId()))
                    .build();
        }
        throw new BusinessException(
                ResultCode.PARAM_INVALID, "请上传服装图（file/files），或在 payload 中提供 imageUrl（http(s)）");
    }

    private OpenApiSubmitResultVO submitGarmentExtract(OpenApiAiModel model, JsonNode root, MultipartFile[] files, Long uid) {
        List<String> urlList = resolveImageUrlList(root, true);
        if (hasAnyMultipart(null, files)) {
            MultipartFile[] arr = resolveManyImages(files, true);
            String garmentScope =
                    root.hasNonNull("garmentScope") && StringUtils.hasText(root.get("garmentScope").asText())
                            ? root.get("garmentScope").asText()
                            : "FULL";
            GeminiTaskSubmitVO vo =
                    geminiImageTaskService.submitProductRetouch(
                            arr,
                            text(root, "backgroundColor"),
                            text(root, "aspectRatio"),
                            garmentScope,
                            text(root, "shootHint"),
                            text(root, "cropPreference"),
                            text(root, "imageSize"),
                            uid);
            return OpenApiSubmitResultVO.builder()
                    .model(model.canonicalId())
                    .taskId(String.valueOf(vo.getTaskId()))
                    .build();
        }
        if (!urlList.isEmpty()) {
            String garmentScope =
                    root.hasNonNull("garmentScope") && StringUtils.hasText(root.get("garmentScope").asText())
                            ? root.get("garmentScope").asText()
                            : "FULL";
            GeminiTaskSubmitVO vo =
                    geminiImageTaskService.submitProductRetouchFromImageUrls(
                            urlList,
                            text(root, "backgroundColor"),
                            text(root, "aspectRatio"),
                            garmentScope,
                            text(root, "shootHint"),
                            text(root, "cropPreference"),
                            text(root, "imageSize"),
                            uid);
            return OpenApiSubmitResultVO.builder()
                    .model(model.canonicalId())
                    .taskId(String.valueOf(vo.getTaskId()))
                    .build();
        }
        throw new BusinessException(
                ResultCode.PARAM_INVALID,
                "请上传图片 files，或在 payload 中提供 imageUrls 数组 / imageUrl（http(s)）");
    }

    private OpenApiSubmitResultVO submitPortraitStudio(OpenApiAiModel model, JsonNode root, MultipartFile[] files, Long uid) {
        String style = text(root, "stylePreset");
        if (!StringUtils.hasText(style)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "payload.stylePreset 不能为空");
        }
        String makeup = text(root, "makeupPreset");
        if (!StringUtils.hasText(makeup)) {
            makeup = "ORIGINAL";
        }
        String outfit = text(root, "outfitMode");
        if (!StringUtils.hasText(outfit)) {
            outfit = "STYLE";
        }
        Integer outputCount =
                root.hasNonNull("outputCount") && root.get("outputCount").isNumber()
                        ? root.get("outputCount").asInt()
                        : null;
        List<String> urlList = resolveImageUrlList(root, true);
        if (hasAnyMultipart(null, files)) {
            MultipartFile[] arr = resolveManyImages(files, true);
            GeminiTaskSubmitVO vo =
                    geminiImageTaskService.submitPortraitStudio(
                            arr,
                            style,
                            makeup,
                            outfit,
                            outputCount,
                            text(root, "aspectRatio"),
                            text(root, "imageSize"),
                            text(root, "modelId"),
                            uid);
            return OpenApiSubmitResultVO.builder()
                    .model(model.canonicalId())
                    .taskId(String.valueOf(vo.getTaskId()))
                    .build();
        }
        if (!urlList.isEmpty()) {
            GeminiTaskSubmitVO vo =
                    geminiImageTaskService.submitPortraitStudioFromImageUrls(
                            urlList,
                            style,
                            makeup,
                            outfit,
                            outputCount,
                            text(root, "aspectRatio"),
                            text(root, "imageSize"),
                            text(root, "modelId"),
                            uid);
            return OpenApiSubmitResultVO.builder()
                    .model(model.canonicalId())
                    .taskId(String.valueOf(vo.getTaskId()))
                    .build();
        }
        throw new BusinessException(
                ResultCode.PARAM_INVALID,
                "请上传参考图 files，或在 payload 中提供 imageUrls 数组 / imageUrl（http(s)）");
    }

    public OpenApiUnifiedTaskVO query(String modelRaw, Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "taskId 不能为空");
        }
        OpenApiAiModel model =
                OpenApiAiModel.tryParse(modelRaw)
                        .orElseThrow(
                                () -> new BusinessException(
                                        ResultCode.PARAM_INVALID,
                                        "未知 model，需与提交时类型一致或同属试穿类（virtual_tryon / batch_tryon）"));
        Long uid = SecurityUtils.getCurrentUserId();
        return switch (model) {
            case VIRTUAL_TRYON, BATCH_TRYON -> OpenApiUnifiedTaskVO.builder()
                    .model(model.canonicalId())
                    .taskKind("tryon")
                    .tryon(tryonService.getStatus(taskId))
                    .build();
            case MODEL_SHOT -> wrapGemini(model, geminiImageTaskService.getTaskForPackshot(taskId, uid));
            case GARMENT_SUITE -> wrapGemini(model, geminiImageTaskService.getTaskForClothingSuite(taskId, uid));
            case GARMENT_EXTRACT -> wrapGemini(model, geminiImageTaskService.getTaskForProductRetouch(taskId, uid));
            case PORTRAIT_STUDIO -> wrapGemini(model, geminiImageTaskService.getTaskForPortraitStudio(taskId, uid));
        };
    }

    private static OpenApiUnifiedTaskVO wrapGemini(OpenApiAiModel model, GeminiTaskQueryVO q) {
        return OpenApiUnifiedTaskVO.builder()
                .model(model.canonicalId())
                .taskKind("gemini")
                .gemini(q)
                .build();
    }

    private JsonNode parsePayload(String payloadJson) {
        try {
            if (payloadJson == null || payloadJson.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(payloadJson);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "payload 须为合法 JSON 对象字符串");
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isTextual()) {
            String t = n.asText();
            return StringUtils.hasText(t) ? t : null;
        }
        if (n.isNumber()) {
            return n.asText();
        }
        return null;
    }

    /** 从 payload 读取 imageUrls 数组；若为空可读单字段 imageUrl。 */
    private static List<String> resolveImageUrlList(JsonNode root, boolean includeSingleImageUrl) {
        List<String> urls = new ArrayList<>();
        JsonNode arr = root.get("imageUrls");
        if (arr != null && arr.isArray()) {
            for (JsonNode e : arr) {
                if (e != null && e.isTextual() && StringUtils.hasText(e.asText())) {
                    urls.add(e.asText().trim());
                }
            }
        }
        if (includeSingleImageUrl && urls.isEmpty()) {
            String one = text(root, "imageUrl");
            if (StringUtils.hasText(one)) {
                urls.add(one.trim());
            }
        }
        return urls;
    }

    private static boolean hasAnyMultipart(MultipartFile single, MultipartFile[] files) {
        if (single != null && !single.isEmpty()) {
            return true;
        }
        if (files != null) {
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static MultipartFile resolveOneImage(MultipartFile single, MultipartFile[] files, boolean required) {
        if (single != null && !single.isEmpty()) {
            return single;
        }
        if (files != null) {
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) {
                    return f;
                }
            }
        }
        if (required) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请上传图片：表单字段 file 或 files");
        }
        return null;
    }

    private static MultipartFile[] resolveManyImages(MultipartFile[] files, boolean required) {
        if (files == null || files.length == 0) {
            if (required) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "请上传图片：表单字段 files（可多文件）");
            }
            return new MultipartFile[0];
        }
        List<MultipartFile> list = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f != null && !f.isEmpty()) {
                list.add(f);
            }
        }
        if (list.isEmpty() && required) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "files 中无有效图片");
        }
        return list.toArray(new MultipartFile[0]);
    }
}
