package com.example.erp.service;

import com.example.erp.common.AuditLog;
import com.example.erp.common.BusinessException;
import com.example.erp.common.ForbiddenException;
import com.example.erp.common.UserContext;
import com.example.erp.entity.ProductInfo;
import com.example.erp.entity.SalesOutbound;
import com.example.erp.mapper.InventoryStockMapper;
import com.example.erp.mapper.ProductInfoMapper;
import com.example.erp.mapper.SalesOutboundMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 销售出库(阶段6 审批流语义):
 * 提交只写单据(待审批)与审批记录,不扣库存;审批通过时同事务"条件扣库存+CAS 状态";
 * 库存不足以通过审批时报错,由审批人选择驳回。
 */
@Service
public class OutboundService {

    public static final int ST_PENDING = 1;
    public static final int ST_APPROVED = 2;
    public static final int ST_REJECTED = 3;
    public static final int ST_CANCELLED = 4;

    @Autowired
    private SalesOutboundMapper salesOutboundMapper;

    @Autowired
    private ProductInfoMapper productInfoMapper;

    @Autowired
    private InventoryStockMapper inventoryStockMapper;

    @Autowired
    private ApprovalService approvalService;

    /**
     * 提交销售出库单：写单据(待审批) + 审批路由；自动通过则同事务扣库存。
     */
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(action = "SUBMIT_OUTBOUND", docType = "OUTBOUND")
    public Map<String, Object> submitOutbound(String sku, Integer qty, String customerName) {
        // 1. 前置参数校验
        if (sku == null || sku.trim().isEmpty()) {
            throw new IllegalArgumentException("商品 SKU 不能为空");
        }
        if (qty == null || qty <= 0) {
            throw new IllegalArgumentException("出库数量必须大于 0");
        }
        if (customerName != null && customerName.length() > 100) {
            throw new IllegalArgumentException("客户名称不能超过 100 个字符");
        }
        ProductInfo product = productInfoMapper.selectBySku(sku);
        if (product == null) {
            throw new BusinessException("商品 SKU [" + sku + "] 不存在");
        }

        // 2. 写单据（待审批），单价/金额/下单人/部门均为下单时快照
        UserContext ctx = UserContext.get();
        SalesOutbound outbound = new SalesOutbound();
        outbound.setOutboundNo("OUT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        outbound.setProductSku(sku);
        outbound.setQuantity(qty);
        outbound.setUnitPrice(product.getPrice());
        outbound.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(qty)));
        outbound.setCustomerName(customerName);
        outbound.setStatus(ST_PENDING);
        outbound.setCreatedBy(ctx.getUserId());
        outbound.setDeptId(ctx.getDeptId());
        salesOutboundMapper.insert(outbound);

        // 3. 审批路由：命中自动通过规则则同事务直接扣库存
        boolean auto = approvalService.onSubmitted("OUTBOUND", outbound.getId(),
                outbound.getTotalAmount(), ctx.getUserId());
        if (auto) {
            stockOutOnAuto(outbound);
        }
        return Map.of("id", outbound.getId(), "autoPassed", auto,
                "amount", outbound.getTotalAmount());
    }

    /**
     * 审批出库单：通过则 CAS 迁移状态并条件扣库存(同一事务,天然防超卖)；驳回仅改状态。
     * 职责分离(SoD)：制单人与审批人不能为同一人。
     */
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(action = "AUDIT_OUTBOUND", docType = "OUTBOUND")
    public void auditOutbound(Long docId, boolean approved, String opinion) {
        UserContext ctx = UserContext.get();
        SalesOutbound doc = salesOutboundMapper.findById(docId);
        if (doc == null) {
            throw new BusinessException("出库单不存在");
        }
        if (doc.getStatus() != ST_PENDING) {
            throw new BusinessException("该单据不在待审批状态");
        }
        if (doc.getCreatedBy() != null && doc.getCreatedBy().equals(ctx.getUserId())) {
            throw new BusinessException("职责分离：制单人与审批人不能为同一人");
        }
        if (!approved && (opinion == null || opinion.isBlank())) {
            throw new BusinessException("驳回必须填写原因");
        }

        // CAS 迁移：并发下只有一次审批能成功
        int rows = salesOutboundMapper.updateStatusCas(docId, ST_PENDING, approved ? ST_APPROVED : ST_REJECTED);
        if (rows == 0) {
            throw new BusinessException("单据状态已变化，请刷新后重试");
        }
        if (approved && inventoryStockMapper.reduceStockIfEnough(doc.getProductSku(), doc.getQuantity()) == 0) {
            // 回滚整个审批事务,单据回到待审批,由审批人选择驳回或先补库存
            throw new BusinessException("库存不足，无法通过该出库单（可驳回并说明原因）");
        }
        approvalService.recordAction("OUTBOUND", docId, doc.getCreatedBy(), ctx.getUserId(), approved, opinion);
    }

    /**
     * 作废待审批单据：仅制单人本人或管理员。
     */
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(action = "CANCEL_OUTBOUND", docType = "OUTBOUND")
    public void cancelOutbound(Long docId) {
        UserContext ctx = UserContext.get();
        SalesOutbound doc = salesOutboundMapper.findById(docId);
        if (doc == null) {
            throw new BusinessException("出库单不存在");
        }
        boolean owner = doc.getCreatedBy() != null && doc.getCreatedBy().equals(ctx.getUserId());
        if (!owner && !ctx.isAdmin()) {
            throw new ForbiddenException("仅制单人或管理员可作废该单据");
        }
        int rows = salesOutboundMapper.updateStatusCas(docId, ST_PENDING, ST_CANCELLED);
        if (rows == 0) {
            throw new BusinessException("仅待审批状态的单据可作废");
        }
        approvalService.recordCancel("OUTBOUND", docId, doc.getCreatedBy(), ctx.getUserId());
    }

    /** 自动通过与人工审批共用的扣库存动作（提交事务内/审批事务内） */
    private void stockOutOnAuto(SalesOutbound doc) {
        int rows = salesOutboundMapper.updateStatusCas(doc.getId(), ST_PENDING, ST_APPROVED);
        if (rows == 0) {
            throw new BusinessException("单据状态已变化，自动出账失败");
        }
        if (inventoryStockMapper.reduceStockIfEnough(doc.getProductSku(), doc.getQuantity()) == 0) {
            throw new BusinessException("库存不足，自动出账失败");
        }
    }

    public List<SalesOutbound> listOutbound() {
        UserContext ctx = UserContext.get();
        return salesOutboundMapper.selectAll(ctx.getDataScope(), ctx.getUserId(), ctx.getDeptPath());
    }

    /** 待审批单据（审批中心） */
    public List<SalesOutbound> listPending() {
        UserContext ctx = UserContext.get();
        return salesOutboundMapper.listPending(ctx.getDataScope(), ctx.getUserId(), ctx.getDeptPath());
    }
}
