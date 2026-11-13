package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fashtry.backend.bean.db.TryonTask;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminTryonTaskVO extends TryonTask {

    /** 用户昵称，便于后台展示 */
    private String userNickname;

    /** 用户上传图（模特 / 服装等） */
    private List<AdminTaskPreviewImageVO> uploadImages;

    /** 试穿效果 */
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
