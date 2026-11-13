package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.db.UserCreditRecord;
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

@RestController
@RequestMapping("/api/admin/credit-records")
@Validated
public class AdminCreditRecordController {

    private final AdminConsoleService adminConsoleService;

    public AdminCreditRecordController(AdminConsoleService adminConsoleService) {
        this.adminConsoleService = adminConsoleService;
    }

    @GetMapping
    public Result<PageResult<UserCreditRecord>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Long userId) {
        return Result.success(adminConsoleService.pageCreditRecords(page, pageSize, userId));
    }
}
