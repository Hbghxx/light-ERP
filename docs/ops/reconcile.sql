-- =====================================================================
-- 库存对账：inventory_stock 实存 vs 单据流水推算
-- 口径：库存应有量 = Σ采购入库 - Σ销售出库（阶段0修复后，库存仅由单据驱动）
-- 注意：/stock/deduct 直扣演示接口不产生流水，其使用会产生对账差异，需人工确认；
--       历史上(阶段0修复前)出库不扣库存也会在此暴露——这正是本对账的设计初衷。
-- 输出：diff = 实存 - 应有；diff≠0 的行即差异行，由 reconcile.sh 据此告警。
-- =====================================================================
SET NAMES utf8mb4;

SELECT
    COALESCE(st.sku, fl.sku)                                          AS sku,
    COALESCE(st.qty, 0)                                               AS actual_qty,
    COALESCE(fl.in_qty, 0) - COALESCE(fl.out_qty, 0)                  AS expected_qty,
    COALESCE(st.qty, 0) - COALESCE(fl.in_qty, 0) + COALESCE(fl.out_qty, 0) AS diff
FROM (SELECT product_sku AS sku, stock_qty AS qty FROM inventory_stock) st
LEFT JOIN (
    SELECT p.product_sku AS sku,
           SUM(p.quantity) AS in_qty,
           (SELECT COALESCE(SUM(o.quantity), 0) FROM sales_outbound o
             WHERE o.product_sku = p.product_sku) AS out_qty
    FROM purchase_inbound p
    GROUP BY p.product_sku
) fl ON fl.sku = st.sku
UNION ALL
SELECT
    f.sku,
    0 AS actual_qty,
    f.in_qty - f.out_qty AS expected_qty,
    -(f.in_qty - f.out_qty) AS diff
FROM (
    SELECT p.product_sku AS sku,
           SUM(p.quantity) AS in_qty,
           (SELECT COALESCE(SUM(o.quantity), 0) FROM sales_outbound o
             WHERE o.product_sku = p.product_sku) AS out_qty
    FROM purchase_inbound p
    WHERE p.product_sku NOT IN (SELECT product_sku FROM inventory_stock)
    GROUP BY p.product_sku
) f
ORDER BY ABS(diff) DESC;
