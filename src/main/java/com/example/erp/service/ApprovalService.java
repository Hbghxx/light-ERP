package com.example.erp.service;

import com.example.erp.entity.ApprovalRecord;
import com.example.erp.entity.ApprovalRule;
import com.example.erp.mapper.ApprovalMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 审批流公共逻辑：规则命中、审批记录落库。
 * 默认无审批角色的规则视为"系统自动通过"；无命中规则时兜底为 ADMIN(角色1) 审批。
 */
@Service
public class ApprovalService {

    public static final long FALLBACK_APPROVER_ROLE = 1L;
    public static final String STATUS_PENDING = "1";
    public static final String STATUS_APPROVED = "2";
    public static final String STATUS_REJECTED = "3";
    public static final String STATUS_CANCELLED = "4";

    @Autowired
    private ApprovalMapper approvalMapper;

    /** 按场景与金额命中规则；无命中兜底为超管审批 */
    public ApprovalRule resolveRule(String scene, BigDecimal amount) {
        ApprovalRule rule = approvalMapper.findRule(scene, amount);
        if (rule == null) {
            rule = new ApprovalRule();
            rule.setScene(scene);
            rule.setApproverRoleId(FALLBACK_APPROVER_ROLE);
        }
        return rule;
    }

    /**
     * 单据提交后调用（与单据同一事务）：写 SUBMIT 记录；
     * 若命中"自动通过"规则，再写 AUTO_PASS 记录，返回 true 告知调用方直接入账。
     */
    public boolean onSubmitted(String docType, Long docId, BigDecimal amount, Long applicantId) {
        ApprovalRule rule = resolveRule(docType, amount);
        insert(docType, docId, rule.getId(), rule.getApproverRoleId(), applicantId, null, "SUBMIT", null);
        if (rule.getApproverRoleId() == null) {
            insert(docType, docId, rule.getId(), null, applicantId, null, "AUTO_PASS", "金额未达审批线，系统自动通过");
            return true;
        }
        return false;
    }

    /** 审批/驳回动作记录（与单据 CAS 更新同一事务；applicantId 传单据制单人） */
    public void recordAction(String docType, Long docId, Long applicantId, Long approverId,
                             boolean approved, String opinion) {
        insert(docType, docId, null, null, applicantId, approverId,
                approved ? "APPROVE" : "REJECT", opinion);
    }

    /** 作废记录 */
    public void recordCancel(String docType, Long docId, Long applicantId, Long operatorId) {
        insert(docType, docId, null, null, applicantId, operatorId, "CANCEL", null);
    }

    public List<ApprovalRecord> listByDoc(String docType, Long docId) {
        return approvalMapper.listByDoc(docType, docId);
    }

    private void insert(String docType, Long docId, Long ruleId, Long approverRoleId,
                        Long applicantId, Long approverId, String action, String opinion) {
        ApprovalRecord r = new ApprovalRecord();
        r.setDocType(docType);
        r.setDocId(docId);
        r.setRuleId(ruleId);
        r.setApproverRoleId(approverRoleId);
        r.setApplicantId(applicantId);
        r.setApproverId(approverId);
        r.setAction(action);
        r.setOpinion(opinion);
        approvalMapper.insertRecord(r);
    }
}
