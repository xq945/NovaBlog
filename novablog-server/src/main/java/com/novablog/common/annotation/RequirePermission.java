package com.novablog.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解。
 * 标注在 Controller 方法上，由 PermissionAspect 统一处理权限校验。
 * 支持精确权限和 own（数据归属）权限两种模式。
 *
 * <pre>
 * 使用示例：
 * &#64;RequirePermission("article:delete")           // 需要 article:delete 权限
 * &#64;RequirePermission("article:delete:own")       // 需要 article:delete:own 权限
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 权限标识，如 "article:delete"
     */
    String value();
}
