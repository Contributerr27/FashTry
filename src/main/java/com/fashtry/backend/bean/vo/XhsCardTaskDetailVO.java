package com.fashtry.backend.bean.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史详情：含完整文案，供前端回放。
 */
@Data
public class XhsCardTaskDetailVO {

    private Long taskId;

    private String status;

    private String topic;

    private String sellingPoints;

    private String audience;

    private String brandName;

    private String templateStyle;

    private String backgroundColor;

    private String textColor;

    private String referenceImageUrl;

    private int consumeCredits;

    private XhsCardContentVO content;

    private String errorMsg;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
