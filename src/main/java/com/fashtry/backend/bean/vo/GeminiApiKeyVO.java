package com.fashtry.backend.bean.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeminiApiKeyVO {
    private Long id;
    private String name;
    private String keyMask;
    private boolean enabled;
    private int sortOrder;
    private String cooldownUntil;
    private String lastError;
    private String lastErrorAt;
    private String lastOkAt;
    private int failCount;
    private int successCount;
}
