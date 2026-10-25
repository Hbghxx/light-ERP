package com.example.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SysRole {
    private Long id;
    private String roleCode;
    private String roleName;
    private String dataScope;    // ALL / DEPT_AND_CHILD / SELF
    private Integer isBuiltin;
    private LocalDateTime createdAt;
}
