package com.novablog.vo;

import lombok.Data;

/**
 * 分类信息 VO
 */
@Data
public class CategoryVO {

    /**
     * 分类ID
     */
    private Long id;

    /**
     * 分类名称
     */
    private String name;
}
