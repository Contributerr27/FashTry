package com.fashtry.backend.service.business;

import com.fashtry.backend.bean.vo.MobileUnifiedHistoryItemVO;
import com.fashtry.backend.bean.vo.PageResult;

/**
 * 移动端「我的创作」统一历史：一次聚合试穿 / 局部改款 / 模特出图 / 套图，减少多端重复请求。
 */
public interface MobileTaskHistoryService {

    /**
     * @param userId   当前登录用户
     * @param kind     all | tryon | image_edit | packshot | suite
     * @param page     页码，从 1 开始；{@code kind=all} 时忽略（固定返回合并后的第一页）
     * @param pageSize 每页条数，最大 50；{@code kind=all} 时每类最多取 50 条再合并
     */
    PageResult<MobileUnifiedHistoryItemVO> listUnified(Long userId, String kind, int page, int pageSize);
}
