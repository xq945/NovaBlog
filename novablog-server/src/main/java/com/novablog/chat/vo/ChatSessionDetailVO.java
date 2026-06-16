package com.novablog.chat.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 会话详情视图对象（含消息列表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChatSessionDetailVO extends ChatSessionVO {

    /**
     * 会话消息列表
     */
    private List<ChatMessageVO> messages;
}
