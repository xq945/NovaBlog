package com.novablog.chat.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话会话实体
 */
@Data
public class ChatSession {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 消息数量
     */
    private Integer messageCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
