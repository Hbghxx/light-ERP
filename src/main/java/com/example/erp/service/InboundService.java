package com.example.erp.service;

import com.example.erp.common.AuditLog;
import com.example.erp.common.BusinessException;
import com.example.erp.common.ForbiddenException;
import com.example.erp.common.UserContext;
import com.example.erp.entity.ProductInfo;
import com.example.erp.entity.PurchaseInbound;
import com.example.erp.mapper.InventoryStockMapper;
import com.example.erp.mapper.ProductInfoMapper;
import com.example.erp.mapper.PurchaseInboundMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 采购入库(阶段6 审批流语义):
 * 提交只写单据(待审批)与审批记录;金额命中自动通过规则时同一事务内直接入账;
 * 人工审批通过时 CAS 迁移状态并加库存;驳回/作废不产生库存变动。
 */
@Service
public class InboundService {

    public static final int ST_PENDING = 1;
    public static final int ST_APPROVED = 2;
    public static final int ST_REJECTED = 3;
    public static final int ST_CANCELLED = 4;

    @Autowired
    private PurchaseInboundMapper purchaseInboundMapper;

    @Autowired
    private InventoryStockMapper inventoryStockMapper;

    @Autowired
    private ProductInfoMapper productInfoMapper;

    @Autowired
    private ApprovalService approvalService;

    /**
     * 提交采购入库单：写单据(待审批) + 审批路由；自动通过则同事务入账。
     */
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(action = "SUBMIT_INBOUND", docType = "INBOUND")
    public Map<String, Object> submitInbound(String sku, Integer qty, String supplierName) {
        // 1. 前置参数校验
        if (sku == null || sku.trim().isEmpty()) {
            throw new IllegalArgumentException("商品 SKU 不能为空");
        }
        if (qty == null || qty <= 0) {
            throw new IllegalArgumentException("入库数量必须大于 0");
        }
        if (supplierName != null && supplierName.length() > 100) {
            throw new IllegalArgumentException("供应商名称不能超过 100 个字符");
        }

        // 2. 商品与库存记录校验（新增商品时会同步初始化库存记录）
        ProductInfo product = productInfoMapper.selectBySku(sku);
        if (product == null) {
            throw new BusinessException("商品 SKU [" + sku + "] 不存在，请先在商品管理中新增");
        }
        if (inventoryStockMapper.selectBySku(sku) == null) {
            throw new BusinessException("商品 SKU [" + sku + "] 缺少库存记录，请重新保存商品信息");
        }

        // 3. 写单据（待审批），单价/金额/下单人/部门均为下单时快照
        UserContext ctx = UserContext.get();
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setInboundNo("IN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        inbound.setProductSku(sku);
        inbound.setQuantity(qty);
        inbound.setUnitPrice(product.getPrice());
        inbound.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(qty)));
        inbound.setSupplierName(supplierName);
        inbound.setStatus(ST_PENDING);
        inbound.setCreatedBy(ctx.getUserId());
        inbound.setDeptId(ctx.getDeptId());
        purchaseInboundMapper.insert(inbound);

        // 4. 审批路由：命中自动通过规则则同事务直接入账
        boolean auto = approvalService.onSubmitted("INBOUND", inbound.getId(),
                inbound.getTotalAmount(), ctx.getUserId());
        if (auto) {
            stockInOnAuto(inbound);
        }
        return Map.of("id", inbound.getId(), "autoPassed", auto,
                "amount", inbound.getTotalAmount());
    }

    /**
     * 审批入库单：通过则 CAS 迁移状态并加库存(同一事务)；驳回仅改状态。
     * 职责分离(SoD)：制单人与审批人不能为同一人。
     */
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(action = "AUDIT_INBOUND", docType = "INBOUND")
    public void auditInbound(Long docId, boolean approved, String opinion) {
        UserContext ctx = UserContext.get();
        PurchaseInbound doc = purchaseInboundMapper.findById(docId);
        if (doc == null) {
            throw new BusinessException("入库单不存在");
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
        int rows = purchaseInboundMapper.updateStatusCas(docId, ST_PENDING, approved ? ST_APPROVED : ST_REJECTED);
        if (rows == 0) {
            throw new BusinessException("单据状态已变化，请刷新后重试");
        }
        if (approved && inventoryStockMapper.increaseStock(doc.getProductSku(), doc.getQuantity()) == 0) {
            throw new BusinessException("库存更新失败，已回滚");
        }
        approvalService.recordAction("INBOUND", docId, doc.getCreatedBy(), ctx.getUserId(), approved, opinion);
    }

    /**
     * 作废待审批单据：仅制单人本人或管理员；已入账/已驳回单据不可作废。
     */
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(action = "CANCEL_INBOUND", docType = "INBOUND")
    public void cancelInbound(Long docId) {
        UserContext ctx = UserContext.get();
        PurchaseInbound doc = purchaseInboundMapper.findById(docId);
        if (doc == null) {
            throw new BusinessException("入库单不存在");
        }
        boolean owner = doc.getCreatedBy() != null && doc.getCreatedBy().equals(ctx.getUserId());
        if (!owner && !ctx.isAdmin()) {
            throw new ForbiddenException("仅制单人或管理员可作废该单据");
        }
        int rows = purchaseInboundMapper.updateStatusCas(docId, ST_PENDING, ST_CANCELLED);
        if (rows == 0) {
            throw new BusinessException("仅待审批状态的单据可作废");
        }
        approvalService.recordCancel("INBOUND", docId, doc.getCreatedBy(), ctx.getUserId());
    }

    /** 自动通过与人工审批共用的入账动作（提交事务内/审批事务内） */
    private void stockInOnAuto(PurchaseInbound doc) {
        int rows = purchaseInboundMapper.updateStatusCas(doc.getId(), ST_PENDING, ST_APPROVED);
        if (rows == 0) {
            throw new BusinessException("单据状态已变化，自动入账失败");
        }
        if (inventoryStockMapper.increaseStock(doc.getProductSku(), doc.getQuantity()) == 0) {
            throw new BusinessException("库存更新失败，已回滚");
        }
    }

    /**
     * 查询采购入库记录（按当前用户数据权限过滤：本人/本部门及以下/全部）
     */
    public List<PurchaseInbound> listInbound() {
        UserContext ctx = UserContext.get();
        return purchaseInboundMapper.selectAll(ctx.getDataScope(), ctx.getUserId(), ctx.getDeptPath());
    }

    /** 待审批单据（审批中心） */
    public List<PurchaseInbound> listPending() {
        UserContext ctx = UserContext.get();
        return purchaseInboundMapper.listPending(ctx.getDataScope(), ctx.getUserId(), ctx.getDeptPath());
    }
}
