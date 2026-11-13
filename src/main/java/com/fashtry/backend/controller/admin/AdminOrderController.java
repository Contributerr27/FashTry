package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.db.Order;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.admin.AdminConsoleService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 充值订单列表（含待支付/已支付等）
 */
@RestController
@RequestMapping("/api/admin/orders")
@Validated
public class AdminOrderController {

    private final AdminConsoleService adminConsoleService;

    public AdminOrderController(AdminConsoleService adminConsoleService) {
        this.adminConsoleService = adminConsoleService;
    }

    @GetMapping
    public Result<PageResult<Order>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Long userId) {
        return Result.success(adminConsoleService.pageOrders(page, pageSize, userId));
    }
}
