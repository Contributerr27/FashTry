package com.fashtry.backend.controller.openapi;

import com.fashtry.backend.bean.vo.OpenApiSubmitResultVO;
import com.fashtry.backend.bean.vo.OpenApiUnifiedTaskVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.service.openapi.OpenApiAiService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 第三方开放接口：单提交、单查询；由 {@link com.fashtry.backend.security.OpenApiBillingAuthenticationFilter}
 * 校验用户 API 密钥（推荐）或 IP 白名单，无需 JWT。
 */
@RestController
@RequestMapping("/api/open/ai")
public class OpenApiAiController {

    private final OpenApiAiService openApiAiService;

    public OpenApiAiController(OpenApiAiService openApiAiService) {
        this.openApiAiService = openApiAiService;
    }

    /**
     * @param model 业务类型，见 {@link com.fashtry.backend.service.openapi.OpenApiAiModel}
     * @param payload JSON 字符串。试穿类：modelUrl / garmentUrl 等；需传图的模型还可使用 payload.imageUrl、payload.imageUrls（http(s)，与
     *     multipart 二选一，同时传时以 multipart 为准）；模特出图参考脸可为 payload.faceReferenceUrl 或表单 faceReference
     */
    @PostMapping(value = "/submit", consumes = {
            MediaType.MULTIPART_FORM_DATA_VALUE,
            MediaType.APPLICATION_FORM_URLENCODED_VALUE
    })
    public Result<OpenApiSubmitResultVO> submit(
            @RequestParam("model") String model,
            @RequestParam(value = "payload", required = false, defaultValue = "{}") String payload,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "faceReference", required = false) MultipartFile faceReference) {
        return Result.success(openApiAiService.submit(model, payload, file, files, faceReference));
    }

    @GetMapping("/task")
    public Result<OpenApiUnifiedTaskVO> task(
            @RequestParam("model") String model, @RequestParam("taskId") Long taskId) {
        return Result.success(openApiAiService.query(model, taskId));
    }
}
