package com.novablog.aop;

import com.novablog.common.UserContext;
import com.novablog.common.annotation.LogOperation;
import com.novablog.entity.OperationLog;
import com.novablog.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 操作日志切面。
 * 捕获 @OperationLog 注解的方法，方法执行成功后记录操作日志。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogMapper operationLogMapper;

    @AfterReturning("@annotation(logOperation)")
    public void logSuccess(JoinPoint joinPoint, LogOperation logOperation) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return; // 未登录用户不记录操作日志
        }
        String username = UserContext.getUsername();

        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setTarget(logOperation.target());
        log.setOperation(logOperation.operation());
        log.setTargetId(extractTargetId(joinPoint));
        log.setDetail(formatDetail(logOperation.detail(), joinPoint.getArgs()));

        operationLogMapper.insert(log);
    }

    /**
     * 提取目标 ID。默认取方法第一个参数作为目标 ID。
     */
    private Long extractTargetId(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0 && args[0] instanceof Number) {
            return ((Number) args[0]).longValue();
        }
        return null;
    }

    /**
     * 格式化详情模板，将 {0}、{1} 替换为对应的方法参数。
     */
    private String formatDetail(String template, Object[] args) {
        if (template == null || template.isEmpty() || args == null) {
            return template;
        }
        String result = template;
        for (int i = 0; i < args.length; i++) {
            String placeholder = "{" + i + "}";
            if (result.contains(placeholder)) {
                String value = args[i] != null ? args[i].toString() : "";
                // 避免详情过长
                if (value.length() > 100) {
                    value = value.substring(0, 100) + "...";
                }
                result = result.replace(placeholder, value);
            }
        }
        return result;
    }
}
