package com.fashtry.backend.controller.admin;

import com.fashtry.backend.bean.dto.GeminiApiKeyUpsertRequest;
import com.fashtry.backend.bean.dto.GeminiKeyStrategyRequest;
import com.fashtry.backend.bean.vo.GeminiKeyProbeVO;
import com.fashtry.backend.bean.vo.GeminiKeySettingsVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.admin.GeminiKeyAdminService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/gemini-keys")
@Validated
public class AdminGeminiKeyController {

    private final GeminiKeyAdminService geminiKeyAdminService;

    public AdminGeminiKeyController(GeminiKeyAdminService geminiKeyAdminService) {
        this.geminiKeyAdminService = geminiKeyAdminService;
    }

    @GetMapping
    public Result<GeminiKeySettingsVO> settings() {
        return Result.success(geminiKeyAdminService.settings());
    }

    @PutMapping("/strategy")
    public Result<Void> strategy(@Valid @RequestBody GeminiKeyStrategyRequest body) {
        geminiKeyAdminService.updateStrategy(body.getStrategy());
        return Result.success();
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody GeminiApiKeyUpsertRequest body) {
        geminiKeyAdminService.create(body);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable long id, @Valid @RequestBody GeminiApiKeyUpsertRequest body) {
        geminiKeyAdminService.update(id, body);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        geminiKeyAdminService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/probe")
    public Result<GeminiKeyProbeVO> probe(@PathVariable long id) {
        return Result.success(geminiKeyAdminService.probe(id));
    }

    @PostMapping("/{id}/clear-cooldown")
    public Result<Void> clearCooldown(@PathVariable long id) {
        geminiKeyAdminService.clearCooldown(id);
        return Result.success();
    }
}
