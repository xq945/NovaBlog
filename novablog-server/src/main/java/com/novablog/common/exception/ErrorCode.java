package com.novablog.common.exception;

import lombok.Getter;

/**
 * 统一错误码枚举
 * 为前端提供结构化的错误标识，便于错误处理和国际化
 */
@Getter
public enum ErrorCode {

    /**
     * 成功
     */
    SUCCESS(200, "操作成功"),

    /**
     * 请求参数错误
     */
    BAD_REQUEST(400, "请求参数错误"),

    /**
     * 未登录或 Token 无效
     */
    UNAUTHORIZED(401, "请先登录"),

    /**
     * 权限不足
     */
    FORBIDDEN(403, "无权访问"),

    /**
     * 资源不存在
     */
    NOT_FOUND(404, "资源不存在"),

    /**
     * 资源冲突（如重复点赞）
     */
    CONFLICT(409, "资源冲突"),

    /**
     * 服务器内部错误
     */
    INTERNAL_ERROR(500, "系统繁忙，请稍后重试"),

    /**
     * 文件上传过大
     */
    FILE_TOO_LARGE(4001, "文件大小不能超过5MB");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
