package com.fashtry.backend.bean.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisterGiftSettingsVO {

    /** 每人注册赠送点数 */
    private int perUserCredits;

    /** 每天赠送总额度 */
    private int dailyTotalCredits;

    /** 今日已赠送点数（Asia/Shanghai） */
    private int todayUsedCredits;

    /** 今日剩余可赠送点数 */
    private int todayRemainingCredits;

    /** yyyy-MM-dd */
    private String today;
}
