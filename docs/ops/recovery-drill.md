# 恢复演练记录 #001

| 项 | 内容 |
|---|---|
| 演练日期 | 2026-10-04 |
| 环境 | WSL2 Ubuntu 22.04,Docker Compose 三容器(MySQL 8.0 数据卷持久化) |
| 演练人 | 项目负责人(在 AI 协助下执行) |
| 演练范围 | 全库逻辑备份 → 模拟误操作破坏 → 对账告警 → 恢复 → 复核 |
| 结论 | **通过**。RTO(恢复耗时)≈ 4 秒(纯灌库)/ 约 40 秒(含后端就绪);RPO = 0(灾难前即时备份) |

## 1. 演练目的

验证《部署手册》中的备份/恢复流程真实可用,验证对账脚本能在数据被破坏后发出告警,
并取得真实的恢复耗时数据,避免"备份从未恢复过"的经典运维风险。

## 2. 演练前基线

```
[reconcile] DEMO-001  actual=106  expected=106  diff=0   → 账实相符
商品: DEMO-001 演示商品(唯一);入库流水 3 行;库存 106
```

## 3. 备份(恢复源)

```bash
$ ./scripts/backup.sh
[backup] OK: backups/erp_20261004_022348.sql.gz (3868 bytes)
```
要点:备份带**空文件防护**(小于 1KB 判失败)与 7 天保留策略;`--single-transaction` 保证
InnoDB 一致性快照,不停机。

## 4. 模拟误操作(灾难注入)

模拟"运维手滑连了生产库"的经典场景:

```sql
UPDATE inventory_stock SET stock_qty = 7;   -- 改坏库存
DELETE FROM purchase_inbound;               -- 清空流水
DELETE FROM sales_outbound;
DELETE FROM product_info;                   -- 删除商品
```

破坏后:商品列表接口返回空;**对账脚本立即告警**(这是告警能力的实证):

```
[reconcile] DEMO-001  actual=7  expected=0  diff=7
[reconcile] ALERT: 发现 1 个 SKU 账实不符，请立即核查！    # 退出码 1,可接入 cron 告警
```

## 5. 恢复过程

```bash
$ docker compose stop backend                 # 停写,防止恢复期间脏写
$ gunzip -c backups/erp_20261004_022348.sql.gz \
    | docker exec -i erp-mysql sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" erp'
$ docker compose start backend
```

**纯灌库耗时 4 秒**(3868 字节压缩备份,含全部 11 张表结构、种子数据与业务数据);
后端就绪约 30 秒(受 Spring 启动时间影响,与恢复本身无关)。

## 6. 恢复后复核

```
商品接口: DEMO-001 演示商品,库存 106        ← 与演练前完全一致
[reconcile] DEMO-001  actual=106  expected=106  diff=0   → 账实相符
```

## 7. 结论与改进项

1. 备份/恢复/对账/告警四个环节全部真实走通,**演练通过**;
2. ~~恢复脚本应固化~~ → **已固化**(2026-10-05):`scripts/restore.sh <backup.gz>`——恢复前自动兜底备份
   当前状态(保留事故现场),恢复后自动执行库存对账;
3. 告警出口已扩展 webhook 通知(`.env` 配置 `ALERT_WEBHOOK`,钉钉/企微/飞书通用格式,见 monitoring.md);邮件通道为后续扩展项;
4. `/stock/deduct` 直扣接口绕过流水,会产生对账差异,使用后需人工确认——生产应禁用该演示接口。

## 8. 定时任务(生产基线)

```cron
# 每日 02:10 全量备份(保留7天)
10 2 * * * /opt/erp/scripts/backup.sh >> /opt/erp/logs/backup.log 2>&1
# 每日 02:20 库存对账(差异即退出码1)
20 2 * * * /opt/erp/scripts/reconcile.sh >> /opt/erp/logs/reconcile.log 2>&1
```
