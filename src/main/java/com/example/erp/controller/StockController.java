package com.example.erp.controller;

import com.example.erp.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stock")
public class StockController {

    @Autowired
    private StockService stockService;

    @PostMapping("/deduct")
    public String deduct(@RequestParam String sku, @RequestParam Integer qty) {
        boolean success = stockService.deductStock(sku, qty);
        if (success) {
            return "扣减成功！";
        } else {
            return "扣减失败：库存不足或并发版本冲突。";
        }
    }
}