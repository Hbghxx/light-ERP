package com.example.erp.service;

import com.example.erp.entity.SalesOutbound;
import com.example.erp.mapper.SalesOutboundMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutboundService {

    @Autowired
    private SalesOutboundMapper salesOutboundMapper;

    // 假设你有库存扣减 Mapper
    // @Autowired
    // private InventoryStockMapper inventoryStockMapper;

    @Transactional
    public String createOutbound(String sku, Integer qty, String customerName) {
        // 1. 扣减库存逻辑 (根据你现有的库存逻辑)
        // ...

        // 2. 【关键】生成并保存出库流水记录
        SalesOutbound outbound = new SalesOutbound();
        outbound.setOutboundNo("OUT-" + System.currentTimeMillis()); // 生成唯一出库单号
        outbound.setProductSku(sku);
        outbound.setQuantity(qty);
        outbound.setCustomerName(customerName);

        salesOutboundMapper.insert(outbound); // 确保调用了 insert

        return "出库成功";
    }

    public java.util.List<SalesOutbound> listOutbound() {
        return salesOutboundMapper.selectAll();
    }
}