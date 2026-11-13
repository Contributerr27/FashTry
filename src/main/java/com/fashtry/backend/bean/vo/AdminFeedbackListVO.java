package com.fashtry.backend.bean.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminFeedbackListVO {

    private Long id;
    private Long userId;
    private String userNickname;
    private String category;
    private String content;
    private String contact;
    private String pagePath;
    private String clientIp;
    private String userAgent;
    private Integer status;
    private LocalDateTime createdAt;
}
