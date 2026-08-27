package com.example.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SalesOutbound {
    private Long id;
    private String outboundNo;
    private String productSku;
    private Integer quantity;
    private String customerName;
    private Integer status;
    private LocalDateTime createdAt;

}