package com.novablog.aop;

import com.novablog.common.annotation.RequireAdmin;
import com.novablog.common.annotation.RequirePermission;
import com.novablog.common.constant.RoleConstant;
import com.novablog.common.exception.BusinessException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 权限校验切面
 * 统一处理 @RequireAdmin 和 @RequirePermission 注解的权限判断
 */
@Aspect
@Component
public class PermissionAspect {

    /**
     * 校验当前用户是否为管理员。
     * 支持方法级别和类级别的 @RequireAdmin。
     *
     * <p>以 SecurityContext 中的 authority 为准，而不是 UserContext 里的单个角色名：
     * 角色查询没有 ORDER BY，多角色用户的角色顺序无法保证，按第一个角色判断会误判。
     */
    @Before("@annotation(requireAdmin) || @within(requireAdmin)")
    public void checkAdmin(RequireAdmin requireAdmin) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(401, "请先登录");
        }
        String adminAuthority = "ROLE_" + RoleConstant.ADMIN;
        boolean isAdmin = authentication.getAuthorities().stream()
            .anyMatch(a -> adminAuthority.equals(a.getAuthority()));
        if (!isAdmin) {
            throw new BusinessException(403, "无权访问");
        }
    }

    /**
     * 校验当前用户是否拥有指定权限。
     * 先从 SecurityContext 获取用户的权限列表，检查是否包含注解要求的权限。
     * 支持 ":own" 后缀的权限标识。
     */
    @Before("@annotation(requirePermission)")
    public void checkPermission(JoinPoint joinPoint, RequirePermission requirePermission) {
        String required = requirePermission.value();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(401, "请先登录");
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        boolean hasPermission = authorities.stream()
            .anyMatch(a -> a.getAuthority().equals(required));

        if (!hasPermission) {
            throw new BusinessException(403, "无权访问");
        }
    }
}
