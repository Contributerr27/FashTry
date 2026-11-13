package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.db.RechargePackage;
import com.fashtry.backend.bean.dto.AdminRechargePackageUpsertRequest;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.admin.AdminConsoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/recharge-packages")
@Validated
public class AdminRechargePackageController {

    private final AdminConsoleService adminConsoleService;

    public AdminRechargePackageController(AdminConsoleService adminConsoleService) {
        this.adminConsoleService = adminConsoleService;
    }

    @GetMapping
    public Result<PageResult<RechargePackage>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int pageSize) {
        return Result.success(adminConsoleService.pageRechargePackages(page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<RechargePackage> get(@PathVariable Integer id) {
        return Result.success(adminConsoleService.getRechargePackage(id));
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody AdminRechargePackageUpsertRequest body) {
        adminConsoleService.createRechargePackage(body);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Integer id, @Valid @RequestBody AdminRechargePackageUpsertRequest body) {
        adminConsoleService.updateRechargePackage(id, body);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        adminConsoleService.deleteRechargePackage(id);
        return Result.success();
    }
}
