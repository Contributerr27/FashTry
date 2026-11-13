package com.fashtry.backend.service.ai;

import org.springframework.util.StringUtils;

/**
 * 小红书爆款图文：固定 System Prompt，强制 JSON 输出。
 */
public final class XhsCardPrompts {

    private XhsCardPrompts() {}

    public static String buildUserPrompt(
            String topic,
            String sellingPoints,
            String audience,
            String brandName,
            String templateStyle,
            boolean hasReferenceImage,
            String referenceStyleHint) {
        String t = safe(topic, 120);
        String sp = safe(sellingPoints, 400);
        String aud = safe(audience, 120);
        String brand = safe(brandName, 40);
        boolean handbook = "handbook".equalsIgnoreCase(templateStyle) || hasReferenceImage;

        StringBuilder sb = new StringBuilder();
        sb.append("你是资深小红书爆款文案编辑。根据用户输入生成「封面卡片 + 内页卡片 + 笔记正文」全套文案。\n");
        sb.append("禁止输出 Markdown 代码块或任何 JSON 以外的文字。\n\n");

        sb.append("【硬性要求】\n");
        sb.append("1. coverTitle：情绪词 + 数字/悬念 + 爆款钩子，8～18 字");
        if (handbook) {
            sb.append("；手册风可用「秒懂XX」「3分钟搞懂」等");
        }
        sb.append("。\n");
        sb.append("2. coverSubtitle：补充卖点，≤28 字。\n");
        if (handbook) {
            sb.append("3. pages：恰好 3 页，问答手册结构：heading 必须是问句（如「什么是…？」），");
            sb.append("body 是极短回答（2～4 行），带少量 Emoji，关键术语可放最后一行。\n");
        } else {
            sb.append("3. pages：恰好 3 页；heading ≤12 字；body 短句、大量 Emoji、1️⃣2️⃣3️⃣ 列表。\n");
        }
        sb.append("4. cta：点赞/收藏号召，带 Emoji，≤20 字。\n");
        sb.append("5. noteCaption【最重要·必填】：可直接复制发到小红书的「笔记正文」，必须同时满足：\n");
        sb.append("   - 大量 Emoji（几乎每行都有）\n");
        sb.append("   - 段落极短：一行一句，用 \\n 换行\n");
        sb.append("   - 口语化种草感，120～220 字（不含标签）\n");
        sb.append("   - 文末必须带 3～5 个话题标签，如：#小红书干货 #AI工具 #效率提升\n");
        sb.append("6. 全文简体中文；勿虚假承诺、勿医疗/投资绝对化。\n\n");

        if (hasReferenceImage) {
            sb.append("【参考模板】用户上传了一张参考模板图，请贴近其信息结构与语气");
            if (StringUtils.hasText(referenceStyleHint)) {
                sb.append("。参考图风格摘要：").append(safe(referenceStyleHint, 280));
            }
            sb.append("。优先输出清晰的问答/知识点分条。\n\n");
        }

        sb.append("【用户输入】\n");
        sb.append("主题：").append(t).append('\n');
        if (StringUtils.hasText(sp)) {
            sb.append("卖点：").append(sp).append('\n');
        }
        if (StringUtils.hasText(aud)) {
            sb.append("受众：").append(aud).append('\n');
        }
        if (StringUtils.hasText(brand)) {
            sb.append("品牌/账号名（可自然植入，勿硬广）：").append(brand).append('\n');
        }

        sb.append("\n【输出 JSON 结构（字段名必须用 camelCase）】\n");
        sb.append("{\n");
        sb.append("  \"coverTitle\": \"string\",\n");
        sb.append("  \"coverSubtitle\": \"string\",\n");
        sb.append("  \"pages\": [\n");
        sb.append("    {\"heading\": \"string\", \"body\": \"string\"},\n");
        sb.append("    {\"heading\": \"string\", \"body\": \"string\"},\n");
        sb.append("    {\"heading\": \"string\", \"body\": \"string\"}\n");
        sb.append("  ],\n");
        sb.append("  \"cta\": \"string\",\n");
        sb.append("  \"noteCaption\": \"string\"\n");
        sb.append("}\n");
        return sb.toString();
    }

    public static String referenceAnalyzeInstruction() {
        return "用中文简短描述这张小红书/笔记模板的视觉风格与信息结构（布局、配色、是否问答、标题形式），"
                + "不超过 80 字，不要输出 JSON。";
    }

    private static String safe(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return "";
        }
        String t = s.trim().replace('\r', ' ').replace('\n', ' ');
        return t.length() > max ? t.substring(0, max) : t;
    }
}
