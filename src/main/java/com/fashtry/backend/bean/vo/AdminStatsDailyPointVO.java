package com.fashtry.backend.bean.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 按自然日聚合的统计点（用于管理台趋势表）
 */
@Data
@Builder
public class AdminStatsDailyPointVO {

    /** yyyy-MM-dd，按 Asia/Shanghai 切日 */
    private String date;

    private long newUsers;
    private long tryonTasksCreated;
    /** 局部改款（gemini_image_tasks.task_type = EDIT）当日新建 */
    private long localEditTasksCreated;
    /** 服装出图（CLOTHING_PRODUCT_SUITE）当日新建 */
    private long clothingImageTasksCreated;
    /** 模特出图（CLOTHING_PACKSHOT）当日新建 */
    private long modelImageTasksCreated;
    private long userUploads;
    /** 当日完成支付的订单笔数 */
    private long paidOrders;
    /** 当日完成支付的订单金额合计（多币种直接相加，仅供参考） */
    private BigDecimal paidAmount;
}
