# 故障案例 #001:销售出库不扣库存,导致库存账实脱节

> 故障案例库定位:记录本项目真实发生过的问题,按"现象 → 定位 → 根因 → 修复 → 预防"沉淀。
> 本篇是项目史上最重要的案例——它既是一次真实缺陷,也是对账体系诞生的原因。

## 1. 现象(现象期)

系统运行一段时间后发现:商品只进不出——采购入库后库存上涨,销售出库后库存纹丝不动;
页面上能查到出库流水,但库存数字与"入库累计 - 出库累计"对不上。

## 2. 定位(若当时有对账)

本项目在缺陷存在期间**没有对账手段**,问题是通过代码评审发现的。若当时已有对账脚本,
每日巡检会直接产出如下差异(以 DEMO-001 为例,入库 108 件、出库 2 件、实存 106):

```
sku       actual_qty  expected_qty  diff
DEMO-001  106         106           0      ← 修复后
DEMO-001  108         106           2      ← 缺陷存在时会看到(出库未扣)
```

定位 SQL 见 `docs/ops/reconcile.sql`:库存表实存 vs 流水推算应有量,按 SKU 求差。

## 3. 根因

`OutboundService.createOutbound` 只写 `sales_outbound` 流水,**扣库存的代码被注释掉了**:

```java
// 1. 扣减库存逻辑 (根据你现有的库存逻辑)
// ...
// 2. 【关键】生成并保存出库流水记录
salesOutboundMapper.insert(outbound);
```

更深一层的根因是**设计缺陷而非手误**:出库与扣减是两个独立接口、两个独立事务
(`/outbound/create` 记流水,`/stock/deduct` 手动扣库存),没有任何机制保证"有单必有账"。

## 4. 修复

阶段 0 完成**出库事务化**:同一事务内"原子条件扣库存 + 写流水",任一失败整体回滚;

```java
@Transactional(rollbackFor = Exception.class)
public void createOutbound(String sku, Integer qty, String customerName) {
    // 参数与商品校验 ...
    int rows = inventoryStockMapper.reduceStockIfEnough(sku, qty);  // stock_qty >= qty 才更新
    if (rows == 0) throw new BusinessException("库存不足，出库失败");
    salesOutboundMapper.insert(outbound);                            // 同事务
}
```

顺带修复:出库不校验 SKU/数量、负数扣减反向加库存、乐观锁 version 语义不完整。

## 5. 预防(三层)

1. **业务层**:出入库均由单据驱动且同事务,不存在"只记账不动库存"的路径;
2. **对账层**:`scripts/reconcile.sh` + cron 每日核对,差异即告警(见 `docs/ops/recovery-drill.md` 演练实证);
3. **追溯层**:审计日志记录所有关键动作;**审批流已上线**(阶段6)——库存变动必须经单据审批入账,
   "绕过单据改库存"只剩 `/stock/deduct` 演示接口一个口子,生产禁用。

## 6. 复盘要点(TALKING POINTS)

- 单据与库存的一致性必须由**同一事务**保证,跨接口的"最终一致"在没有对账兜底时等于没有一致;
- **对账是数据完整性的最后防线**:它不依赖任何代码正确性假设,直接核对结果;
- 备份解决"丢了能回来",对账解决"错了能知道"——两者缺一不可。
