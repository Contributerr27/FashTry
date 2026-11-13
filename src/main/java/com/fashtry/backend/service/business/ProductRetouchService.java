package com.fashtry.backend.service.business;

import com.fashtry.backend.service.ai.GeminiImageService;
import org.springframework.stereotype.Service;

/**
 * 服装提取：与 Gemini 是否就绪等轻量状态（实际提交与出图在 {@code PRODUCT_RETOUCH} 异步任务中）。
 */
@Service
public class ProductRetouchService {

    private final GeminiImageService geminiImageService;

    public ProductRetouchService(GeminiImageService geminiImageService) {
        this.geminiImageService = geminiImageService;
    }

    public boolean isEnabled() {
        return geminiImageService.isReady();
    }
}
