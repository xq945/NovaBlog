package com.novablog.vo;

import lombok.Data;

/**
 * 标签信息 VO
 */
@Data
public class TagVO {

    /**
     * 标签ID
     */
    private Long id;

    /**
     * 标签名称
     */
    private String name;
}
