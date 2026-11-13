package com.fashtry.backend.service.business;

import com.fashtry.backend.bean.db.ProductToModelTask;
import com.fashtry.backend.bean.dto.ProductToModelSubmitRequest;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.dto.ProductToModelTaskMessage;
import com.fashtry.backend.bean.vo.ProductToModelStatusVO;
import com.fashtry.backend.bean.vo.ProductToModelSubmitVO;

/**
 * 商品图转模特图服务
 * <p>
 * 提交后在当前请求内调用上游 API、入库并写 Redis，再由定时任务同步状态。
 * </p>
 */
public interface ProductToModelService {

    /**
     * 处理任务：调用 FASHN API、入库、更新 Redis（由 {@link #submit} 内部调用）
     */
    void processTaskMessage(ProductToModelTaskMessage message);

    /**
     * 提交任务：生成 taskId，校验点数后直接处理并入库
     */
    ProductToModelSubmitVO submit(ProductToModelSubmitRequest request);

    /**
     * 查询任务状态：先查 Redis，没有再查数据库
     */
    ProductToModelStatusVO getStatus(Long taskId);

    /**
     * 分页查询我的任务列表
     */
    PageResult<ProductToModelStatusVO> listMyTasks(int page, int size);

    /**
     * 根据数据库任务更新 Redis 缓存（供定时任务调用）
     */
    void updateRedis(Long taskId, ProductToModelTask task);
}
