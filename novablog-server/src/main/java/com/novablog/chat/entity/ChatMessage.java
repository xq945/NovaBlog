package com.novablog.chat.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话消息实体
 */
@Data
public class ChatMessage {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 所属会话ID
     */
    private Long sessionId;

    /**
     * 角色：user/assistant
     */
    private String role;

    /**
     * 消息内容（Markdown）
     */
    private String content;

    /**
     * AI 回答引用的来源文章 JSON
     */
    private String sourcesJson;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
