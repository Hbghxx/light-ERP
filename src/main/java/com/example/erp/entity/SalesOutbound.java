package com.example.erp.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SalesOutbound {
    private Long id;
    private String outboundNo;
    private String productSku;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String customerName;
    private Integer status;
    private Long createdBy;
    private Long deptId;
    private LocalDateTime createdAt;

    // 关联展示字段（非表列）
    private String applicantName;

}