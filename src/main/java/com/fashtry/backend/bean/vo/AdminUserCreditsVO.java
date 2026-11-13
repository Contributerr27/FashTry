package com.fashtry.backend.bean.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminUserCreditsVO {

    /** 字符串形式，与列表一致 */
    private String userId;
    private Integer previousTotalCredits;
    private Integer totalCredits;
    private Integer delta;
    private Integer frozenCredits;
}
