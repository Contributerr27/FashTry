package com.fashtry.backend.bean.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理台数据概览（用户量、任务量、订单等聚合）
 */
@Data
@Builder
public class AdminStatsOverviewVO {

    /** 数据生成时间 */
    private String generatedAt;

    /** 日切与时区说明 */
    private String dayCutTimezone;

    /** 说明：无独立 PV 日志时「访问量」以业务提交量为参考 */
    private String trafficNote;

    private long totalUsers;
    private long newUsersToday;
    private long newUsersLast7Days;
    private long newUsersLast30Days;

    private long totalTryonTasks;
    private long tryonStatusQueued;
    private long tryonStatusProcessing;
    private long tryonStatusDone;
    private long tryonStatusFailed;
    private long tryonTasksCreatedLast7Days;
    /** 已完成试穿任务消耗点数合计 */
    private long tryonCreditsConsumedDone;

    private long totalLocalEditTasks;
    private long localEditTasksCreatedLast7Days;
    private long localEditCreditsConsumedSuccess;

    private long totalClothingImageTasks;
    private long clothingImageTasksCreatedLast7Days;
    private long clothingImageCreditsConsumedSuccess;

    private long totalModelImageTasks;
    private long modelImageTasksCreatedLast7Days;
    private long modelImageCreditsConsumedSuccess;

    private long totalUserUploads;
    private long userUploadsLast7Days;

    private long totalBrands;

    private long paidOrdersTotal;
    private BigDecimal paidOrdersAmountTotal;
    private long paidOrdersLast7Days;

    /** 全站用户钱包：累计充值点数、累计充值金额（元）合计 */
    private long walletTotalRechargedCredits;
    private BigDecimal walletTotalRechargedAmount;

    /** 近 14 日按日明细 */
    private List<AdminStatsDailyPointVO> dailyLast14Days;
}
