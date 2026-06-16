package com.novablog.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 带会话的问答请求
 */
@Data
public class ChatAskRequest {

    /**
     * 会话ID，为空时创建新会话
     */
    private Long sessionId;

    /**
     * 用户问题
     */
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过500字符")
    private String question;
}
