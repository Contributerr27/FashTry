package com.fashtry.backend.bean.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GeminiKeySettingsVO {
    /** ROUND_ROBIN | FAILOVER | RANDOM */
    private String strategy;
    /** 数据库没有启用 Key 时，是否还会用配置文件里的 Key */
    private boolean yamlFallback;
    /** Gemini Developer API 不提供余额查询 */
    private String balanceNote;
    private List<GeminiApiKeyVO> keys;
}
