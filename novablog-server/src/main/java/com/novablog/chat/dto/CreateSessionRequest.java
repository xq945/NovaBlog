package com.novablog.chat.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建会话请求
 */
@Data
public class CreateSessionRequest {

    /**
     * 会话标题，为空时由 AI 自动生成
     */
    @Size(max = 200, message = "标题长度不能超过200字符")
    private String title;
}
