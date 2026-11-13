package com.fashtry.backend.service.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashtry.backend.bean.vo.AdminTaskPreviewImageVO;
import com.fashtry.backend.bean.vo.GeminiImageResultJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 从任务 payload / result JSON 解析后台预览用图片 URL */
@Component
public class AdminTaskPreviewImageResolver {

    private static final Set<String> PAYLOAD_SKIP_KEYS =
            Set.of(
                    "prompt",
                    "secondPrompt",
                    "instruction",
                    "modelId",
                    "aspectRatio",
                    "imageSize",
                    "taskType",
                    "stylePreset",
                    "makeupPreset",
                    "outfitMode",
                    "outputCount",
                    "parentTaskId",
                    "parent_task_id",
                    "regenerateSlotIndex",
                    "regenerateSlotQueue",
                    "artDirection",
                    "ecomScene",
                    "modelGender",
                    "ageGroup",
                    "bodyType",
                    "modelStyleNote",
                    "faceMode",
                    "facePreset",
                    "framingMode",
                    "sceneVariantCount",
                    "backgroundColor",
                    "garmentScope",
                    "removeHanger",
                    "removePriceTag",
                    "removeBadge",
                    "shootHint",
                    "cropPreference",
                    "outputLanguage");

    private static final Map<String, String> FIELD_LABELS =
            Map.ofEntries(
                    Map.entry("sourceCosUrls", "输入图"),
                    Map.entry("sourceCosUrl", "源图"),
                    Map.entry("source_cos_urls", "输入图"),
                    Map.entry("source_cos_url", "源图"),
                    Map.entry("imageUrls", "输入图"),
                    Map.entry("imageUrl", "输入图"),
                    Map.entry("imageCosUrls", "参考图"),
                    Map.entry("image_cos_urls", "参考图"),
                    Map.entry("modelCosUrl", "模特图"),
                    Map.entry("model_cos_url", "模特图"),
                    Map.entry("modelUrl", "模特图"),
                    Map.entry("garmentCosUrl", "服装图"),
                    Map.entry("garment_cos_url", "服装图"),
                    Map.entry("garmentUrl", "服装图"),
                    Map.entry("maskCosUrl", "蒙版"),
                    Map.entry("mask_cos_url", "蒙版"),
                    Map.entry("referenceCosUrl", "参考图"),
                    Map.entry("reference_cos_url", "参考图"),
                    Map.entry("faceReferenceCosUrl", "参考脸图"),
                    Map.entry("face_reference_cos_url", "参考脸图"),
                    Map.entry("faceReferenceUrl", "参考脸图"),
                    Map.entry("flatImageUrl", "平铺图"),
                    Map.entry("garmentImageUrl", "服装图"));

    private final ObjectMapper objectMapper;

    public AdminTaskPreviewImageResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<AdminTaskPreviewImageVO> uploadImagesFromGeminiPayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(payloadJson.trim());
            if (root == null || !root.isObject()) {
                return List.of();
            }
            LinkedHashMap<String, AdminTaskPreviewImageVO> byUrl = new LinkedHashMap<>();
            root.fields()
                    .forEachRemaining(
                            e -> collectPayloadField(byUrl, e.getKey(), e.getValue()));
            return List.copyOf(byUrl.values());
        } catch (Exception e) {
            return List.of();
        }
    }

    public Long parentTaskIdFromPayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(payloadJson.trim());
            JsonNode idNode = root.get("parentTaskId");
            if (idNode == null || idNode.isNull()) {
                idNode = root.get("parent_task_id");
            }
            if (idNode == null || idNode.isNull()) {
                return null;
            }
            long id = idNode.asLong(0L);
            return id > 0 ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

    public List<AdminTaskPreviewImageVO> mergeUploadImages(
            List<AdminTaskPreviewImageVO> primary, List<AdminTaskPreviewImageVO> secondary) {
        LinkedHashMap<String, AdminTaskPreviewImageVO> byUrl = new LinkedHashMap<>();
        appendUploadImages(byUrl, primary);
        appendUploadImages(byUrl, secondary);
        return List.copyOf(byUrl.values());
    }

    public List<AdminTaskPreviewImageVO> resultImagesFromGeminiResultJson(String resultJson) {
        if (!StringUtils.hasText(resultJson)) {
            return List.of();
        }
        try {
            GeminiImageResultJson parsed =
                    objectMapper.readValue(resultJson.trim(), GeminiImageResultJson.class);
            if (parsed.getUrls() == null || parsed.getUrls().isEmpty()) {
                return List.of();
            }
            List<String> labels =
                    parsed.getImageLabels() != null ? parsed.getImageLabels() : List.of();
            List<AdminTaskPreviewImageVO> out = new ArrayList<>();
            for (int i = 0; i < parsed.getUrls().size(); i++) {
                String url = parsed.getUrls().get(i);
                if (!isHttpUrl(url)) {
                    continue;
                }
                String rawLabel = i < labels.size() ? labels.get(i) : null;
                String label = geminiResultLabel(rawLabel);
                if (!StringUtils.hasText(label)) {
                    label = "结果 " + (out.size() + 1);
                }
                out.add(new AdminTaskPreviewImageVO(url.trim(), label));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<AdminTaskPreviewImageVO> uploadImagesFromTryon(
            String modelUrl, String garmentUrl, String geminiPayloadJson) {
        LinkedHashMap<String, AdminTaskPreviewImageVO> byUrl = new LinkedHashMap<>();
        addSingleUrl(byUrl, modelUrl, "模特图");
        addSingleUrl(byUrl, garmentUrl, "服装图");
        appendUploadImages(byUrl, uploadImagesFromGeminiPayload(geminiPayloadJson));
        return List.copyOf(byUrl.values());
    }

    public List<AdminTaskPreviewImageVO> resultImagesFromTryon(
            String resultCosUrl, String resultUrl, String thumbUrl) {
        String url =
                firstNonBlank(resultCosUrl, resultUrl, thumbUrl);
        if (!isHttpUrl(url)) {
            return List.of();
        }
        return List.of(new AdminTaskPreviewImageVO(url.trim(), "试穿效果"));
    }

    public List<AdminTaskPreviewImageVO> uploadImagesFromVideoSource(String sourceImageUrl) {
        if (!isHttpUrl(sourceImageUrl)) {
            return List.of();
        }
        return List.of(new AdminTaskPreviewImageVO(sourceImageUrl.trim(), "起始帧图"));
    }

    private void appendUploadImages(
            LinkedHashMap<String, AdminTaskPreviewImageVO> byUrl, List<AdminTaskPreviewImageVO> items) {
        if (items == null) {
            return;
        }
        for (AdminTaskPreviewImageVO item : items) {
            addSingleUrl(byUrl, item.getUrl(), item.getLabel());
        }
    }

    private void collectPayloadField(
            LinkedHashMap<String, AdminTaskPreviewImageVO> byUrl, String key, JsonNode val) {
        if (val == null || val.isNull() || PAYLOAD_SKIP_KEYS.contains(key)) {
            return;
        }
        String baseLabel = labelForKey(key);
        if (val.isTextual()) {
            if (isImageUrlFieldKey(key)) {
                addSingleUrl(byUrl, val.asText(), baseLabel);
            }
            return;
        }
        if (val.isArray() && isImageUrlFieldKey(key)) {
            int i = 0;
            for (JsonNode item : val) {
                i++;
                if (item.isTextual()) {
                    addSingleUrl(byUrl, item.asText(), baseLabel + " " + i);
                } else if (item.isObject()) {
                    String nested =
                            firstNonBlank(
                                    text(item, "url"),
                                    text(item, "fileUrl"),
                                    text(item, "imageUrl"),
                                    text(item, "cosUrl"));
                    String nestedLabel = text(item, "label");
                    addSingleUrl(
                            byUrl,
                            nested,
                            StringUtils.hasText(nestedLabel) ? nestedLabel : baseLabel + " " + i);
                }
            }
        }
    }

    private void addSingleUrl(LinkedHashMap<String, AdminTaskPreviewImageVO> byUrl, String url, String label) {
        if (!isHttpUrl(url) || byUrl.containsKey(url.trim())) {
            return;
        }
        byUrl.put(url.trim(), new AdminTaskPreviewImageVO(url.trim(), StringUtils.hasText(label) ? label : "图片"));
    }

    private static boolean isHttpUrl(String s) {
        if (!StringUtils.hasText(s)) {
            return false;
        }
        String u = s.trim().toLowerCase();
        return u.startsWith("http://") || u.startsWith("https://");
    }

    private static boolean isImageUrlFieldKey(String key) {
        if (!StringUtils.hasText(key) || PAYLOAD_SKIP_KEYS.contains(key)) {
            return false;
        }
        String k = key.toLowerCase();
        return k.endsWith("url")
                || k.endsWith("urls")
                || k.contains("cosurl")
                || k.contains("_url")
                || k.contains("image");
    }

    private static String labelForKey(String key) {
        if (FIELD_LABELS.containsKey(key)) {
            return FIELD_LABELS.get(key);
        }
        String lower = key.toLowerCase();
        if (lower.contains("mask")) {
            return "蒙版";
        }
        if (lower.contains("face") && lower.contains("ref")) {
            return "参考脸图";
        }
        if (lower.contains("reference") || lower.contains("ref")) {
            return "参考图";
        }
        if (lower.contains("garment") || lower.contains("cloth")) {
            return "服装图";
        }
        if (lower.contains("model")) {
            return "模特图";
        }
        if (lower.contains("source") || lower.contains("input")) {
            return "输入图";
        }
        if (lower.contains("image") || lower.contains("url")) {
            return "图片";
        }
        return "图片";
    }

    private static String geminiResultLabel(String label) {
        if (!StringUtils.hasText(label)) {
            return "";
        }
        return switch (label) {
            case "MODEL_WEAR" -> "模特上身";
            case "ECOM_WHITE", "ECOM_WHITE_MAIN" -> "白底主图";
            case "ECOM_PREMIUM_MAIN" -> "高级主图";
            case "DETAIL_LONG_STRIP" -> "详情长图";
            default -> {
                if (label.startsWith("SCENE_")) {
                    yield "场景 " + label.substring("SCENE_".length());
                }
                if (label.startsWith("DETAIL_")) {
                    yield "细节 " + label.substring("DETAIL_".length());
                }
                if (label.startsWith("SELL_")) {
                    yield "卖点 " + label.substring("SELL_".length());
                }
                yield label;
            }
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isTextual() ? v.asText() : null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }
}
