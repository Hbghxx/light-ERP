package com.example.erp.mapper;

import com.example.erp.entity.ApprovalRecord;
import com.example.erp.entity.ApprovalRule;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ApprovalMapper {

    /** 按场景与金额命中规则；无命中返回 NULL（调用方兜底为超管审批） */
    @Select("SELECT id, scene, min_amount AS minAmount, max_amount AS maxAmount, " +
            "approver_role_id AS approverRoleId, enabled FROM approval_rule " +
            "WHERE scene = #{scene} AND enabled = 1 " +
            "AND (min_amount IS NULL OR min_amount <= #{amount}) " +
            "AND (max_amount IS NULL OR max_amount > #{amount}) " +
            "ORDER BY id LIMIT 1")
    ApprovalRule findRule(@Param("scene") String scene, @Param("amount") BigDecimal amount);

    @Insert("INSERT INTO approval_record (doc_type, doc_id, rule_id, approver_role_id, applicant_id, approver_id, action, opinion) " +
            "VALUES (#{docType}, #{docId}, #{ruleId}, #{approverRoleId}, #{applicantId}, #{approverId}, #{action}, #{opinion})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRecord(ApprovalRecord record);

    @Select("SELECT id, doc_type AS docType, doc_id AS docId, rule_id AS ruleId, " +
            "approver_role_id AS approverRoleId, applicant_id AS applicantId, approver_id AS approverId, " +
            "action, opinion, created_at AS createdAt FROM approval_record " +
            "WHERE doc_type = #{docType} AND doc_id = #{docId} ORDER BY id")
    List<ApprovalRecord> listByDoc(@Param("docType") String docType, @Param("docId") Long docId);
}
