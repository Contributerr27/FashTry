package com.fashtry.backend.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 小红书图文卡片：文案生成请求（用户不写 Prompt，只填主题要素）。
 */
@Data
public class XhsCardGenerateRequest {

    @NotBlank(message = "主题不能为空")
    @Size(max = 120, message = "主题过长")
    private String topic;

    /** 卖点/干货要点，可选 */
    @Size(max = 400, message = "卖点过长")
    private String sellingPoints;

    /** 目标受众，可选 */
    @Size(max = 120, message = "受众描述过长")
    private String audience;

    /** 底部 Logo / 账号名，可选 */
    @Size(max = 40, message = "品牌名过长")
    private String brandName;

    /**
     * 模板样式：clipnote / textnote / handbook / customref 等；
     * 传 {@code random} 时由服务端随机选一套。
     */
    @Size(max = 32)
    private String templateStyle;

    /** 自定义背景色，如 #FFD60A；空则用模板默认 */
    @Size(max = 16)
    private String backgroundColor;

    /** 自定义文字色；空则按背景自动算或用模板默认 */
    @Size(max = 16)
    private String textColor;

    /** 用户上传的参考模板图 URL（COS），可选 */
    @Size(max = 512)
    private String referenceImageUrl;
}
