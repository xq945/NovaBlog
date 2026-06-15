package com.novablog.service.component;

import com.novablog.common.UserContext;
import com.novablog.common.constant.RoleConstant;
import com.novablog.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 文章权限校验组件
 * 封装文章操作（修改/删除）的权限判断逻辑
 */
@Component
public class ArticlePermissionComponent {

    /**
     * 校验当前用户是否有权操作指定文章
     *
     * @param authorId 文章作者ID
     */
    public void checkPermission(Long authorId) {
        Long currentUserId = UserContext.getUserId();
        String currentRole = UserContext.getRole();

        // 自己或管理员可以操作
        if (!Objects.equals(currentUserId, authorId) && !RoleConstant.ADMIN.equals(currentRole)) {
            throw new BusinessException(403, "无权操作");
        }
    }
}
