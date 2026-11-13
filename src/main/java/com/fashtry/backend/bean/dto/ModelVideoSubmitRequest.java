package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 穿搭视频提交：{@code sourceType} 决定有效字段。
 * <ul>
 *   <li>TRYON — {@link #tryonTaskId}
 *   <li>IMAGE_EDIT — {@link #imageEditTaskId}
 *   <li>UPLOAD — {@link #sourceImageUrl}（须为本站 COS：business/、tryon/、product-to-model/ 等）
 * </ul>
 */
@Data
public class ModelVideoSubmitRequest {

    /**
     * TRYON | IMAGE_EDIT | UPLOAD，默认按 TRYON 解析
     */
    private String sourceType;

    /** sourceType=TRYON 时必填，字符串避免大整数精度丢失 */
    private String tryonTaskId;

    /** sourceType=IMAGE_EDIT 时必填 */
    private String imageEditTaskId;

    /** sourceType=UPLOAD 时必填：用户通过 /api/upload/business 等得到的 COS URL */
    private String sourceImageUrl;

    @NotBlank(message = "视频描述不能为空")
    @Size(max = 1800, message = "描述过长")
    private String prompt;

    /**
     * 生成时长（秒），须为后台配置的允许值之一；不传则用默认。
     */
    private Integer durationSeconds;

    /**
     * 分辨率：720p 或 1080p；不传则用默认。
     */
    private String resolution;

    /**
     * 画幅比例：16:9（横屏）或 9:16（竖屏）；不传则用默认。
     */
    private String aspectRatio;
}
