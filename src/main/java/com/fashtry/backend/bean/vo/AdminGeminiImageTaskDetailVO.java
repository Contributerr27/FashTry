package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fashtry.backend.bean.db.GeminiImageTask;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminGeminiImageTaskDetailVO extends GeminiImageTask {

    private String userNickname;

    /** 用户上传 / 输入图（已解析 payload，含父任务合并） */
    private List<AdminTaskPreviewImageVO> uploadImages;

    /** 生成结果图（已解析 result_json） */
    private List<AdminTaskPreviewImageVO> resultImages;

    @Override
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public Long getId() {
        return super.getId();
    }

    @Override
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public Long getUserId() {
        return super.getUserId();
    }
}
