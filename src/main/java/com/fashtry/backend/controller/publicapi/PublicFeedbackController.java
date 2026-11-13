package com.fashtry.backend.controller.publicapi;

import com.fashtry.backend.bean.dto.FeedbackSubmitRequest;
import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.security.LoginUser;
import com.fashtry.backend.security.SecurityUtils;
import com.fashtry.backend.service.business.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicFeedbackController {

    private final FeedbackService feedbackService;

    public PublicFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/feedback")
    public Result<Map<String, Long>> submit(@Valid @RequestBody FeedbackSubmitRequest body, HttpServletRequest request) {
        LoginUser lu = SecurityUtils.getCurrentLoginUser();
        LoginUser submitter = lu != null && !"ADMIN".equals(lu.getRole()) ? lu : null;
        String ip = resolveClientIp(request);
        String ua = request.getHeader("User-Agent");
        long id = feedbackService.submit(body, submitter, ip, ua);
        return Result.success(Map.of("id", id));
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            String first = comma > 0 ? xff.substring(0, comma) : xff;
            return first.trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(real)) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }
}
