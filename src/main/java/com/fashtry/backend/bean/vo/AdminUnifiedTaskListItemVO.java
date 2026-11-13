package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 后台「任务列表」合并行：试穿 / Gemini 异步图 / 模特视频 */
@Data
public class AdminUnifiedTaskListItemVO {

    /** tryon | gemini_image | model_video */
    private String recordSource;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 列表加载后回填 */
    private String userNickname;

    private Integer status;

    /** tryon：provider；gemini：task_type；model_video：固定 model_video */
    private String taskLabel;

    /** tryon：服装 category；gemini：可为空；model_video：分辨率/画幅等 */
    private String detailTag;

    private Integer consumeCredits;

    private LocalDateTime createdAt;
}
