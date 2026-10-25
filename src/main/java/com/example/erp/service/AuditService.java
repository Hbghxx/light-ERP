package com.example.erp.service;

import com.example.erp.entity.SysAuditLog;
import com.example.erp.mapper.SysAuditLogMapper;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 审计日志：业务单据动作与账号/权限变更统一落 sys_audit_log，只增不改。
 * detail 序列化为 JSON 快照；与业务同事务（调用方处于事务中时一并回滚）。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private SysAuditLogMapper auditLogMapper;

    public void write(Long userId, String action, String docType, Long docId, String docNo, Object detail, String ip) {
        SysAuditLog entry = new SysAuditLog();
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setDocType(docType);
        entry.setDocId(docId);
        entry.setDocNo(docNo);
        entry.setDetail(toJson(detail));
        entry.setIp(ip != null ? ip : currentIp());
        auditLogMapper.insert(entry);
    }

    public List<SysAuditLog> listRecent(int limit) {
        return auditLogMapper.listRecent(Math.min(Math.max(limit, 1), 500));
    }

    private String toJson(Object detail) {
        if (detail == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(detail);
        } catch (Exception e) {
            log.warn("审计详情序列化失败，降级为 toString", e);
            return String.valueOf(detail);
        }
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            // 经 Nginx 反代时取真实客户端 IP（nginx 已设置 X-Forwarded-For）
            String xff = attrs.getRequest().getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            return attrs.getRequest().getRemoteAddr();
        } catch (Exception e) {
            return null; // 非请求上下文（如启动初始化）
        }
    }
}
