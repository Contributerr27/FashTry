package com.fashtry.backend.bean.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class XhsCardHistoryItemVO {

    private Long taskId;

    /** SUCCESS / FAILED */
    private String status;

    private String topic;

    private String coverTitle;

    private String templateStyle;

    private String backgroundColor;

    private String textColor;

    private String brandName;

    private String referenceImageUrl;

    private int consumeCredits;

    private LocalDateTime createdAt;
}
