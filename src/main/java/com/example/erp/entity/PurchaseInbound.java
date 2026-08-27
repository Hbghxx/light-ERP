package com.example.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PurchaseInbound {
    private Long id;
    private String inboundNo;
    private String productSku;
    private Integer quantity;
    private String supplierName;
    private Integer status;
    private LocalDateTime createdAt;

}