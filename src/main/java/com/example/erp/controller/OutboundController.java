package com.example.erp.controller;

import com.example.erp.entity.SalesOutbound;
import com.example.erp.service.OutboundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/outbound")
@CrossOrigin // 确保跨域响应头正常开启
public class OutboundController {

    @Autowired
    private OutboundService outboundService;

    @PostMapping("/create")
    public String createOutbound(@RequestParam String sku,
                                 @RequestParam Integer qty,
                                 @RequestParam(required = false) String customerName) {
        return outboundService.createOutbound(sku, qty, customerName);
    }

    @GetMapping("/list")
    public List<SalesOutbound> listOutbound() {
        return outboundService.listOutbound();
    }
}