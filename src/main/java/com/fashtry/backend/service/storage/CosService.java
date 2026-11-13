package com.fashtry.backend.service.storage;

import com.fashtry.backend.bean.vo.PublicModelImageUploadVO;
import com.fashtry.backend.common.config.CosProperties;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.security.SecurityUtils;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URL;
import java.util.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 腾讯云 COS 对象存储服务
 * 路径格式: {prefix}{year}/{month}/{uuid}.{ext}
 * - business: business/2025/03/xxx.jpg（模特照、衣服照等）
 * - other: other/2025/03/xxx.png（用户头像等）
 */
@Service
public class CosService {

    private static final Logger log = LoggerFactory.getLogger(CosService.class);
    private static final String FOLDER_BUSINESS = "business/";
    private static final String FOLDER_OTHER = "other/";
    /** 试穿结果图目录 */
    private static final String FOLDER_TRYON = "tryon/";
    /** Gemini 等 AI 生成图（与 FASHN 试穿结果区分） */
    private static final String FOLDER_GEMINI_GENERATED = "tryon/gemini/";
    /** 服装精修：相关存储路径（Gemini 任务等） */
    private static final String FOLDER_PRODUCT_RETOUCH_ANALYSIS = "tryon/gemini/product-retouch/";
    /** Veo 等生成的模特展示视频 MP4 */
    private static final String FOLDER_MODEL_VIDEO = "model-video/";
    /** 试穿缩略图目录 */
    private static final String FOLDER_TRYON_THUMB = "tryon/thumb/";
    /** Product to Model 结果图目录 */
    private static final String FOLDER_PRODUCT_TO_MODEL = "product-to-model/";
    /** Product to Model 缩略图目录 */
    private static final String FOLDER_PRODUCT_TO_MODEL_THUMB = "product-to-model/thumb/";
    /** 公共模特库原图 */
    private static final String FOLDER_PUBLIC_MODEL = "public-model/";
    /** 公共模特库缩略图（服务端压缩生成） */
    private static final String FOLDER_PUBLIC_MODEL_THUMB = "public-model/thumb/";
    /** 公共服装库原图 */
    private static final String FOLDER_PUBLIC_GARMENT = "public-garment/";
    /** 公共服装库缩略图 */
    private static final String FOLDER_PUBLIC_GARMENT_THUMB = "public-garment/thumb/";
    /** 品牌方服装库原图 */
    private static final String FOLDER_BRAND_GARMENT = "brand-garment/";
    /** 品牌方服装库缩略图 */
    private static final String FOLDER_BRAND_GARMENT_THUMB = "brand-garment/thumb/";
    /** 品牌 Logo */
    private static final String FOLDER_BRAND_LOGO = "brand-logo/";
    /** 品牌入驻：营业执照 */
    private static final String FOLDER_BRAND_BUSINESS_LICENSE = "brand-business-license/";
    /** 品牌入驻：法人身份证正面 */
    private static final String FOLDER_BRAND_LEGAL_ID_FRONT = "brand-legal-id-front/";
    /** 品牌入驻：法人身份证反面 */
    private static final String FOLDER_BRAND_LEGAL_ID_BACK = "brand-legal-id-back/";
    /** 缩略图最大宽度 */
    private static final int THUMB_MAX_WIDTH = 200;

    /** 允许的图片类型 */
    private static final String[] ALLOWED_IMAGE_TYPES = {"image/jpeg", "image/png", "image/gif", "image/webp"};

    private static final String CATEGORY_BUSINESS = "business";
    private static final String CATEGORY_OTHER = "other";

    private final COSClient cosClient;
    private final CosProperties cosProperties;
    private final UserUploadRecordService userUploadRecordService;
    private final RestTemplate restTemplate;
    private final CosImageModerationService cosImageModerationService;

    public CosService(COSClient cosClient, CosProperties cosProperties,
                      UserUploadRecordService userUploadRecordService, RestTemplate restTemplate,
                      CosImageModerationService cosImageModerationService) {
        this.cosClient = cosClient;
        this.cosProperties = cosProperties;
        this.userUploadRecordService = userUploadRecordService;
        this.restTemplate = restTemplate;
        this.cosImageModerationService = cosImageModerationService;
    }

    /**
     * 上传业务文件（模特照、衣服照等）到 business/ 目录
     */
    public String uploadBusiness(MultipartFile file) {
        return upload(file, FOLDER_BUSINESS, CATEGORY_BUSINESS, false);
    }

    /**
     * @param ciImageModeration 为 true 且配置开启 CI 时，对桶内对象做同步图片审核（用于微信小程序上传）
     */
    public String uploadBusiness(MultipartFile file, boolean ciImageModeration) {
        return upload(file, FOLDER_BUSINESS, CATEGORY_BUSINESS, ciImageModeration);
    }

