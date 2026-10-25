package com.example.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SysAuditLog {
    private Long id;
    private Long userId;
    private String action;      // CREATE/UPDATE/AUDIT/REJECT/CANCEL/CREATE_USER/RESET_PASSWORD/...
    private String docType;     // INBOUND/OUTBOUND/USER/ROLE
    private Long docId;
    private String docNo;
    private String detail;      // JSON 快照
    private String ip;
    private LocalDateTime createdAt;

    // 关联展示字段
    private String username;
}
