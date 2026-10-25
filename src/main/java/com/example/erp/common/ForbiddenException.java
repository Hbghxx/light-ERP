package com.example.erp.common;

/** 已登录但权限不足：由全局异常处理器映射为 HTTP 403 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
