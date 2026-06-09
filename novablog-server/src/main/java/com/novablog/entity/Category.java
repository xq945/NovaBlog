package com.novablog.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分类实体类
 * 对应数据库 category 表
 */
@Data
public class Category {

    /**
     * 主键ID
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

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
