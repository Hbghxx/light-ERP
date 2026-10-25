package com.example.erp.mapper;

import com.example.erp.entity.SysAuditLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysAuditLogMapper {

    @Insert("INSERT INTO sys_audit_log (user_id, action, doc_type, doc_id, doc_no, detail, ip) " +
            "VALUES (#{userId}, #{action}, #{docType}, #{docId}, #{docNo}, #{detail}, #{ip})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysAuditLog log);

    @Select("SELECT a.id, a.user_id AS userId, u.username, a.action, a.doc_type AS docType, a.doc_id AS docId, " +
            "a.doc_no AS docNo, CAST(a.detail AS CHAR) AS detail, a.ip, a.created_at AS createdAt " +
            "FROM sys_audit_log a LEFT JOIN sys_user u ON u.id = a.user_id " +
            "ORDER BY a.id DESC LIMIT #{limit}")
    List<SysAuditLog> listRecent(@Param("limit") int limit);
}
