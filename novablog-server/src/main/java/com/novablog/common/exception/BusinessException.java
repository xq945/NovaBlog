package com.novablog.common.exception;

import lombok.Getter;

/**
 * 业务异常
 * 用于封装业务逻辑中的已知错误场景（如用户名已存在、密码错误等）
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
