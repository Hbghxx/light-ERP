package com.example.erp.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductInfo {
    private Long id;
    private String sku;
    private String name;
    private BigDecimal price;
    private LocalDateTime createdAt;

    // 新增：库存关联字段
    private Integer stockQty;
    private LocalDateTime stockUpdatedAt;
}