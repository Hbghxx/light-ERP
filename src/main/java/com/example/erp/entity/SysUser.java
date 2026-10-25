package com.example.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SysUser {
    private Long id;
    private String username;
    private String password;
    private String realName;
    private Long deptId;
    private Integer status;          // 1正常 0停用 2锁定
    private Integer errorCount;
    private Integer needChangePwd;   // 1首次登录需改密
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    // 关联展示字段（列表查询填充，非表列）
    private String deptName;
    private String roleNames;
}
