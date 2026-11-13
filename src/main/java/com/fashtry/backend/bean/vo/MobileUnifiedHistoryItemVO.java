package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 移动端统一历史列表项：{@code kind} 标识任务类型，仅对应一种 payload 非空。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MobileUnifiedHistoryItemVO {

    /** tryon | image_edit | packshot | suite | retouch | portrait */
    private String kind;

    /** 用于合并排序的时间戳（毫秒） */
    private long sortAtMs;

    private TryonStatusVO tryon;
    private GeminiImageEditHistoryItemVO imageEdit;
    private GeminiPackshotHistoryItemVO packshot;
    private GeminiPackshotHistoryItemVO suite;
    private GeminiPackshotHistoryItemVO retouch;
    private GeminiPackshotHistoryItemVO portrait;
}
