package com.example.erp.service;

import com.example.erp.entity.InventoryStock;
import com.example.erp.mapper.InventoryStockMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StockService {

    @Autowired
    private InventoryStockMapper inventoryStockMapper;

    /**
     * 乐观锁扣减库存（不加 @Transactional，保证每次重试查询最新数据库快照）
     */
    public boolean deductStock(String sku, Integer qty) {
        // 非正数会让条件更新放大库存（stock_qty >= 负数恒成立），必须拦截
        if (sku == null || sku.trim().isEmpty()) {
            throw new IllegalArgumentException("商品 SKU 不能为空");
        }
        if (qty == null || qty <= 0) {
            throw new IllegalArgumentException("扣减数量必须大于 0");
        }

        int maxRetries = 3;

        for (int i = 0; i < maxRetries; i++) {
            // 1. 重新查询数据库最新版本号
            InventoryStock stock = inventoryStockMapper.selectBySku(sku);
            if (stock == null || stock.getStockQty() < qty) {
                return false;
            }

            // 2. 带当前最新 version 尝试更新
            int rows = inventoryStockMapper.reduceStockWithLock(sku, qty, stock.getVersion());
            if (rows > 0) {
                return true;
            }
        }

        return false;
    }
}