    /** 直传 COS 成功后确认写入 user_upload_records（须登录） */
    public void confirmPresignUpload(
            String fileUrl, String objectKey, String category, Long fileSize, String originalName) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请先登录");
        }
        if (!StringUtils.hasText(fileUrl)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "fileUrl 不能为空");
        }
        String url = fileUrl.trim();
        if (!isOurCosFileUrl(url)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "无效的文件地址");
        }
        String cat = StringUtils.hasText(category) && CATEGORY_OTHER.equalsIgnoreCase(category.trim())
                ? CATEGORY_OTHER
                : CATEGORY_BUSINESS;
        userUploadRecordService.registerIfAbsent(userId, url, cat, originalName, fileSize);
    }

    private boolean isOurCosFileUrl(String urlString) {
        if (!StringUtils.hasText(urlString) || !StringUtils.hasText(cosProperties.getDomain())) {
            return false;
        }
        try {
            URI parsed = URI.create(urlString.trim());
            String host = parsed.getHost();
            if (!StringUtils.hasText(host)) {
                return false;
            }
            String cfg = cosProperties.getDomain().trim();
            if (!cfg.contains("://")) {
                cfg = "https://" + cfg;
            }
            URI base = URI.create(cfg.endsWith("/") ? cfg.substring(0, cfg.length() - 1) : cfg);
            return StringUtils.hasText(base.getHost()) && host.equalsIgnoreCase(base.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    /** 后台：品牌 Logo 上传到 brand-logo/（≤5MB，jpg/png/gif/webp） */
    public String uploadBrandLogo(MultipartFile file) {
        validateFile(file);
        return upload(file, FOLDER_BRAND_LOGO, CATEGORY_BUSINESS, false);
    }

    /** 后台：营业执照 */
    public String uploadBrandBusinessLicense(MultipartFile file) {
        validateFile(file);
        return upload(file, FOLDER_BRAND_BUSINESS_LICENSE, CATEGORY_BUSINESS, false);
    }

    /** 后台：法人身份证正面 */
    public String uploadBrandLegalIdFront(MultipartFile file) {
        validateFile(file);
        return upload(file, FOLDER_BRAND_LEGAL_ID_FRONT, CATEGORY_BUSINESS, false);
    }

    /** 后台：法人身份证反面 */
    public String uploadBrandLegalIdBack(MultipartFile file) {
        validateFile(file);
        return upload(file, FOLDER_BRAND_LEGAL_ID_BACK, CATEGORY_BUSINESS, false);
    }

    /**
     * 生成业务文件直传 COS 的预签名 PUT URL（浏览器直传）。
     * <p>
     * 注意：需要在 COS 桶上配置 CORS 允许 PUT、并允许带必要的请求头（如 Content-Type）。
     */
    public com.fashtry.backend.bean.vo.UploadPresignVO presignBusinessPut(String fileName, String contentType, long fileSize) {
        validateUploadMetaWithLimit(fileName, contentType, fileSize, USER_IMAGE_MAX_BYTES);
        return presignPut(FOLDER_BUSINESS, fileName, contentType, 10 * 60 * 1000L);
    }

    /** 用户头像等其它文件直传（与 uploadOther 同目录，最大 15MB） */
    public com.fashtry.backend.bean.vo.UploadPresignVO presignOtherPut(String fileName, String contentType, long fileSize) {
        validateUploadMetaWithLimit(fileName, contentType, fileSize, USER_IMAGE_MAX_BYTES);
        return presignPut(FOLDER_OTHER, fileName, contentType, 10 * 60 * 1000L);
    }

    /** 后台：公共模特库原图直传（最大 10MB） */
    public com.fashtry.backend.bean.vo.UploadPresignVO presignPublicModelPut(String fileName, String contentType, long fileSize) {
        validateUploadMetaWithLimit(fileName, contentType, fileSize, 10L * 1024 * 1024);
        return presignPut(FOLDER_PUBLIC_MODEL, fileName, contentType, 10 * 60 * 1000L);
    }

    /** 后台：公共模特库缩略图直传（建议 JPEG，最大 2MB） */
    public com.fashtry.backend.bean.vo.UploadPresignVO presignPublicModelThumbPut(String fileName, String contentType, long fileSize) {
        validateUploadMetaWithLimit(fileName, contentType, fileSize, 2L * 1024 * 1024);
        return presignPut(FOLDER_PUBLIC_MODEL_THUMB, fileName, contentType, 10 * 60 * 1000L);
    }

    /** 后台：公共服装库原图直传（最大 10MB） */
    public com.fashtry.backend.bean.vo.UploadPresignVO presignPublicGarmentPut(String fileName, String contentType, long fileSize) {
        validateUploadMetaWithLimit(fileName, contentType, fileSize, 10L * 1024 * 1024);
        return presignPut(FOLDER_PUBLIC_GARMENT, fileName, contentType, 10 * 60 * 1000L);
    }

    /** 后台：公共服装库缩略图直传（建议 JPEG，最大 2MB） */
    public com.fashtry.backend.bean.vo.UploadPresignVO presignPublicGarmentThumbPut(String fileName, String contentType, long fileSize) {
        validateUploadMetaWithLimit(fileName, contentType, fileSize, 2L * 1024 * 1024);
        return presignPut(FOLDER_PUBLIC_GARMENT_THUMB, fileName, contentType, 10 * 60 * 1000L);
    }

    /** 后台：品牌服装库原图直传（最大 10MB） */
    public com.fashtry.backend.bean.vo.UploadPresignVO presignBrandGarmentPut(String fileName, String contentType, long fileSize) {
        validateUploadMetaWithLimit(fileName, contentType, fileSize, 10L * 1024 * 1024);
        return presignPut(FOLDER_BRAND_GARMENT, fileName, contentType, 10 * 60 * 1000L);
    }

    /** 后台：品牌服装库缩略图直传（建议 JPEG，最大 2MB） */
    public com.fashtry.backend.bean.vo.UploadPresignVO presignBrandGarmentThumbPut(String fileName, String contentType, long fileSize) {
        validateUploadMetaWithLimit(fileName, contentType, fileSize, 2L * 1024 * 1024);
        return presignPut(FOLDER_BRAND_GARMENT_THUMB, fileName, contentType, 10 * 60 * 1000L);
    }

    /**
     * 后台品牌服装：上传原图并生成缩略图（目录 brand-garment/）。
     */
    public PublicModelImageUploadVO uploadBrandGarmentImage(MultipartFile file) {
        validatePublicModelFile(file);
        try {
            byte[] fullBytes = file.getBytes();
            BufferedImage probe = ImageIO.read(new ByteArrayInputStream(fullBytes));
            if (probe == null) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "无法解析的图片文件");
            }
            String keyFull = buildObjectKey(FOLDER_BRAND_GARMENT, file.getOriginalFilename());
            String contentType = file.getContentType();
            if (!StringUtils.hasText(contentType)) {
                contentType = "image/jpeg";
            }
            ObjectMetadata metaFull = new ObjectMetadata();
            metaFull.setContentLength(fullBytes.length);
            metaFull.setContentType(contentType);
            try (InputStream is = new ByteArrayInputStream(fullBytes)) {
                PutObjectRequest request = new PutObjectRequest(cosProperties.getBucketName(), keyFull, is, metaFull);
                cosClient.putObject(request);
            }
            String imageUrl = buildFileUrl(keyFull);

            byte[] thumbBytes = buildJpegThumbnail(fullBytes);
            if (thumbBytes == null || thumbBytes.length == 0) {
                throw new BusinessException(ResultCode.UPLOAD_FAIL, "缩略图生成失败");
            }
            String keyThumb = buildObjectKey(FOLDER_BRAND_GARMENT_THUMB, "thumb.jpg");
            ObjectMetadata metaThumb = new ObjectMetadata();
            metaThumb.setContentLength(thumbBytes.length);
            metaThumb.setContentType("image/jpeg");
            try (InputStream is = new ByteArrayInputStream(thumbBytes)) {
                PutObjectRequest request = new PutObjectRequest(cosProperties.getBucketName(), keyThumb, is, metaThumb);
                cosClient.putObject(request);
            }
            String thumbUrl = buildFileUrl(keyThumb);
            return new PublicModelImageUploadVO(imageUrl, thumbUrl);
        } catch (BusinessException e) {
            throw e;
        } catch (CosServiceException e) {
            log.error("COS 上传品牌服装图失败: code={}, msg={}", e.getErrorCode(), e.getErrorMessage());
            throw new BusinessException(ResultCode.UPLOAD_FAIL);
        } catch (Exception e) {
            log.error("品牌服装图上传失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.UPLOAD_FAIL);
        }
    }

    /**
     * 上传其他文件（用户头像等）到 other/ 目录
     */
    public String uploadOther(MultipartFile file) {
        return upload(file, FOLDER_OTHER, CATEGORY_OTHER, false);
    }

    public String uploadOther(MultipartFile file, boolean ciImageModeration) {
        return upload(file, FOLDER_OTHER, CATEGORY_OTHER, ciImageModeration);
    }

    /**
     * 后台公共模特：上传原图到 COS，并本地生成 JPEG 缩略图（最大宽度 {@link #THUMB_MAX_WIDTH}）后上传，无需单独传缩略图。
     */
    public PublicModelImageUploadVO uploadPublicModelImage(MultipartFile file) {
        validatePublicModelFile(file);
        try {
            byte[] fullBytes = file.getBytes();
            BufferedImage probe = ImageIO.read(new ByteArrayInputStream(fullBytes));
            if (probe == null) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "无法解析的图片文件");
            }
            String keyFull = buildObjectKey(FOLDER_PUBLIC_MODEL, file.getOriginalFilename());
            String contentType = file.getContentType();
            if (!StringUtils.hasText(contentType)) {
                contentType = "image/jpeg";
            }
            ObjectMetadata metaFull = new ObjectMetadata();
            metaFull.setContentLength(fullBytes.length);
            metaFull.setContentType(contentType);
            try (InputStream is = new ByteArrayInputStream(fullBytes)) {
                PutObjectRequest request = new PutObjectRequest(cosProperties.getBucketName(), keyFull, is, metaFull);
                cosClient.putObject(request);
            }
            String imageUrl = buildFileUrl(keyFull);

            byte[] thumbBytes = buildJpegThumbnail(fullBytes);
            if (thumbBytes == null || thumbBytes.length == 0) {
                throw new BusinessException(ResultCode.UPLOAD_FAIL, "缩略图生成失败");
            }
            String keyThumb = buildObjectKey(FOLDER_PUBLIC_MODEL_THUMB, "thumb.jpg");
            ObjectMetadata metaThumb = new ObjectMetadata();
            metaThumb.setContentLength(thumbBytes.length);
            metaThumb.setContentType("image/jpeg");
            try (InputStream is = new ByteArrayInputStream(thumbBytes)) {
                PutObjectRequest request = new PutObjectRequest(cosProperties.getBucketName(), keyThumb, is, metaThumb);
                cosClient.putObject(request);
            }
            String thumbUrl = buildFileUrl(keyThumb);
            return new PublicModelImageUploadVO(imageUrl, thumbUrl);
        } catch (BusinessException e) {
            throw e;
        } catch (CosServiceException e) {
            log.error("COS 上传公共模特图失败: code={}, msg={}", e.getErrorCode(), e.getErrorMessage());
            throw new BusinessException(ResultCode.UPLOAD_FAIL);
        } catch (Exception e) {
            log.error("公共模特图上传失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.UPLOAD_FAIL);
        }
    }

    /**
     * 后台公共服装库：上传原图并生成缩略图（与公共模特逻辑一致，目录不同）。
     */
    public PublicModelImageUploadVO uploadPublicGarmentImage(MultipartFile file) {
        validatePublicModelFile(file);
        try {
            byte[] fullBytes = file.getBytes();
            BufferedImage probe = ImageIO.read(new ByteArrayInputStream(fullBytes));
            if (probe == null) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "无法解析的图片文件");
            }
            String keyFull = buildObjectKey(FOLDER_PUBLIC_GARMENT, file.getOriginalFilename());
            String contentType = file.getContentType();
            if (!StringUtils.hasText(contentType)) {
                contentType = "image/jpeg";
            }
            ObjectMetadata metaFull = new ObjectMetadata();
            metaFull.setContentLength(fullBytes.length);
            metaFull.setContentType(contentType);
            try (InputStream is = new ByteArrayInputStream(fullBytes)) {
                PutObjectRequest request = new PutObjectRequest(cosProperties.getBucketName(), keyFull, is, metaFull);
                cosClient.putObject(request);
            }
            String imageUrl = buildFileUrl(keyFull);

            byte[] thumbBytes = buildJpegThumbnail(fullBytes);
            if (thumbBytes == null || thumbBytes.length == 0) {
                throw new BusinessException(ResultCode.UPLOAD_FAIL, "缩略图生成失败");
            }
            String keyThumb = buildObjectKey(FOLDER_PUBLIC_GARMENT_THUMB, "thumb.jpg");
            ObjectMetadata metaThumb = new ObjectMetadata();
            metaThumb.setContentLength(thumbBytes.length);
            metaThumb.setContentType("image/jpeg");
            try (InputStream is = new ByteArrayInputStream(thumbBytes)) {
                PutObjectRequest request = new PutObjectRequest(cosProperties.getBucketName(), keyThumb, is, metaThumb);
                cosClient.putObject(request);
            }
            String thumbUrl = buildFileUrl(keyThumb);
            return new PublicModelImageUploadVO(imageUrl, thumbUrl);
        } catch (BusinessException e) {
            throw e;
        } catch (CosServiceException e) {
            log.error("COS 上传公共服装图失败: code={}, msg={}", e.getErrorCode(), e.getErrorMessage());
            throw new BusinessException(ResultCode.UPLOAD_FAIL);
        } catch (Exception e) {
            log.error("公共服装图上传失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.UPLOAD_FAIL);
        }
    }

    private void validatePublicModelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请选择要上传的图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !isAllowedType(contentType)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "仅支持 jpg、png、gif、webp 格式图片");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "图片大小不能超过 10MB");
        }
    }

    /** 统一输出 JPEG 缩略图，便于列表展示与体积控制 */
    private byte[] buildJpegThumbnail(byte[] imageBytes) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (src == null) {
            return null;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        int tw = Math.min(w, THUMB_MAX_WIDTH);
        int th = (int) Math.round(h * (double) tw / w);
        BufferedImage thumb = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, tw, th);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, tw, th, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(thumb, "jpg", out);
        return out.toByteArray();
    }

    /**
     * 从图片 URL 下载并上传到 COS（试穿结果图）
     * <p>
     * 用于定时任务将 FASHN 生成的结果图转存到腾讯云 COS
     * </p>
     *
     * @param imageUrl 图片 URL（如 FASHN CDN 链接）
     * @return COS 访问 URL
     */
    /**
     * 将 Gemini 等生成的图片字节写入 COS（{@code tryon/gemini/}），返回公网访问 URL。
     *
     * @param imageBytes 原始图片字节
     * @param mimeType   如 image/png、image/jpeg；可空则按 png 处理
     */
    /**
     * 上传 Veo 等生成的 MP4 到 {@code model-video/}，返回公网 URL。
     */
    public String uploadModelVideoMp4(byte[] videoBytes) {
        if (videoBytes == null || videoBytes.length == 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "视频数据为空");
        }
        String key = buildObjectKey(FOLDER_MODEL_VIDEO, "out.mp4");
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(videoBytes.length);
        metadata.setContentType("video/mp4");
        try (InputStream is = new ByteArrayInputStream(videoBytes)) {
            PutObjectRequest request = new PutObjectRequest(cosProperties.getBucketName(), key, is, metadata);
            cosClient.putObject(request);
            return buildFileUrl(key);
        } catch (CosServiceException e) {
            log.error("COS 上传模特视频失败: code={}, msg={}", e.getErrorCode(), e.getErrorMessage());
            throw new BusinessException(ResultCode.UPLOAD_FAIL);
        } catch (CosClientException | IOException e) {
            log.error("COS 上传模特视频失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.UPLOAD_FAIL);
        }
    }

    /** 试穿结果图约 2～3MB，公网上行慢时 COS 返回 UserNetworkTooSlow；有限次重试可显著降低误判失败率 */
    private static final int GEMINI_IMAGE_COS_UPLOAD_ATTEMPTS = 4;

    /**
     * 商拍修图预检：将多模态分析结果（通常为 JSON 文本的 UTF-8 字节）写入 {@code tryon/gemini/product-retouch/}，返回公网 URL。
     */
    public String uploadProductRetouchAnalysisJson(String jsonText) {
        if (jsonText == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "分析内容为空");
        }
        byte[] jsonBytes = jsonText.getBytes(StandardCharsets.UTF_8);
        if (jsonBytes.length == 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "分析内容为空");
        }
        if (jsonBytes.length > 4 * 1024 * 1024) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "分析结果过大");
        }
        String key = buildObjectKey(FOLDER_PRODUCT_RETOUCH_ANALYSIS, "analysis.json");
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(jsonBytes.length);
        metadata.setContentType("application/json; charset=utf-8");
        for (int attempt = 1; attempt <= GEMINI_IMAGE_COS_UPLOAD_ATTEMPTS; attempt++) {
            try (InputStream is = new ByteArrayInputStream(jsonBytes)) {
                PutObjectRequest request = new PutObjectRequest(cosProperties.getBucketName(), key, is, metadata);
                cosClient.putObject(request);
                return buildFileUrl(key);
            } catch (CosServiceException e) {
                if (attempt < GEMINI_IMAGE_COS_UPLOAD_ATTEMPTS && isRetryableGeminiCosUplink(e)) {
                    long backoffMs = 2000L * attempt;
                    log.warn(
                            "COS 上传商拍分析 JSON 将重试 ({}/{}): code={} msg={} backoffMs={}",
                            attempt,
                            GEMINI_IMAGE_COS_UPLOAD_ATTEMPTS,
                            e.getErrorCode(),
                            e.getErrorMessage(),
                            backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException(ResultCode.UPLOAD_FAIL);
                    }
                    continue;
                }
                log.error("COS 上传商拍分析失败: code={}, msg={}", e.getErrorCode(), e.getErrorMessage());
                throw new BusinessException(ResultCode.UPLOAD_FAIL);
            } catch (CosClientException | IOException e) {
                log.error("COS 上传商拍分析失败: {}", e.getMessage());
                throw new BusinessException(ResultCode.UPLOAD_FAIL);
            }
        }
        throw new BusinessException(ResultCode.UPLOAD_FAIL);
    }

    public String uploadGeminiGeneratedImage(byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "图片数据为空");
        }
        String ext = extensionFromMime(mimeType);
        String key = buildObjectKey(FOLDER_GEMINI_GENERATED, "result." + ext);
        String ct = org.springframework.util.StringUtils.hasText(mimeType) ? mimeType.trim() : "image/png";
        if (!ct.startsWith("image/")) {
            ct = "image/png";
        }
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(imageBytes.length);
        metadata.setContentType(ct);
        for (int attempt = 1; attempt <= GEMINI_IMAGE_COS_UPLOAD_ATTEMPTS; attempt++) {
            try (InputStream is = new ByteArrayInputStream(imageBytes)) {
                PutObjectRequest request = new PutObjectRequest(cosProperties.getBucketName(), key, is, metadata);
                cosClient.putObject(request);
                return buildFileUrl(key);
            } catch (CosServiceException e) {
                if (attempt < GEMINI_IMAGE_COS_UPLOAD_ATTEMPTS && isRetryableGeminiCosUplink(e)) {
                    long backoffMs = 2000L * attempt;
                    log.warn(
                            "COS 上传 Gemini 生成图将重试 ({}/{}): code={} msg={} backoffMs={}",
                            attempt,
                            GEMINI_IMAGE_COS_UPLOAD_ATTEMPTS,
                            e.getErrorCode(),
                            e.getErrorMessage(),
                            backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException(ResultCode.UPLOAD_FAIL);
                    }
                    continue;
                }
                log.error("COS 上传 Gemini 生成图失败: code={}, msg={}", e.getErrorCode(), e.getErrorMessage());
                throw new BusinessException(ResultCode.UPLOAD_FAIL);
            } catch (CosClientException | IOException e) {
                log.error("COS 上传 Gemini 生成图失败: {}", e.getMessage());
                throw new BusinessException(ResultCode.UPLOAD_FAIL);
            }
        }
        throw new BusinessException(ResultCode.UPLOAD_FAIL);
    }

    /** 上行瞬断、服务端判定过慢等，短等待后重试往往可成功 */
    private static boolean isRetryableGeminiCosUplink(CosServiceException e) {
        if (e == null || e.getErrorCode() == null) {
            return false;
        }
        String code = e.getErrorCode();
        return "UserNetworkTooSlow".equals(code)
                || "RequestTimeout".equals(code)
                || "SlowDown".equals(code)
                || "ServiceUnavailable".equals(code);
    }

    private static String extensionFromMime(String mime) {
        if (!StringUtils.hasText(mime)) {
            return "png";
        }
        String m = mime.trim().toLowerCase();
        if ("image/jpeg".equals(m) || "image/jpg".equals(m)) {
            return "jpg";
        }
        if ("image/webp".equals(m)) {
            return "webp";
        }
        if ("image/gif".equals(m)) {
            return "gif";
        }
        return "png";
    }

    public String uploadTryonResultFromUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "图片 URL 不能为空");
        }
        byte[] bytes = restTemplate.getForObject(imageUrl, byte[].class);
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ResultCode.UPLOAD_FAIL, "下载图片失败");
        }
        String ext = (imageUrl.contains(".jpeg") || imageUrl.contains(".jpg")) ? "jpg" : "png";
        String key = buildObjectKey(FOLDER_TRYON, "result." + ext);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType("jpg".equals(ext) ? "image/jpeg" : "image/png");
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            PutObjectRequest request = new PutObjectRequest(cosProperties.getBucketName(), key, is, metadata);
            cosClient.putObject(request);
            return buildFileUrl(key);
        } catch (CosServiceException e) {
            log.error("COS 上传试穿结果失败: code={}, msg={}", e.getErrorCode(), e.getErrorMessage());
            throw new BusinessException(ResultCode.UPLOAD_FAIL);
        } catch (CosClientException | IOException e) {
            log.error("COS 上传试穿结果失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.UPLOAD_FAIL);
        }
    }

    /**
     * 从图片 URL 下载并上传到 COS（Product to Model 结果图）
     */
    public String uploadProductToModelResultFromUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "图片 URL 不能为空");
        }
        byte[] bytes = restTemplate.getForObject(imageUrl, byte[].class);
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ResultCode.UPLOAD_FAIL, "下载图片失败");
        }
        String ext = (imageUrl.contains(".jpeg") || imageUrl.contains(".jpg")) ? "jpg" : "png";
        String key = buildObjectKey(FOLDER_PRODUCT_TO_MODEL, "result." + ext);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType("jpg".equals(ext) ? "image/jpeg" : "image/png");
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            PutObjectRequest request = new PutObjectRequest(cosProperties.getBucketName(), key, is, metadata);
            cosClient.putObject(request);
            return buildFileUrl(key);
        } catch (CosServiceException e) {
            log.error("COS 上传 ProductToModel 结果失败: code={}, msg={}", e.getErrorCode(), e.getErrorMessage());
            throw new BusinessException(ResultCode.UPLOAD_FAIL);
        } catch (CosClientException | IOException e) {
            log.error("COS 上传 ProductToModel 结果失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.UPLOAD_FAIL);
        }
    }

    /**
     * 从图片 URL 下载、生成缩略图并上传到 COS（Product to Model）
     */
    public String uploadProductToModelThumbnailFromUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }
        try {
            byte[] bytes = restTemplate.getForObject(imageUrl, byte[].class);
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            byte[] thumbBytes = resizeToThumbnail(bytes);
            if (thumbBytes == null || thumbBytes.length == 0) {
                return null;
            }
            String key = buildObjectKey(FOLDER_PRODUCT_TO_MODEL_THUMB, "thumb.jpg");
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(thumbBytes.length);
            metadata.setContentType("image/jpeg");
            try (InputStream is = new ByteArrayInputStream(thumbBytes)) {
                PutObjectRequest request = new PutObjectRequest(cosProperties.getBucketName(), key, is, metadata);
                cosClient.putObject(request);
                return buildFileUrl(key);
            }
        } catch (Exception e) {
            log.warn("生成 ProductToModel 缩略图失败: url={}, {}", imageUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 从图片 URL 下载、生成缩略图并上传到 COS
     *
     * @param imageUrl 原图 URL
     * @return 缩略图 COS 访问 URL，失败返回 null
     */
    public String uploadTryonThumbnailFromUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }
        try {
            byte[] bytes = restTemplate.getForObject(imageUrl, byte[].class);
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            byte[] thumbBytes = resizeToThumbnail(bytes);
            if (thumbBytes == null || thumbBytes.length == 0) {
                return null;
            }
            String key = buildObjectKey(FOLDER_TRYON_THUMB, "thumb.jpg");
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(thumbBytes.length);
            metadata.setContentType("image/jpeg");
            try (InputStream is = new ByteArrayInputStream(thumbBytes)) {
                PutObjectRequest request = new PutObjectRequest(cosProperties.getBucketName(), key, is, metadata);
                cosClient.putObject(request);
                return buildFileUrl(key);
            }
        } catch (Exception e) {
            log.warn("生成试穿缩略图失败: url={}, {}", imageUrl, e.getMessage());
            return null;
        }
    }

    private byte[] resizeToThumbnail(byte[] imageBytes) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (src == null) {
            return null;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= THUMB_MAX_WIDTH) {
            return imageBytes;
        }
        int thumbH = (int) (h * (double) THUMB_MAX_WIDTH / w);
        BufferedImage thumb = new BufferedImage(THUMB_MAX_WIDTH, thumbH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, THUMB_MAX_WIDTH, thumbH, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(thumb, "jpg", out);
        return out.toByteArray();
    }

    private String upload(MultipartFile file, String folderPrefix, String category, boolean ciImageModeration) {
        validateFile(file, USER_IMAGE_MAX_BYTES);
        String key = buildObjectKey(folderPrefix, file.getOriginalFilename());
        try (InputStream is = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            String contentType = file.getContentType();
            if (StringUtils.hasText(contentType)) {
                metadata.setContentType(contentType);
            }
            PutObjectRequest request = new PutObjectRequest(cosProperties.getBucketName(), key, is, metadata);
            cosClient.putObject(request);
            String url = buildFileUrl(key);
            if (ciImageModeration && cosProperties.isCiModerationEnabled()) {
                log.info("[CI] CosService.upload: run moderation objectKey={} category={}", key, category);
                try {
                    cosImageModerationService.assertImagePassed(key);
                } catch (BusinessException e) {
                    log.warn("[CI] CosService.upload: moderation rejected key={} msg={}", key, e.getMessage());
                    deleteObjectQuietly(key);
                    throw e;
                }
            } else {
                log.info("[CI] CosService.upload: skip moderation key={} ciClientHeader={} ciEnabled={}",
                        key, ciImageModeration, cosProperties.isCiModerationEnabled());
            }
            Long userId = SecurityUtils.getCurrentUserId();
            if (userId != null) {
                userUploadRecordService.registerIfAbsent(
                        userId, url, category, file.getOriginalFilename(), file.getSize());
            }
            return url;
        } catch (CosServiceException e) {
            log.error("COS 服务异常: code={}, msg={}, requestId={}", e.getErrorCode(), e.getErrorMessage(), e.getRequestId());
            throw new BusinessException(ResultCode.UPLOAD_FAIL);
        } catch (IOException | CosClientException e) {
            log.error("COS 上传失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.UPLOAD_FAIL);
        }
    }

    private void deleteObjectQuietly(String key) {
        try {
            cosClient.deleteObject(cosProperties.getBucketName(), key);
        } catch (Exception e) {
            log.warn("删除未通过审核的对象失败 key={}: {}", key, e.getMessage());
        }
    }

    /** 用户业务图、头像。品牌证件仍用 {@link #BRAND_FILE_MAX_BYTES}。 */
    private static final long USER_IMAGE_MAX_BYTES = 15L * 1024 * 1024;

    private static final long BRAND_FILE_MAX_BYTES = 5L * 1024 * 1024;

    private void validateFile(MultipartFile file) {
        validateFile(file, BRAND_FILE_MAX_BYTES);
    }

    private void validateFile(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请选择要上传的文件");
        }
        String contentType = file.getContentType();
        if (contentType == null || !isAllowedType(contentType)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "仅支持 jpg、png、gif、webp 格式图片");
        }
        if (file.getSize() > maxBytes) {
            long mb = Math.max(1, maxBytes / (1024 * 1024));
            throw new BusinessException(ResultCode.PARAM_INVALID, "文件大小不能超过 " + mb + "MB");
        }
    }

    private void validateUploadMetaWithLimit(String fileName, String contentType, long fileSize, long maxBytes) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "文件名不能为空");
        }
        if (!StringUtils.hasText(contentType) || !isAllowedType(contentType.trim())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "仅支持 jpg、png、gif、webp 格式图片");
        }
        if (fileSize <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "文件大小无效");
        }
        if (fileSize > maxBytes) {
            long mb = Math.max(1, maxBytes / (1024 * 1024));
            throw new BusinessException(ResultCode.PARAM_INVALID, "文件大小不能超过 " + mb + "MB");
        }
    }

    private com.fashtry.backend.bean.vo.UploadPresignVO presignPut(String folderPrefix, String fileName, String contentType, long ttlMs) {
        String key = buildObjectKey(folderPrefix, fileName);
        try {
            Date expiration = new Date(System.currentTimeMillis() + Math.max(60_000L, ttlMs));
            GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(
                    cosProperties.getBucketName(), key, HttpMethodName.PUT);
            req.setExpiration(expiration);
            // 不强制签名 Content-Type，避免浏览器/SDK 差异导致签名不匹配
            URL url = cosClient.generatePresignedUrl(req);
            String fileUrl = buildFileUrl(key);
            return com.fashtry.backend.bean.vo.UploadPresignVO.builder()
                    .uploadUrl(url != null ? url.toString() : null)
                    .fileUrl(fileUrl)
                    .objectKey(key)
                    .expireAt(expiration.getTime())
                    .build();
        } catch (Exception e) {
            log.error("生成 COS 预签名 URL 失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "生成上传地址失败");
        }
    }

    private boolean isAllowedType(String contentType) {
        for (String t : ALLOWED_IMAGE_TYPES) {
            if (t.equalsIgnoreCase(contentType)) {
                return true;
            }
        }
        return false;
    }

    private String buildObjectKey(String folderPrefix, String originalFilename) {
        // 路径: {prefix}{year}/{month}/{uuid}.{ext}，如 business/2025/03/abc123.jpg
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/"));
        String prefix = folderPrefix + datePath;
        String ext = "jpg";
        if (StringUtils.hasText(originalFilename)) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot > 0 && dot < originalFilename.length() - 1) {
                ext = originalFilename.substring(dot + 1).toLowerCase();
            }
        }
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return prefix + uuid + "." + ext;
    }

    private String buildFileUrl(String key) {
        String domain = cosProperties.getDomain();
        if (domain.endsWith("/")) {
            return domain + key;
        }
        return domain + "/" + key;
    }

    /**
     * 穿搭视频首帧：仅允许本站 COS 公有读域名，且对象键以业务目录开头，防止任意 URL 被用作下载代理。
     */
    public boolean isAllowedModelVideoFrameUrl(String urlString) {
        if (!StringUtils.hasText(urlString) || !StringUtils.hasText(cosProperties.getDomain())) {
            return false;
        }
        final String trimmed = urlString.trim();
        URI parsed;
        try {
            parsed = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String host = parsed.getHost();
        if (!StringUtils.hasText(host)) {
            return false;
        }
        String cfg = cosProperties.getDomain().trim();
        if (!cfg.contains("://")) {
            cfg = "https://" + cfg;
        }
        URI base;
        try {
            base = URI.create(cfg.endsWith("/") ? cfg.substring(0, cfg.length() - 1) : cfg);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String baseHost = base.getHost();
        if (!StringUtils.hasText(baseHost) || !host.equalsIgnoreCase(baseHost)) {
            return false;
        }
        String path = parsed.getPath();
        if (path == null) {
            return false;
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path.startsWith(FOLDER_BUSINESS)
                || path.startsWith(FOLDER_TRYON)
                || path.startsWith(FOLDER_PRODUCT_TO_MODEL);
    }
}
