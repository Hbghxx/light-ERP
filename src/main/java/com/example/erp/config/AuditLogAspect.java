package com.example.erp.config;

import com.example.erp.common.AuditLog;
import com.example.erp.common.UserContext;
import com.example.erp.service.AuditService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;

/**
 * @AuditLog 切面：业务动作成功返回后统一写审计。
 * docId 优先取方法返回值(Long，如提交返回单据ID)，否则取第一个 Long 参数(如审批的 docId)。
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    @Autowired
    private AuditService auditService;

    @AfterReturning(pointcut = "@annotation(auditLog)", returning = "result")
    public void afterReturning(JoinPoint jp, AuditLog auditLog, Object result) {
        try {
            Long docId = result instanceof Long l ? l : firstLongArg(jp.getArgs());
            Map<String, Object> detail = Map.of(
                    "method", jp.getSignature().toShortString(),
                    "args", Arrays.deepToString(jp.getArgs()));
            auditService.write(UserContext.currentUserId(), auditLog.action(), auditLog.docType(),
                    docId, null, detail, null);
        } catch (Exception e) {
            // 审计失败不阻断业务，但要留痕便于排查
            log.error("审计切面写日志失败: {}", auditLog.action(), e);
        }
    }

    private Long firstLongArg(Object[] args) {
        for (Object a : args) {
            if (a instanceof Long l) {
                return l;
            }
        }
        return null;
    }
}
