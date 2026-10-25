package com.example.erp.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计埋点注解：标注在 Service 公共方法上，方法成功返回后由 AuditLogAspect 写审计日志。
 * docId 取返回值(Long)或第一个 Long 参数；detail 记录方法名与参数快照。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    String action();
    String docType();
}
