package com.fashtry.backend.controller.common;

import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.bean.vo.UploadResultVO;
import com.fashtry.backend.bean.dto.UploadPresignRequest;
import com.fashtry.backend.bean.dto.UploadConfirmRequest;
import com.fashtry.backend.bean.vo.UploadPresignVO;
import com.fashtry.backend.service.storage.CosService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 文件上传接口
 * - /business: 业务文件（模特照、衣服照等）→ business/ 目录
 * - /other: 其他文件（用户头像等）→ other/ 目录
 */
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    /** 仅微信小程序 multipart 上传携带，用于触发 COS 图片审核；Web/H5 直传或表单上传不传，不审核 */
    public static final String HEADER_FASHTRY_CLIENT = "X-Fashtry-Client";
    public static final String CLIENT_MP_WEIXIN = "mp-weixin";

    private final CosService cosService;

    public UploadController(CosService cosService) {
        this.cosService = cosService;
    }

    /**
     * 上传业务文件（模特照、衣服照等）
     * 存储路径: business/{uuid}.{ext}
     */
    @PostMapping("/business")
    public Result<UploadResultVO> uploadBusiness(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        boolean mp = CLIENT_MP_WEIXIN.equalsIgnoreCase(request.getHeader(HEADER_FASHTRY_CLIENT));
        String url = cosService.uploadBusiness(file, mp);
        return Result.success(UploadResultVO.builder().url(url).build());
    }

    /**
     * 直传 COS：获取业务文件预签名 PUT URL（前端拿到后直接 PUT 到 COS）。
     */
    @PostMapping("/business/presign")
    public Result<UploadPresignVO> presignBusiness(@Valid @RequestBody UploadPresignRequest body) {
        UploadPresignVO vo = cosService.presignBusinessPut(body.getFileName(), body.getContentType(), body.getFileSize());
        return Result.success(vo);
    }

    /** 直传 COS 成功后确认，写入 user_upload_records */
    @PostMapping("/business/confirm")
    public Result<Void> confirmBusiness(@Valid @RequestBody UploadConfirmRequest body) {
        cosService.confirmPresignUpload(
                body.getFileUrl(),
                body.getObjectKey(),
                body.getCategory() != null ? body.getCategory() : "business",
                body.getFileSize(),
                body.getFileName());
        return Result.success(null);
    }

    /**
     * 直传 COS：获取头像等其它文件预签名 PUT URL。
     */
    @PostMapping("/other/presign")
    public Result<UploadPresignVO> presignOther(@Valid @RequestBody UploadPresignRequest body) {
        UploadPresignVO vo = cosService.presignOtherPut(body.getFileName(), body.getContentType(), body.getFileSize());
        return Result.success(vo);
    }

    /** 直传 COS（other）成功后确认 */
    @PostMapping("/other/confirm")
    public Result<Void> confirmOther(@Valid @RequestBody UploadConfirmRequest body) {
        cosService.confirmPresignUpload(
                body.getFileUrl(),
                body.getObjectKey(),
                "other",
                body.getFileSize(),
                body.getFileName());
        return Result.success(null);
    }

    /**
     * 上传其他文件（用户头像等）
     * 存储路径: other/{uuid}.{ext}
     */
    @PostMapping("/other")
    public Result<UploadResultVO> uploadOther(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        boolean mp = CLIENT_MP_WEIXIN.equalsIgnoreCase(request.getHeader(HEADER_FASHTRY_CLIENT));
        String url = cosService.uploadOther(file, mp);
        return Result.success(UploadResultVO.builder().url(url).build());
    }
}
