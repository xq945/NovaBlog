package com.novablog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 生成摘要请求
 */
@Data
public class SummaryRequest {

    /**
     * 文章正文
     */
    @NotBlank(message = "正文不能为空")
    @Size(min = 100, max = 50000, message = "正文长度必须在 100 到 50000 字符之间")
    private String content;
}
