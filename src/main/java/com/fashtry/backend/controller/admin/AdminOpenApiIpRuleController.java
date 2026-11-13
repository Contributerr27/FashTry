package com.fashtry.backend.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fashtry.backend.bean.db.OpenApiIpRule;
import com.fashtry.backend.bean.dto.OpenApiIpRuleCreateRequest;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.mapper.OpenApiIpRuleMapper;
import com.fashtry.backend.service.openapi.OpenApiIpAllowlistService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/open-api-ip-rules")
public class AdminOpenApiIpRuleController {

    private final OpenApiIpRuleMapper ruleMapper;
    private final OpenApiIpAllowlistService openApiIpAllowlistService;

    public AdminOpenApiIpRuleController(OpenApiIpRuleMapper ruleMapper, OpenApiIpAllowlistService openApiIpAllowlistService) {
        this.ruleMapper = ruleMapper;
        this.openApiIpAllowlistService = openApiIpAllowlistService;
    }

    @GetMapping
    public Result<List<OpenApiIpRule>> list() {
        LambdaQueryWrapper<OpenApiIpRule> w = new LambdaQueryWrapper<>();
        w.orderByDesc(OpenApiIpRule::getId);
        return Result.success(ruleMapper.selectList(w));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody OpenApiIpRuleCreateRequest body) {
        OpenApiIpRule row = new OpenApiIpRule();
        row.setIpRule(body.getIpRule().trim());
        row.setRemark(body.getRemark());
        row.setEnabled(1);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        ruleMapper.insert(row);
        openApiIpAllowlistService.evictCache();
        return Result.success(row.getId());
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        ruleMapper.deleteById(id);
        openApiIpAllowlistService.evictCache();
        return Result.success();
    }
}
