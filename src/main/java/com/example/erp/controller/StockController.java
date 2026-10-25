package com.example.erp.controller;

import com.example.erp.common.BusinessException;
import com.example.erp.common.RequiresPermission;
import com.example.erp.common.Result;
import com.example.erp.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stock")
public class StockController {

    @Autowired
    private StockService stockService;

    /**
     * 乐观锁扣减演示接口（数量必须为正，由 Service 校验）
     */
    @PostMapping("/deduct")
    @RequiresPermission("stock:deduct")
    public Result<String> deduct(@RequestParam String sku, @RequestParam Integer qty) {
        if (!stockService.deductStock(sku, qty)) {
            throw new BusinessException("扣减失败：库存不足或并发冲突");
        }
        return Result.ok("扣减成功");
    }
}
