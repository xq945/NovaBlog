package com.novablog.aop;

import com.novablog.common.annotation.AutoFillTime;
import com.novablog.common.enums.OperationType;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动填充时间字段切面
 * 拦截标注了 @AutoFillTime 的方法，在方法执行前为实体对象设置 createTime/updateTime
 */
@Aspect
@Component
public class AutoFillTimeAspect {

    private static final String CREATE_TIME_FIELD = "createTime";
    private static final String UPDATE_TIME_FIELD = "updateTime";

    /**
     * 缓存类的字段信息，避免重复反射带来的性能开销
     */
    private final ConcurrentHashMap<Class<?>, Field[]> fieldCache = new ConcurrentHashMap<>();

    /**
     * 定义切入点：所有标注 @AutoFillTime 的方法
     */
    @Pointcut("@annotation(com.novablog.common.annotation.AutoFillTime)")
    public void autoFillTimePointcut() {
    }

    /**
     * 在目标方法执行前自动填充时间字段
     *
     * @param joinPoint    连接点
     * @param autoFillTime 注解信息
     */
    @Before("autoFillTimePointcut() && @annotation(autoFillTime)")
    public void autoFill(JoinPoint joinPoint, AutoFillTime autoFillTime) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return;
        }

        // 查找第一个非空的实体参数（假设业务方法中实体参数位于前面）
        Object entity = Arrays.stream(args)
                .filter(arg -> arg != null && !isSimpleType(arg.getClass()))
                .findFirst()
                .orElse(null);

        if (entity == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        OperationType operationType = autoFillTime.value();

        if (operationType == OperationType.INSERT) {
            setFieldIfNull(entity, CREATE_TIME_FIELD, now);
            setFieldIfNull(entity, UPDATE_TIME_FIELD, now);
        } else if (operationType == OperationType.UPDATE) {
            setField(entity, UPDATE_TIME_FIELD, now);
        }
    }

    /**
     * 当字段存在且当前值为 null 时设置值
     */
    private void setFieldIfNull(Object entity, String fieldName, Object value) {
        Field field = getField(entity.getClass(), fieldName);
        if (field == null) {
            return;
        }
        try {
            field.setAccessible(true);
            if (field.get(entity) == null) {
                field.set(entity, value);
            }
        } catch (IllegalAccessException e) {
            // 反射设置失败时静默忽略，避免影响主流程
        }
    }

    /**
     * 直接设置字段值（不判断是否为 null）
     */
    private void setField(Object entity, String fieldName, Object value) {
        Field field = getField(entity.getClass(), fieldName);
        if (field == null) {
            return;
        }
        try {
            field.setAccessible(true);
            field.set(entity, value);
        } catch (IllegalAccessException e) {
            // 反射设置失败时静默忽略，避免影响主流程
        }
    }

    /**
     * 从缓存或类定义中获取指定字段
     */
    private Field getField(Class<?> clazz, String fieldName) {
        Field[] fields = fieldCache.computeIfAbsent(clazz, Class::getDeclaredFields);
        return Arrays.stream(fields)
                .filter(field -> field.getName().equals(fieldName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 判断是否为简单类型（基本类型、包装类、String 等），简单类型不视为实体
     */
    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz == String.class
                || Number.class.isAssignableFrom(clazz)
                || clazz == Boolean.class
                || clazz == Character.class;
    }
}
