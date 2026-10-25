package com.example.erp.common;

/** 未登录/凭证无效：由全局异常处理器映射为 HTTP 401 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
