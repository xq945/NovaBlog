package com.novablog.dto.response;

import lombok.Data;

/**
 * 文件导入文章结果
 */
@Data
public class ArticleImportResult {

    /**
     * 提取的标题
     */
    private String title;

    /**
     * 提取的正文
     */
    private String content;

    /**
     * 自动生成的摘要
     */
    private String summary;
}
