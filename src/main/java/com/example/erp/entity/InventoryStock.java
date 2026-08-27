package com.example.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class InventoryStock {
    private Long id;
    private String productSku;
    private Integer stockQty;
    private Integer version;
    private LocalDateTime updatedAt;
}

