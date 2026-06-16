package com.novablog.chat.vo;

import com.novablog.rag.dto.SourceArticle;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话消息视图对象
 */
@Data
public class ChatMessageVO {

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
     * 消息内容
     */
    private String content;

    /**
     * AI 回答引用的来源文章
     */
    private List<SourceArticle> sources;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
