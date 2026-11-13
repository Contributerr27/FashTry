package com.fashtry.backend.bean.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 局部改款历史列表项：供 GET /api/image-edit/history，含缩略图地址与成图 URL 列表（可放大/下载）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiImageEditHistoryItemVO {

    private Long taskId;
    /** PENDING / PROCESSING / COMPLETED / FAILED */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    /** 截断后的修改说明，用于卡片展示 */
    private String instructionPreview;
    /** 提交时的原图 COS 地址 */
    private String sourceImageUrl;
    /** 生成结果图 URL 列表（未完成为空） */
    private List<String> outputUrls;
}
