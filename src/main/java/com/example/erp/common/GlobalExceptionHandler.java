package com.example.erp.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 参数/业务错误：message 直接展示给用户
     */
    @ExceptionHandler({IllegalArgumentException.class, BusinessException.class})
    public ResponseEntity<Result<Void>> handleBadRequest(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(Result.CODE_BAD_REQUEST, e.getMessage()));
    }

    /**
     * 缺少必填参数 / 参数类型错误：应答为 400 的参数问题，而非笼统的系统异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(Result.CODE_BAD_REQUEST, "缺少必填参数: " + e.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(Result.CODE_BAD_REQUEST, "参数类型错误: " + e.getName()));
    }

    /**
     * 唯一键冲突（如并发提交重复 SKU、单号撞号）：给用户可理解的 400 提示
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(Result.CODE_BAD_REQUEST, "数据已存在或并发冲突，请勿重复提交"));
    }

    /**
     * 未登录/凭证无效：401；已登录但权限不足：403
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Result<Void>> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(401, e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Result<Void>> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(403, e.getMessage()));
    }

    /**
     * 未预期异常：不向用户暴露内部细节，仅记录日志
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(Result.CODE_ERROR, "系统异常，请稍后重试"));
    }
}
