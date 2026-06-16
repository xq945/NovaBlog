package com.novablog.common.annotation;

import com.novablog.common.enums.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自动填充时间字段注解
 * 标注在 Service 层方法上，由 AOP 切面根据操作类型自动设置实体的 createTime/updateTime
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoFillTime {

    /**
     * 操作类型
     *
     * @return INSERT 或 UPDATE
     */
    OperationType value();
}
