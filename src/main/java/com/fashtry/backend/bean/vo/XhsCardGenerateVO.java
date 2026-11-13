package com.fashtry.backend.bean.vo;

import lombok.Data;

@Data
public class XhsCardGenerateVO {

    private Long taskId;

    private XhsCardContentVO content;

    /** 本次实际扣除点数 */
    private int consumeCredits;

    /** 可选回显，方便前端默认填进卡片底部 */
    private String brandName;

    private String templateStyle;

    private String backgroundColor;

    private String textColor;

    /** 参考模板图 URL */
    private String referenceImageUrl;
}
