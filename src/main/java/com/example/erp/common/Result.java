package com.example.erp.common;

import lombok.Data;

/**
 * 统一响应体：code = 0 表示成功，非 0 表示业务失败
 */
@Data
public class Result<T> {
    public static final int CODE_SUCCESS = 0;
    public static final int CODE_BAD_REQUEST = 400;
    public static final int CODE_ERROR = 500;

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = CODE_SUCCESS;
        r.message = "操作成功";
        r.data = data;
        return r;
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
