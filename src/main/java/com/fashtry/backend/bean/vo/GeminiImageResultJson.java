package com.fashtry.backend.bean.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 入库的 {@code result_json} 结构 */
@Data
public class GeminiImageResultJson {

    private List<String> urls = new ArrayList<>();
    private List<String> textParts = new ArrayList<>();
    /** 与 {@link #urls} 一一对应，如 MODEL_WEAR、SCENE_1、ECOM_WHITE（仅部分任务写入） */
    private List<String> imageLabels = new ArrayList<>();
}
