package com.novablog.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 向量索引状态统计
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IndexStatusVO {

    /**
     * 已索引文章数
     */
    private Long indexed;

    /**
     * 未索引文章数
     */
    private Long unindexed;

    /**
     * 索引失败文章数
     */
    private Long failed;
}
