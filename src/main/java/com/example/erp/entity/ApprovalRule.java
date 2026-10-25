package com.example.erp.entity;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ApprovalRule {
    private Long id;
    private String scene;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Long approverRoleId;   // NULL = 自动通过
    private Integer enabled;
}
