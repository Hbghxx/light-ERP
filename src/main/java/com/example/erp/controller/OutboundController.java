package com.example.erp.controller;

import com.example.erp.common.RequiresPermission;
import com.example.erp.common.Result;
import com.example.erp.entity.SalesOutbound;
import com.example.erp.service.OutboundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/outbound")
@CrossOrigin // 确保跨域响应头正常开启
public class OutboundController {

    @Autowired
    private OutboundService outboundService;

    /**
     * 提交销售出库单（阶段6: 待审批,金额<审批线自动扣账）
     */
    @PostMapping("/create")
    @RequiresPermission("outbound:create")
    public Result<String> createOutbound(@RequestParam String sku,
                                         @RequestParam Integer qty,
                                         @RequestParam(required = false) String customerName) {
        Map<String, Object> r = outboundService.submitOutbound(sku, qty, customerName);
        boolean auto = Boolean.TRUE.equals(r.get("autoPassed"));
        return Result.ok(auto ? "出库单已自动通过并扣账" : "出库单已提交，等待审批");
    }

    @GetMapping("/list")
    public Result<List<SalesOutbound>> listOutbound() {
        return Result.ok(outboundService.listOutbound());
    }

    /** 审批（通过=扣库存/驳回）：需出库审批权限，制单人不能审批自己的单据 */
    @PostMapping("/audit")
    @RequiresPermission("outbound:audit")
    public Result<String> audit(@RequestParam Long docId,
                                @RequestParam boolean approved,
                                @RequestParam(required = false) String opinion) {
        outboundService.auditOutbound(docId, approved, opinion);
        return Result.ok(approved ? "审批通过，已出账" : "已驳回");
    }

    /** 作废待审批单据（制单人本人或管理员） */
    @PostMapping("/cancel")
    public Result<String> cancel(@RequestParam Long docId) {
        outboundService.cancelOutbound(docId);
        return Result.ok("单据已作废");
    }

    /** 待审批单据（审批中心用） */
    @GetMapping("/pending")
    @RequiresPermission("outbound:audit")
    public Result<List<SalesOutbound>> pending() {
        return Result.ok(outboundService.listPending());
    }
}
