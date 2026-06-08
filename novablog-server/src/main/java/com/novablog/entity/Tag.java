package com.novablog.entity;

import lombok.Data;

/**
 * 标签实体类
 * 对应数据库 tag 表
 */
@Data
public class Tag {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 标签名称
     */
    private String name;
}
