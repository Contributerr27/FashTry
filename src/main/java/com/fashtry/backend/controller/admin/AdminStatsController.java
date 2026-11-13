package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.vo.AdminStatsOverviewVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.admin.AdminStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    public AdminStatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    @GetMapping("/overview")
    public Result<AdminStatsOverviewVO> overview() {
        return Result.success(adminStatsService.overview());
    }
}
