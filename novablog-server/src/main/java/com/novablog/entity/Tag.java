package com.novablog.entity;

import lombok.Data;

import java.time.LocalDateTime;

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

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
