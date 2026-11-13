package com.fashtry.backend.service.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashtry.backend.bean.db.XhsCardTask;
import com.fashtry.backend.bean.dto.XhsCardGenerateRequest;
import com.fashtry.backend.bean.vo.GeminiImageResultVO;
import com.fashtry.backend.bean.vo.PageResult;
import com.fashtry.backend.bean.vo.XhsCardContentVO;
import com.fashtry.backend.bean.vo.XhsCardGenerateVO;
import com.fashtry.backend.bean.vo.XhsCardHistoryItemVO;
import com.fashtry.backend.bean.vo.XhsCardPageVO;
import com.fashtry.backend.bean.vo.XhsCardTaskDetailVO;
import com.fashtry.backend.common.config.GeminiApiProperties;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.fashtry.backend.mapper.XhsCardTaskMapper;
import com.fashtry.backend.service.ai.GeminiImageService;
import com.fashtry.backend.service.ai.XhsCardPrompts;
import com.fashtry.backend.utils.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class XhsCardService {

    private static final Logger log = LoggerFactory.getLogger(XhsCardService.class);
    private static final Pattern JSON_FENCE =
            Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?})\\s*```");
    private static final Pattern HEX_COLOR = Pattern.compile("^#([0-9A-Fa-f]{6})$");
    private static final List<String> STYLES =
            List.of(
                    "clipnote",
                    "textnote",
                    "handbook",
                    "boardmint",
                    "boardpink",
                    "boardsky",
                    "papercraft",
                    "dark",
                    "customref");
    private static final Set<String> STYLE_SET = Set.copyOf(STYLES);

    private final GeminiImageService geminiImageService;
    private final GeminiApiProperties geminiApiProperties;
    private final WalletDeductionService walletDeductionService;
    private final XhsCardTaskMapper xhsCardTaskMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final org.springframework.web.client.RestTemplate restTemplate;

    public XhsCardService(
            GeminiImageService geminiImageService,
            GeminiApiProperties geminiApiProperties,
            WalletDeductionService walletDeductionService,
            XhsCardTaskMapper xhsCardTaskMapper,
            SnowflakeIdGenerator idGenerator,
            ObjectMapper objectMapper,
            org.springframework.web.client.RestTemplate restTemplate) {
        this.geminiImageService = geminiImageService;
        this.geminiApiProperties = geminiApiProperties;
        this.walletDeductionService = walletDeductionService;
        this.xhsCardTaskMapper = xhsCardTaskMapper;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public int consumeCredits() {
        return Math.max(0, geminiApiProperties.getXhsCardConsumeCredits());
    }

    public List<String> availableStyles() {
        return STYLES;
    }

    public XhsCardGenerateVO generate(Long userId, XhsCardGenerateRequest request) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请先登录");
        }
        if (!geminiImageService.isReady()) {
            throw new BusinessException(
                    ResultCode.PARAM_INVALID, "文案服务未启用：请配置 app.gemini.enabled 与 api-key");
        }

        String refUrl = normalizeUrl(request.getReferenceImageUrl());
        String style = resolveStyle(request.getTemplateStyle(), refUrl != null);
        String bg = normalizeHex(request.getBackgroundColor());
        String fg = normalizeHex(request.getTextColor());
        if (bg != null && fg == null) {
            fg = autoTextColor(bg);
        }

        int cost = consumeCredits();
        long taskId = idGenerator.nextId();
        LocalDateTime now = LocalDateTime.now();

        XhsCardTask task = new XhsCardTask();
        task.setId(taskId);
        task.setUserId(userId);
        task.setTopic(trimTo(request.getTopic(), 120));
        task.setSellingPoints(emptyToNull(trimTo(request.getSellingPoints(), 400)));
        task.setAudience(emptyToNull(trimTo(request.getAudience(), 120)));
        task.setBrandName(emptyToNull(trimTo(request.getBrandName(), 40)));
        task.setTemplateStyle(style);
        task.setBackgroundColor(bg);
        task.setTextColor(fg);
        task.setReferenceImageUrl(refUrl);
        task.setStatus(1);
        task.setConsumeCredits(cost);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        xhsCardTaskMapper.insert(task);

        if (cost > 0) {
            boolean frozen = walletDeductionService.freezeCredits(userId, cost, "xhs_card", taskId);
            if (!frozen) {
                markFailed(task, "点数不足，请先充值");
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }

        String styleHint = refUrl != null ? analyzeReferenceStyle(refUrl) : null;
        String prompt =
                XhsCardPrompts.buildUserPrompt(
                        request.getTopic(),
                        request.getSellingPoints(),
                        request.getAudience(),
                        request.getBrandName(),
                        style,
                        refUrl != null,
                        styleHint);

        XhsCardContentVO content;
        try {
            content = callAndParse(prompt);
            if (!isValid(content)) {
                log.warn("xhs-card first parse invalid, retry once userId={} taskId={}", userId, taskId);
                content =
                        callAndParse(
                                prompt
                                        + "\n\n上次输出不合格：必须含完整 noteCaption（高Emoji、短行、文末#话题标签），pages 恰好 3 项，请严格按 JSON 重写。");
            }
            if (!isValid(content)) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "文案生成失败，请重试");
            }
            normalize(content);
        } catch (RuntimeException e) {
            if (cost > 0) {
                walletDeductionService.releaseFrozenCredits(userId, cost, "xhs_card", taskId);
            }
            String msg = e instanceof BusinessException be ? be.getMessage() : e.getMessage();
            markFailed(task, msg != null ? msg : "生成失败");
            throw e;
        }

        if (cost > 0) {
            boolean settled = walletDeductionService.settleFrozenCredits(userId, cost, "xhs_card", taskId);
            if (!settled) {
                walletDeductionService.releaseFrozenCredits(userId, cost, "xhs_card", taskId);
                markFailed(task, "点数不足，请先充值");
                throw new BusinessException(ResultCode.PARAM_INVALID, "点数不足，请先充值");
            }
        }

        try {
            task.setResultJson(objectMapper.writeValueAsString(content));
        } catch (Exception e) {
            log.warn("xhs-card serialize result failed taskId={}", taskId, e);
            task.setResultJson(null);
        }
        task.setStatus(2);
        task.setErrorMsg(null);
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        xhsCardTaskMapper.updateById(task);

        XhsCardGenerateVO vo = new XhsCardGenerateVO();
        vo.setTaskId(taskId);
        vo.setContent(content);
        vo.setConsumeCredits(cost);
        vo.setBrandName(task.getBrandName());
        vo.setTemplateStyle(style);
        vo.setBackgroundColor(bg);
        vo.setTextColor(fg);
        vo.setReferenceImageUrl(refUrl);
        return vo;
    }

    public PageResult<XhsCardHistoryItemVO> listHistory(Long userId, int page, int size) {
        if (userId == null || userId <= 0) {
            return PageResult.of(List.of(), 0, 1, size);
        }
        int p = Math.max(1, page);
        int s = Math.min(50, Math.max(1, size));
        Page<XhsCardTask> pg = new Page<>(p, s);
        LambdaQueryWrapper<XhsCardTask> w =
                new LambdaQueryWrapper<XhsCardTask>()
                        .eq(XhsCardTask::getUserId, userId)
                        .orderByDesc(XhsCardTask::getCreatedAt);
        var out = xhsCardTaskMapper.selectPage(pg, w);
        List<XhsCardHistoryItemVO> rows = new ArrayList<>();
        for (XhsCardTask t : out.getRecords()) {
            rows.add(toHistoryItem(t));
        }
        return PageResult.of(rows, out.getTotal(), p, s);
    }

    public XhsCardTaskDetailVO getDetail(Long userId, Long taskId) {
        if (userId == null || userId <= 0 || taskId == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "参数无效");
        }
        XhsCardTask t = xhsCardTaskMapper.selectById(taskId);
        if (t == null || !userId.equals(t.getUserId())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务不存在");
        }
        XhsCardTaskDetailVO vo = new XhsCardTaskDetailVO();
        vo.setTaskId(t.getId());
        vo.setStatus(statusLabel(t.getStatus()));
        vo.setTopic(t.getTopic());
        vo.setSellingPoints(t.getSellingPoints());
        vo.setAudience(t.getAudience());
        vo.setBrandName(t.getBrandName());
        vo.setTemplateStyle(t.getTemplateStyle());
        vo.setBackgroundColor(t.getBackgroundColor());
        vo.setTextColor(t.getTextColor());
        vo.setReferenceImageUrl(t.getReferenceImageUrl());
        vo.setConsumeCredits(t.getConsumeCredits() != null ? t.getConsumeCredits() : 0);
        vo.setErrorMsg(t.getErrorMsg());
        vo.setCreatedAt(t.getCreatedAt());
        vo.setCompletedAt(t.getCompletedAt());
        if (StringUtils.hasText(t.getResultJson())) {
            try {
                vo.setContent(objectMapper.readValue(t.getResultJson(), XhsCardContentVO.class));
            } catch (Exception e) {
                log.warn("xhs-card parse result_json failed taskId={}", taskId);
            }
        }
        return vo;
    }

    private XhsCardHistoryItemVO toHistoryItem(XhsCardTask t) {
        XhsCardHistoryItemVO vo = new XhsCardHistoryItemVO();
        vo.setTaskId(t.getId());
        vo.setStatus(statusLabel(t.getStatus()));
        vo.setTopic(t.getTopic());
        vo.setTemplateStyle(t.getTemplateStyle());
        vo.setBackgroundColor(t.getBackgroundColor());
        vo.setTextColor(t.getTextColor());
        vo.setBrandName(t.getBrandName());
        vo.setReferenceImageUrl(t.getReferenceImageUrl());
        vo.setConsumeCredits(t.getConsumeCredits() != null ? t.getConsumeCredits() : 0);
        vo.setCreatedAt(t.getCreatedAt());
        if (StringUtils.hasText(t.getResultJson())) {
            try {
                XhsCardContentVO c = objectMapper.readValue(t.getResultJson(), XhsCardContentVO.class);
                vo.setCoverTitle(c != null ? c.getCoverTitle() : null);
            } catch (Exception ignored) {
                /* ignore */
            }
        }
        return vo;
    }

    private void markFailed(XhsCardTask task, String msg) {
        task.setStatus(3);
        task.setErrorMsg(trimTo(msg, 500));
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        xhsCardTaskMapper.updateById(task);
    }

    private static String statusLabel(Integer status) {
        if (status == null) {
            return "UNKNOWN";
        }
        return switch (status) {
            case 0 -> "PENDING";
            case 1 -> "PROCESSING";
            case 2 -> "SUCCESS";
            case 3 -> "FAILED";
            default -> "UNKNOWN";
        };
    }

    private static String resolveStyle(String raw, boolean hasReference) {
        if (!StringUtils.hasText(raw)) {
            return hasReference ? "handbook" : "clipnote";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if ("random".equals(s)) {
            // 随机时不抽 customref（需参考图）
            List<String> pool =
                    STYLES.stream().filter(x -> !"customref".equals(x)).toList();
            return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        }
        if ("customref".equals(s)) {
            return hasReference ? "customref" : "handbook";
        }
        // 兼容旧模板名
        if ("minimal".equals(s) || "yellow".equals(s)) {
            return "clipnote";
        }
        if ("blush".equals(s) || "coral".equals(s)) {
            return "boardpink";
        }
        if ("mint".equals(s)) {
            return "boardmint";
        }
        if ("ink".equals(s)) {
            return "boardsky";
        }
        return STYLE_SET.contains(s) ? s : (hasReference ? "handbook" : "clipnote");
    }

    private String analyzeReferenceStyle(String imageUrl) {
        try {
            byte[] bytes = restTemplate.getForObject(imageUrl, byte[].class);
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            String mime = guessMime(imageUrl);
            GeminiImageResultVO raw =
                    geminiImageService.generateTextFromImage(
                            bytes, mime, XhsCardPrompts.referenceAnalyzeInstruction());
            if (raw == null || raw.getTextParts() == null || raw.getTextParts().isEmpty()) {
                return null;
            }
            String hint = String.join("", raw.getTextParts()).trim();
            return hint.length() > 280 ? hint.substring(0, 280) : hint;
        } catch (Exception e) {
            log.warn("xhs-card analyze reference failed: {}", e.getMessage());
            return null;
        }
    }

    private static String guessMime(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        if (u.contains(".png")) {
            return "image/png";
        }
        if (u.contains(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private static String normalizeUrl(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim();
        if (!(t.startsWith("https://") || t.startsWith("http://"))) {
            return null;
        }
        return t.length() > 512 ? t.substring(0, 512) : t;
    }

    private static String normalizeHex(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim();
        if (t.length() == 6 && !t.startsWith("#")) {
            t = "#" + t;
        }
        if (!HEX_COLOR.matcher(t).matches()) {
            return null;
        }
        return t.toUpperCase(Locale.ROOT);
    }

    /** 按相对亮度选黑/白字 */
    private static String autoTextColor(String bgHex) {
        try {
            int r = Integer.parseInt(bgHex.substring(1, 3), 16);
            int g = Integer.parseInt(bgHex.substring(3, 5), 16);
            int b = Integer.parseInt(bgHex.substring(5, 7), 16);
            double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
            return lum > 0.55 ? "#161616" : "#F5F5F5";
        } catch (Exception e) {
            return "#161616";
        }
    }

    private XhsCardContentVO callAndParse(String prompt) {
        GeminiImageResultVO raw = geminiImageService.generateText(prompt, true);
        String text = joinText(raw);
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "模型未返回文案");
        }
        try {
            return objectMapper.readValue(extractJson(text), XhsCardContentVO.class);
        } catch (Exception e) {
            log.warn("xhs-card JSON parse failed: {}", e.getMessage());
            return null;
        }
    }

    private static String joinText(GeminiImageResultVO raw) {
        if (raw == null || raw.getTextParts() == null || raw.getTextParts().isEmpty()) {
            return "";
        }
        return String.join("\n", raw.getTextParts()).trim();
    }

    private static String extractJson(String text) {
        String t = text.trim();
        Matcher m = JSON_FENCE.matcher(t);
        if (m.find()) {
            return m.group(1).trim();
        }
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return t.substring(start, end + 1);
        }
        return t;
    }

    private static boolean isValid(XhsCardContentVO c) {
        if (c == null || !StringUtils.hasText(c.getCoverTitle())) {
            return false;
        }
        List<XhsCardPageVO> pages = c.getPages();
        if (pages == null || pages.size() < 2) {
            return false;
        }
        int ok = 0;
        for (XhsCardPageVO p : pages) {
            if (p != null && (StringUtils.hasText(p.getHeading()) || StringUtils.hasText(p.getBody()))) {
                ok++;
            }
        }
        return ok >= 2;
    }

    private static void normalize(XhsCardContentVO c) {
        if (c.getCoverSubtitle() == null) {
            c.setCoverSubtitle("");
        }
        if (c.getCta() == null) {
            c.setCta("记得点赞收藏～✨");
        }
        List<XhsCardPageVO> pages = c.getPages();
        if (pages == null) {
            pages = new ArrayList<>();
            c.setPages(pages);
        }
        while (pages.size() > 3) {
            pages.remove(pages.size() - 1);
        }
        while (pages.size() < 3) {
            XhsCardPageVO pad = new XhsCardPageVO();
            pad.setHeading("补充tips");
            pad.setBody("1️⃣ 先收藏再慢慢看\n2️⃣ 有用记得点赞呀❤️");
            pages.add(pad);
        }
        for (XhsCardPageVO p : pages) {
            if (p.getHeading() == null) {
                p.setHeading("");
            }
            if (p.getBody() == null) {
                p.setBody("");
            }
        }
        c.setNoteCaption(ensureNoteCaption(c));
    }

    /** 保证正文可复制：高 Emoji 短段落 + 话题标签；模型漏写时用卡片内容兜底拼接。 */
    private static String ensureNoteCaption(XhsCardContentVO c) {
        String raw = c.getNoteCaption() != null ? c.getNoteCaption().trim() : "";
        if (StringUtils.hasText(raw) && raw.length() >= 40) {
            return ensureHashtags(raw);
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(c.getCoverTitle())) {
            sb.append(c.getCoverTitle().trim()).append(" ✨\n\n");
        }
        if (StringUtils.hasText(c.getCoverSubtitle())) {
            sb.append(c.getCoverSubtitle().trim()).append(" 🔥\n\n");
        }
        if (c.getPages() != null) {
            int i = 1;
            for (XhsCardPageVO p : c.getPages()) {
                if (p == null) {
                    continue;
                }
                if (StringUtils.hasText(p.getHeading())) {
                    sb.append(i).append("️⃣ ").append(p.getHeading().trim()).append('\n');
                }
                if (StringUtils.hasText(p.getBody())) {
                    String body = p.getBody().trim().replace("\r\n", "\n");
                    for (String line : body.split("\n")) {
                        String L = line.trim();
                        if (StringUtils.hasText(L)) {
                            sb.append(L).append('\n');
                        }
                    }
                }
                sb.append('\n');
                i++;
            }
        }
        if (StringUtils.hasText(c.getCta())) {
            sb.append(c.getCta().trim()).append('\n');
        }
        return ensureHashtags(sb.toString().trim());
    }

    private static String ensureHashtags(String text) {
        String t = text == null ? "" : text.trim();
        if (t.contains("#")) {
            return t;
        }
        if (!t.isEmpty() && !t.endsWith("\n")) {
            t = t + "\n";
        }
        return t + "\n#小红书干货 #AI工具 #效率提升 #建议收藏";
    }

    private static String trimTo(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return s == null ? null : "";
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }
}
