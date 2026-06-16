package com.novablog.common.enums;

/**
 * 数据库操作类型
 * 用于配合 @AutoFillTime 注解声明需要自动填充时间字段的操作
 */
public enum OperationType {
    /**
     * 插入操作：自动填充 createTime 和 updateTime
     */
    INSERT,

    /**
     * 更新操作：自动填充 updateTime
     */
    UPDATE
}
