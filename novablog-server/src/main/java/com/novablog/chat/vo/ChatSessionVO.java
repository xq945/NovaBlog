package com.novablog.chat.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表视图对象
 */
@Data
public class ChatSessionVO {

    /**
     * 主键ID
     */
    private Long id;

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
