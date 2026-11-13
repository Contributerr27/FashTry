package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** 后台任务详情：三选一非空 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminTaskDetailWrapper {

    private String recordSource;
    private AdminTryonTaskVO tryon;
    private AdminGeminiImageTaskDetailVO geminiImage;
    private AdminModelVideoTaskDetailVO modelVideo;
}
