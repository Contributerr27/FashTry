package com.fashtry.backend.bean.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 后台任务详情预览图（用户上传 / 生成结果） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminTaskPreviewImageVO {

    private String url;
    private String label;
}
