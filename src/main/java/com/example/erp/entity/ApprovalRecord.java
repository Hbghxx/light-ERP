package com.example.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApprovalRecord {
    private Long id;
    private String docType;
    private Long docId;
    private Long ruleId;
    private Long approverRoleId;
    private Long applicantId;
    private Long approverId;
    private String action;      // SUBMIT / AUTO_PASS / APPROVE / REJECT / CANCEL
    private String opinion;
    private LocalDateTime createdAt;
}
