package com.fashtry.backend.controller.publicapi;

import com.fashtry.backend.bean.dto.GeminiGenerateRequest;
import com.fashtry.backend.bean.dto.GeminiInterleavedRequest;
import com.fashtry.backend.bean.dto.GeminiTryOnRequest;
import com.fashtry.backend.bean.vo.GeminiTaskQueryVO;
import com.fashtry.backend.bean.vo.GeminiTaskSubmitVO;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.bean.vo.TryonSubmitVO;
import com.fashtry.backend.service.ai.GeminiImageService;
import com.fashtry.backend.service.ai.GeminiImageTaskService;
import com.fashtry.backend.service.business.TryonService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * Gemini Developer API 异步任务：提交后返回 taskId，定时任务执行生成并上传 COS，结果入库。
 * 查询：GET /tasks/{taskId}。生产环境请关闭或加鉴权。
 */
@RestController
@RequestMapping("/api/public/gemini-image")
public class GeminiImageController {

    private final GeminiImageService geminiImageService;
    private final GeminiImageTaskService geminiImageTaskService;
    private final TryonService tryonService;

    public GeminiImageController(
            GeminiImageService geminiImageService,
            GeminiImageTaskService geminiImageTaskService,
            TryonService tryonService) {
        this.geminiImageService = geminiImageService;
        this.geminiImageTaskService = geminiImageTaskService;
        this.tryonService = tryonService;
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", geminiImageService.isReady());
        m.put(
                "hint",
                "异步文生图/交织：提交后返回 taskId，再轮询本路径下 GET .../tasks/{taskId}；"
                        + "PC 单图局部改款走 /api/image-edit；模特出图走 /api/clothing-packshot（均需登录）；"
                        + "虚拟试穿走 /api/tryon 与 GET /api/tryon/task。");
        m.put("configHint", "若 enabled 为 false，表示智能生图未在后端开启或未正确配置密钥（详情见服务端日志与配置手册）。");
        return Result.success(m);
    }

    /**
     * 查询异步任务：status=PENDING|PROCESSING|COMPLETED|FAILED；完成时 urls 为 COS 地址。
     */
    @GetMapping("/tasks/{taskId}")
    public Result<GeminiTaskQueryVO> getTask(@PathVariable("taskId") Long taskId) {
        return Result.success(geminiImageTaskService.getTask(taskId));
    }

    @PostMapping("/generate")
    public Result<GeminiTaskSubmitVO> generate(@Valid @RequestBody GeminiGenerateRequest request) {
        return Result.success(geminiImageTaskService.submitGenerate(request));
    }

    @PostMapping(value = "/interleaved", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<GeminiTaskSubmitVO> interleavedJson(@Valid @RequestBody GeminiInterleavedRequest request) {
        return Result.success(geminiImageTaskService.submitInterleavedJson(request));
    }

    @PostMapping(value = "/interleaved", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GeminiTaskSubmitVO> interleavedMultipart(
            @RequestParam("prompt") String prompt,
            @RequestParam(value = "secondPrompt", required = false) String secondPrompt,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "modelId", required = false) String modelId,
            @RequestParam(value = "aspectRatio", required = false) String aspectRatio,
            @RequestParam(value = "imageSize", required = false) String imageSize) {
        return Result.success(
                geminiImageTaskService.submitInterleavedMultipart(
                        prompt, secondPrompt, files, modelId, aspectRatio, imageSize));
    }

    /**
     * 虚拟试穿：JSON 传入模特图、服装图 URL；任务写入 {@code tryon_tasks}（provider=GEMINI），与 FASHN 共用同一套查询接口。
     */
    @PostMapping(value = "/try-on", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<TryonSubmitVO> tryOn(@Valid @RequestBody GeminiTryOnRequest request) {
        return Result.success(tryonService.submitGeminiTryOn(request));
    }
}
