package com.fashtry.backend.service.storage;

import com.fashtry.backend.common.config.CosProperties;
import com.fashtry.backend.common.enums.ResultCode;
import com.fashtry.backend.common.exception.BusinessException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 腾讯云数据万象：同步图片审核（ObjectKey 已在桶内，走内网审核，不额外产生外网下行流量）。
 * <p>
 * 注意：顶层 {@code Result} 可能为 0，但 {@code PoliticsInfo}/{@code PornInfo} 等子节点中 {@code HitFlag} 仍为 1，
 * 必须一并解析，否则会误放行涉政等内容。
 * </p>
 */
@Service
public class CosImageModerationService {

    private static final Logger log = LoggerFactory.getLogger(CosImageModerationService.class);

    /** 日志中打印的响应体最大长度（避免撑爆日志） */
    private static final int CI_LOG_BODY_MAX = 16_000;

    /** 优先匹配「顶层」Result + Label（避免命中 PornInfo 等嵌套节点里的 Label） */
    private static final Pattern XML_RESULT_AND_LABEL = Pattern.compile(
            "<Result>\\s*(\\d+)\\s*</Result>\\s*<Label>\\s*([^<]+?)\\s*</Label>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern XML_SUBLABEL_AFTER = Pattern.compile(
            "<SubLabel>\\s*([^<]+?)\\s*</SubLabel>",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern XML_INNER_LABEL = Pattern.compile(
            "<Label>\\s*([^<]+?)\\s*</Label>", Pattern.CASE_INSENSITIVE);

    private static final Pattern XML_INNER_SUBLABEL = Pattern.compile(
            "<SubLabel>\\s*([^<]+?)\\s*</SubLabel>", Pattern.CASE_INSENSITIVE);

    private static final Pattern XML_HIT_FLAG = Pattern.compile(
            "<HitFlag>\\s*(\\d+)\\s*</HitFlag>", Pattern.CASE_INSENSITIVE);

    private static final Pattern XML_ERROR_CODE = Pattern.compile("<Code>\\s*([^<]+)\\s*</Code>", Pattern.CASE_INSENSITIVE);
    private static final Pattern XML_ERROR_MSG = Pattern.compile("<Message>\\s*([^<]+)\\s*</Message>", Pattern.CASE_INSENSITIVE);

    /** JSON 响应（部分网关/SDK 返回 JSON） */
    private static final Pattern JSON_RESULT = Pattern.compile("\"Result\"\\s*:\\s*(\\d+)");
    private static final Pattern JSON_LABEL = Pattern.compile("\"Label\"\\s*:\\s*\"([^\"]*)\"");

    /**
     * 与腾讯云各场景节点对应；顺序为拦截优先级（涉政优先于色情等）。
     */
    private static final String[][] SCENE_TAGS_AND_LABEL_HINT = {
            {"PoliticsInfo", "Political"},
            {"TerroristInfo", "Terror"},
            {"TerrorismInfo", "Terror"},
            {"PornInfo", "Porn"},
            {"AdsInfo", "Ads"},
            {"IllegalInfo", "Illegal"},
            {"QualityInfo", "Quality"},
    };

    private final COSClient cosClient;
    private final CosProperties cosProperties;
    private final RestTemplate restTemplate;

    public CosImageModerationService(COSClient cosClient, CosProperties cosProperties, RestTemplate restTemplate) {
        this.cosClient = cosClient;
        this.cosProperties = cosProperties;
        this.restTemplate = restTemplate;
    }

    /**
     * 审核桶内已上传对象；未通过则抛出业务异常（调用方应删除对象）。
     */
    public void assertImagePassed(String objectKey) {
        if (!cosProperties.isCiModerationEnabled()) {
            log.info("[CI] skip: ciModerationEnabled=false objectKey={}", objectKey);
            return;
        }
        if (!StringUtils.hasText(objectKey)) {
            log.info("[CI] skip: empty objectKey");
            return;
        }
        try {
            log.info("[CI] start objectKey={} bucket={} bizType={} ciBlockSuspect={}",
                    objectKey,
                    cosProperties.getBucketName(),
                    StringUtils.hasText(cosProperties.getCiBizType()) ? cosProperties.getCiBizType() : "(default)",
                    cosProperties.isCiBlockSuspect());

            GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(
                    cosProperties.getBucketName(), objectKey, HttpMethodName.GET);
            req.setExpiration(new Date(System.currentTimeMillis() + 600_000L));
            req.addRequestParameter("ci-process", "sensitive-content-recognition");
            if (StringUtils.hasText(cosProperties.getCiBizType())) {
                req.addRequestParameter("biz-type", cosProperties.getCiBizType().trim());
            }
            URL signed = cosClient.generatePresignedUrl(req);
            log.info("[CI] request presigned GET (truncated): {}", truncate(signed.toString(), 500));

            ResponseEntity<String> resp = restTemplate.getForEntity(signed.toURI(), String.class);
            String body = resp.getBody();
            if (!resp.getStatusCode().is2xxSuccessful() || body == null) {
                log.warn("[CI] bad http status={} bodyNull={}", resp.getStatusCode(), body == null);
                throw new BusinessException(ResultCode.UPLOAD_FAIL, "图片审核服务异常（HTTP " + resp.getStatusCode().value() + "）");
            }
            String raw = stripBom(body.trim());
            log.info("[CI] response http={} contentLength={} bodyPrefix={}",
                    resp.getStatusCode().value(),
                    raw.length(),
                    truncate(raw, CI_LOG_BODY_MAX));

            if (raw.contains("<Error") || raw.contains("<Error>")) {
                log.warn("[CI] COS returned Error wrapper: {}", truncate(raw, CI_LOG_BODY_MAX));
                throw new BusinessException(ResultCode.UPLOAD_FAIL, parseCosXmlErrorMessage(raw));
            }

            String block = extractRecognitionResultBlock(raw);
            if (block == null) {
                log.warn("[CI] no <RecognitionResult> in body (maybe JSON only or unexpected format). startsWithJson={}",
                        raw.startsWith("{"));
            } else {
                log.info("[CI] RecognitionResult block length={}", block.length());
                log.info("[CI] HitFlag sequence (all in document order): {}", summarizeHitFlagSequence(block));
                logSceneScanDetails(block);
            }

            NestedSceneHit nested = block != null ? scanNestedSceneHitFlags(block) : null;
            if (nested != null) {
                log.info("[CI] nestedScene chosen: hitFlag={} label={} subLabel={}",
                        nested.hitFlag(), nested.label(), nested.subLabel());
                if (nested.hitFlag() == 1) {
                    log.warn("[CI] REJECT by nested HitFlag=1");
                    throw new BusinessException(ResultCode.PARAM_INVALID,
                            "图片未通过审核：" + labelBracketZh(nested.label(), nested.subLabel()));
                }
                if (nested.hitFlag() == 2 && cosProperties.isCiBlockSuspect()) {
                    log.warn("[CI] REJECT by nested HitFlag=2 (ciBlockSuspect=true)");
                    throw new BusinessException(ResultCode.PARAM_INVALID,
                            "图片疑似违规：" + labelBracketZh(nested.label(), nested.subLabel()));
                }
                if (nested.hitFlag() == 2) {
                    log.info("[CI] nested HitFlag=2 ignored (ciBlockSuspect=false), continue to top Result");
                }
            } else {
                log.info("[CI] nestedScene: none (no non-zero HitFlag in known scene tags)");
            }

            ModerationOutcome out = parseOutcome(raw, block);
            log.info("[CI] parsed top: result={} label={} subLabel={}", out.result, out.label, out.subLabel);

            if (out.result < 0) {
                log.warn("[CI] parse failed: cannot read Result/Label. bodyPrefix={}", truncate(raw, CI_LOG_BODY_MAX));
                throw new BusinessException(ResultCode.UPLOAD_FAIL, "图片审核服务返回格式异常，请稍后重试");
            }
            if (out.result == 0) {
                log.info("[CI] PASS objectKey={} (topResult=0)", objectKey);
                return;
            }
            if (out.result == 1) {
                log.warn("[CI] REJECT by top Result=1 label={}", out.label);
                throw new BusinessException(ResultCode.PARAM_INVALID,
                        "图片未通过审核：" + labelBracketZh(out.label, out.subLabel));
            }
            if (out.result == 2 && cosProperties.isCiBlockSuspect()) {
                log.warn("[CI] REJECT by top Result=2 (ciBlockSuspect=true) label={}", out.label);
                throw new BusinessException(ResultCode.PARAM_INVALID,
                        "图片疑似违规：" + labelBracketZh(out.label, out.subLabel));
            }
            log.info("[CI] PASS objectKey={} topResult={} (suspect not blocked or unknown code, treat as pass)", objectKey, out.result);
        } catch (BusinessException e) {
            log.warn("[CI] BusinessException key={} code={} msg={}", objectKey, e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("[CI] unexpected error key={}", objectKey, e);
            throw new BusinessException(ResultCode.UPLOAD_FAIL, "图片审核失败，请稍后重试");
        }
    }

    /** 文档中出现的所有 HitFlag 数值顺序，便于对照子节点 */
    private static String summarizeHitFlagSequence(String block) {
        Matcher m = XML_HIT_FLAG.matcher(block);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        while (m.find() && n < 48) {
            if (n > 0) {
                sb.append(',');
            }
            sb.append(m.group(1).trim());
            n++;
        }
        return sb.isEmpty() ? "(no <HitFlag> in block)" : sb.toString();
    }

    /** 各场景标签是否存在及内部 HitFlag / Label，便于对照腾讯云文档 */
    private void logSceneScanDetails(String block) {
        for (String[] row : SCENE_TAGS_AND_LABEL_HINT) {
            String tag = row[0];
            String inner = extractFirstTagInner(block, tag);
            if (inner == null) {
                log.info("[CI] scene <{}>: absent", tag);
                continue;
            }
            Matcher hf = XML_HIT_FLAG.matcher(inner);
            String hit = hf.find() ? hf.group(1).trim() : "?";
            String lab = firstMatch(inner, XML_INNER_LABEL);
            String sub = firstMatch(inner, XML_INNER_SUBLABEL);
            log.info("[CI] scene <{}>: HitFlag={} Label={} SubLabel={}", tag, hit, lab, sub);
        }
    }

    private static String extractRecognitionResultBlock(String xml) {
        int start = xml.indexOf("<RecognitionResult");
        if (start < 0) {
            return null;
        }
        int end = xml.indexOf("</RecognitionResult>", start);
        if (end < 0) {
            return null;
        }
        return xml.substring(start, end + "</RecognitionResult>".length());
    }

    /**
     * 扫描 PoliticsInfo / PornInfo 等子节点中的 HitFlag；顶层 Result 为 0 时仍可能在此命中。
     */
    private static NestedSceneHit scanNestedSceneHitFlags(String block) {
        for (String[] row : SCENE_TAGS_AND_LABEL_HINT) {
            String tag = row[0];
            String hint = row[1];
            String inner = extractFirstTagInner(block, tag);
            if (inner == null) {
                continue;
            }
            Matcher hf = XML_HIT_FLAG.matcher(inner);
            if (!hf.find()) {
                continue;
            }
            int hit = Integer.parseInt(hf.group(1).trim());
            if (hit == 0) {
                continue;
            }
            String label = firstMatch(inner, XML_INNER_LABEL);
            String sub = firstMatch(inner, XML_INNER_SUBLABEL);
            if (!StringUtils.hasText(label)) {
                label = hint;
            }
            return new NestedSceneHit(hit, label, sub);
        }
        return null;
    }

    private static String extractFirstTagInner(String xml, String tag) {
        Pattern p = Pattern.compile(
                "<" + Pattern.quote(tag) + "[^>]*>([\\s\\S]*?)</" + Pattern.quote(tag) + ">",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    private static String parseCosXmlErrorMessage(String xml) {
        String code = firstMatch(xml, XML_ERROR_CODE);
        String msg = firstMatch(xml, XML_ERROR_MSG);
        StringBuilder sb = new StringBuilder("图片审核服务错误");
        if (StringUtils.hasText(code)) {
            sb.append("【").append(code.trim()).append("】");
        }
        if (StringUtils.hasText(msg)) {
            sb.append(" ").append(msg.trim());
        }
        return sb.toString();
    }

    private static String firstMatch(String s, Pattern p) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private record NestedSceneHit(int hitFlag, String label, String subLabel) {}

    private record ModerationOutcome(int result, String label, String subLabel) {}

    private static ModerationOutcome parseOutcome(String body, String precomputedBlock) {
        if (!StringUtils.hasText(body)) {
            return new ModerationOutcome(-1, null, null);
        }
        if (body.startsWith("{")) {
            return parseJsonOutcome(body);
        }
        String block = precomputedBlock != null ? precomputedBlock : extractRecognitionResultBlock(body);
        if (block == null) {
            return new ModerationOutcome(-1, null, null);
        }
        return parseXmlOutcomeFromBlock(block);
    }

    private static ModerationOutcome parseJsonOutcome(String json) {
        Matcher mr = JSON_RESULT.matcher(json);
        if (!mr.find()) {
            return new ModerationOutcome(-1, null, null);
        }
        int result = Integer.parseInt(mr.group(1));
        String label = null;
        Matcher ml = JSON_LABEL.matcher(json);
        if (ml.find()) {
            label = ml.group(1);
        }
        return new ModerationOutcome(result, label, null);
    }

    private static ModerationOutcome parseXmlOutcomeFromBlock(String block) {
        Matcher m = XML_RESULT_AND_LABEL.matcher(block);
        if (m.find()) {
            int result = Integer.parseInt(m.group(1).trim());
            String label = m.group(2).trim();
            String sub = null;
            Matcher sm = XML_SUBLABEL_AFTER.matcher(block);
            if (sm.find()) {
                sub = sm.group(1).trim();
            }
            return new ModerationOutcome(result, label, sub);
        }
        Matcher mr = Pattern.compile("<Result>\\s*(\\d+)\\s*</Result>", Pattern.CASE_INSENSITIVE).matcher(block);
        if (mr.find()) {
            return new ModerationOutcome(Integer.parseInt(mr.group(1).trim()), null, null);
        }
        return new ModerationOutcome(-1, null, null);
    }

    /**
     * 将腾讯云 Label/SubLabel 映射为前端可读的中文标签（【涉政】【色情】【血腥】等）。
     */
    static String labelBracketZh(String label, String subLabel) {
        String zh = mapLabelToZh(label, subLabel);
        return "【" + zh + "】";
    }

    static String mapLabelToZh(String label, String subLabel) {
        String l = label != null ? label.trim() : "";
        String s = subLabel != null ? subLabel.trim() : "";
        String u = (l + " " + s).toLowerCase(Locale.ROOT);

        if (l.equalsIgnoreCase("Normal")) {
            return "正常";
        }
        // 色情 / 低俗
        if (matchesAny(l, "Porn", "Sexy", "NSFW")
                || u.contains("porn") || u.contains("sexy") || u.contains("sexbehavior")) {
            return "色情";
        }
        // 涉政
        if (matchesAny(l, "Political", "Politics", "Politician")
                || u.contains("politic") || u.contains("leader") || u.contains("敏感人物")) {
            return "涉政";
        }
        // 暴恐 / 血腥 / 暴力（Illegal 单独归为违法违规）
        if (matchesAny(l, "Terror", "Terrorism", "Violence")
                || u.contains("terror") || u.contains("violence") || u.contains("bloody")
                || u.contains("blood") || u.contains("gore") || u.contains("weapon")) {
            return "血腥";
        }
        if (matchesAny(l, "Abuse")) {
            return "谩骂";
        }
        if (matchesAny(l, "Ads", "Ad", "Advertising")) {
            return "广告";
        }
        if (matchesAny(l, "Quality")) {
            return "图片质量";
        }
        if (matchesAny(l, "Illegal")) {
            return "违法违规";
        }
        if (matchesAny(l, "Teenager", "Minor")) {
            return "未成年相关";
        }
        // 未识别英文标签时回显原文，便于排查
        if (StringUtils.hasText(l) && !l.equalsIgnoreCase("Normal")) {
            return l;
        }
        return "违规";
    }

    private static boolean matchesAny(String label, String... keys) {
        if (!StringUtils.hasText(label)) {
            return false;
        }
        for (String k : keys) {
            if (label.equalsIgnoreCase(k)) {
                return true;
            }
        }
        return false;
    }

    private static String stripBom(String s) {
        if (s != null && s.startsWith("\uFEFF")) {
            return s.substring(1);
        }
        return s;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
