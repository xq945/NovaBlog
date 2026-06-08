package com.novablog.dto;

import lombok.Data;

/**
 * 分类请求参数
 */
@Data
public class CategoryDTO {

    /**
     * 分类ID（修改时必填）
     */
    private Long id;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 分类描述
     */
    private String description;
}
