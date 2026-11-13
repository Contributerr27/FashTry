package com.fashtry.backend.controller.business;

import com.fashtry.backend.bean.dto.ProductToModelSubmitRequest;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.ProductToModelStatusVO;
import com.fashtry.backend.bean.vo.ProductToModelSubmitVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.business.ProductToModelService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 商品图转模特图接口
 * <p>
 * 提交后入库并写 Redis，定时任务同步上游状态。需 JWT 认证
 * </p>
 */
@RestController
@RequestMapping("/api/product-to-model")
public class ProductToModelController {

    private final ProductToModelService productToModelService;

    public ProductToModelController(ProductToModelService productToModelService) {
        this.productToModelService = productToModelService;
    }

    /**
     * 提交任务
     * <p>
     * Product Only：仅传 productImage
     * Try-on：传 productImage + modelImage
     * 流程：生成 taskId → 调用上游并入库 → 返回 taskId
     * </p>
     */
    @PostMapping("/submit")
    public Result<ProductToModelSubmitVO> submit(@Valid @RequestBody ProductToModelSubmitRequest request) {
        ProductToModelSubmitVO vo = productToModelService.submit(request);
        return Result.success(vo);
    }

    /**
     * 查询任务状态
     * <p>
     * 先查 Redis，没有再查数据库。仅允许查询本人任务
     * </p>
     */
    @GetMapping("/task")
    public Result<ProductToModelStatusVO> getStatus(@RequestParam Long taskId) {
        ProductToModelStatusVO vo = productToModelService.getStatus(taskId);
        return Result.success(vo);
    }

    /**
     * 查询我的任务列表（分页）
     */
    @GetMapping("/myTasks")
    public Result<PageResult<ProductToModelStatusVO>> listMyTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(productToModelService.listMyTasks(page, size));
    }
}
