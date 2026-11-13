package com.fashtry.backend.bean.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiPackshotHistoryItemVO {

    private Long taskId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String sourceImageUrl;
    /** AI 写真等多张参考缩略；可为 null，此时仅用 {@link #sourceImageUrl} */
    private List<String> sourceImageUrls;
    private String artDirection;
    /** AI 写真妆容键，如 {@code ORIGINAL}；其它业务为 null */
    private String makeupPreset;
    /** AI 写真服装模式：{@code STYLE}、{@code KEEP_REFERENCE}；其它业务为 null */
    private String outfitMode;
    /** 服装提取：{@code FULL} / {@code UPPER} / {@code LOWER}；其它业务为 null */
    private String garmentScope;
    private List<String> outputUrls;
    private List<String> imageLabels;
    /** 单步重试任务时非 null，指向整套套图任务 ID */
    private Long parentTaskId;
    /** 单步重试时重生成步骤 0～9；整套任务为 null */
    private Integer regenerateSlotIndex;
}
