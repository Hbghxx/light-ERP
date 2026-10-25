package com.example.erp.controller;

import com.example.erp.common.RequiresPermission;
import com.example.erp.common.Result;
import com.example.erp.entity.PurchaseInbound;
import com.example.erp.service.InboundService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/inbound")
@CrossOrigin // 允许跨域调用，解决前端 8080/本地静态文件访问问题
public class InboundController {

    private final InboundService inboundService;

    // 推荐使用构造器注入替代 @Autowired 字段注入
    public InboundController(InboundService inboundService) {
        this.inboundService = inboundService;
    }

    /**
     * 提交采购入库单（阶段6: 待审批,金额<审批线自动入账）
     */
    @PostMapping("/create")
    @RequiresPermission("inbound:create")
    public Result<String> createInbound(@RequestParam String sku,
                                        @RequestParam Integer qty,
                                        @RequestParam(required = false) String supplierName) {
        Map<String, Object> r = inboundService.submitInbound(sku, qty, supplierName);
        boolean auto = Boolean.TRUE.equals(r.get("autoPassed"));
        return Result.ok(auto ? "入库单已自动通过并入账" : "入库单已提交，等待审批");
    }

    @GetMapping("/list")
    public Result<List<PurchaseInbound>> listInbound() {
        return Result.ok(inboundService.listInbound());
    }

    /** 审批（通过/驳回）：需入库审批权限，制单人不能审批自己的单据 */
    @PostMapping("/audit")
    @RequiresPermission("inbound:audit")
    public Result<String> audit(@RequestParam Long docId,
                                @RequestParam boolean approved,
                                @RequestParam(required = false) String opinion) {
        inboundService.auditInbound(docId, approved, opinion);
        return Result.ok(approved ? "审批通过，已入账" : "已驳回");
    }

    /** 作废待审批单据（制单人本人或管理员） */
    @PostMapping("/cancel")
    public Result<String> cancel(@RequestParam Long docId) {
        inboundService.cancelInbound(docId);
        return Result.ok("单据已作废");
    }

    /** 待审批单据（审批中心用） */
    @GetMapping("/pending")
    @RequiresPermission("inbound:audit")
    public Result<List<PurchaseInbound>> pending() {
        return Result.ok(inboundService.listPending());
    }
}
