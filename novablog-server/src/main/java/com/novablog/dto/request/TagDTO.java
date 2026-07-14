package com.novablog.dto.request;

import lombok.Data;

/**
 * 标签请求参数
 */
@Data
public class TagDTO {

    /**
     * 标签ID（修改时必填）
     */
    private Long id;

    /**
     * 标签名称
     */
    private String name;
}
