package com.example.erp.controller;

import com.example.erp.entity.PurchaseInbound;
import com.example.erp.service.InboundService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inbound")
@CrossOrigin // 允许跨域调用，解决前端 8080/本地静态文件访问问题
public class InboundController {

    private final InboundService inboundService;

    // 推荐使用构造器注入替代 @Autowired 字段注入
    public InboundController(InboundService inboundService) {
        this.inboundService = inboundService;
    }

    @PostMapping("/create")
    public ResponseEntity<String> createInbound(@RequestParam String sku,
                                                @RequestParam Integer qty,
                                                @RequestParam(required = false) String supplierName) {
        try {
            inboundService.executeInbound(sku, qty, supplierName);
            return ResponseEntity.ok("采购入库成功！");
        } catch (Exception e) {
            // 返回 400 BAD_REQUEST 状态码，促发前端 axios 的 .catch 回调渲染错误消息
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<PurchaseInbound>> listInbound() {
        return ResponseEntity.ok(inboundService.listInbound());
    }
}