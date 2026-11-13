package com.fashtry.backend.bean.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeminiKeyProbeVO {
    private boolean ok;
    private int httpStatus;
    private String message;
}
