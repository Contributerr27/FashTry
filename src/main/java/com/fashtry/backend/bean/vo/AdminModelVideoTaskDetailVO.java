package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fashtry.backend.bean.db.ModelVideoTask;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminModelVideoTaskDetailVO extends ModelVideoTask {

    private String userNickname;

    /** 起始帧 / 用户上传源图 */
    private List<AdminTaskPreviewImageVO> uploadImages;

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

    @Override
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public Long getSourceTryonTaskId() {
        return super.getSourceTryonTaskId();
    }

    @Override
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public Long getSourceImageEditTaskId() {
        return super.getSourceImageEditTaskId();
    }
}
