package com.novablog.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 生成摘要响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SummaryVO {

    /**
     * 生成的摘要
     */
    private String summary;
}
