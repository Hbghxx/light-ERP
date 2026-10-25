package com.example.erp.common;

/**
 * 业务异常：message 可直接展示给用户
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
