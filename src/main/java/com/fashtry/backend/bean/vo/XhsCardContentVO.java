package com.fashtry.backend.bean.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 返回的小红书图文结构化内容（与 Prompt JSON 字段一致）。
 */
@Data
public class XhsCardContentVO {

    @JsonAlias({"cover_title"})
    private String coverTitle;

    @JsonAlias({"cover_subtitle"})
    private String coverSubtitle;

    private List<XhsCardPageVO> pages = new ArrayList<>();

    private String cta;

    /** 可复制到小红书的笔记正文（高 Emoji、短段落、#话题标签） */
    @JsonAlias({"note_caption", "caption", "body", "post_text"})
    private String noteCaption;
}
