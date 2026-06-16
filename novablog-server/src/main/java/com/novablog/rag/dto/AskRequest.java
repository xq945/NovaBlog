package com.novablog.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 问答请求 DTO
 */
@Data
public class AskRequest {

    /**
     * 用户问题
     */
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过500字符")
    private String question;
}
