package com.example.erp.service;

import com.example.erp.entity.InventoryStock;
import com.example.erp.entity.PurchaseInbound;
import com.example.erp.mapper.InventoryStockMapper;
import com.example.erp.mapper.PurchaseInboundMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class InboundService {

    @Autowired
    private PurchaseInboundMapper purchaseInboundMapper;

    @Autowired
    private InventoryStockMapper inventoryStockMapper;

    /**
     * 执行采购入库
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeInbound(String sku, Integer qty, String supplierName) {
        // 1. 前置参数校验
        if (sku == null || sku.trim().isEmpty()) {
            throw new IllegalArgumentException("商品 SKU 不能为空");
        }
        if (qty == null || qty <= 0) {
            throw new IllegalArgumentException("入库数量必须大于 0");
        }

        // 2. 保存采购入库流水
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setInboundNo("IN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        inbound.setProductSku(sku);
        inbound.setQuantity(qty);
        inbound.setSupplierName(supplierName);
        purchaseInboundMapper.insert(inbound);

        // 3. 更新库存表
        int rows = inventoryStockMapper.increaseStock(sku, qty);

        // 4. 若更新影响行数为 0，说明该 SKU 尚无库存记录，需自动初始化（兜底逻辑）
        if (rows == 0) {
            InventoryStock stock = inventoryStockMapper.selectBySku(sku);
            if (stock == null) {
                // 如果需要支持自动建仓，可以在 InventoryStockMapper 中补全 insert 方法，在此处初始化库存记录
                throw new RuntimeException("商品 SKU: " + sku + " 在库存表中未注册，请先初始化商品库存信息");
            }
        }
    }

    /**
     * 查询所有采购入库记录
     */
    public List<PurchaseInbound> listInbound() {
        return purchaseInboundMapper.selectAll();
    }
}