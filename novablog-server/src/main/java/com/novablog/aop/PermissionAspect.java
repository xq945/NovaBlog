package com.novablog.aop;

import com.novablog.common.UserContext;
import com.novablog.common.annotation.RequireAdmin;
import com.novablog.common.constant.RoleConstant;
import com.novablog.common.exception.BusinessException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * 权限校验切面
 * 统一处理 @RequireAdmin 注解的权限判断
 */
@Aspect
@Component
public class PermissionAspect {

    /**
     * 校验当前用户是否为管理员
     */
    @Before("@annotation(requireAdmin)")
    public void checkAdmin(RequireAdmin requireAdmin) {
        String role = UserContext.getRole();
        if (!RoleConstant.ADMIN.equals(role)) {
            throw new BusinessException(403, "无权访问");
        }
    }
}
