package com.novablog.rag.dto;

import lombok.Data;

import java.util.List;

/**
 * 问答响应 DTO
 */
@Data
public class AskResponse {

    /**
     * AI 生成的回答（Markdown 格式）
     */
    private String answer;

    /**
     * 回答引用的来源文章列表
     */
    private List<SourceArticle> sourceArticles;

    /**
     * 会话ID（登录用户带会话记录时使用）
     */
    private Long sessionId;
}
