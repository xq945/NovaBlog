package com.novablog.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。
 * 标注在 Controller 方法上，由 OperationLogAspect 自动记录操作日志。
 *
 * <pre>
 * 使用示例：
 * &#64;LogOperation(target = "ARTICLE", operation = "CREATE", detail = "发布文章：{0}")
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogOperation {

    /**
     * 操作对象，如 ARTICLE / COMMENT / USER / CATEGORY / TAG
     */
    String target();

    /**
     * 操作类型：CREATE / UPDATE / DELETE / OTHER
     */
    String operation() default "OTHER";

    /**
     * 操作详情模板，支持 {0}, {1} 等参数占位（对应方法参数下标）
     */
    String detail() default "";
}
