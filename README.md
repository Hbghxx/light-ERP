# 轻量级 ERP 管理系统

基于 **Spring Boot 4 + MyBatis + MySQL + Vue 3** 的进销存系统(采购入库 / 销售出库 / 库存),
从业务功能到运维保障完整闭环:JWT 登录与 RBAC 权限、数据权限、库存对账告警、备份恢复演练、Prometheus 监控。

> 目标是做一个"不仅能跑,而且敢上线"的小型业务系统——单据驱动库存、同事务防超卖、
> 账实可对账、变更留痕、故障有案例库。开发路线与决策记录见 [PLAN.md](PLAN.md)。

## 功能特性

**业务**

- 商品主数据管理(新增即初始化库存,改价不影响历史单据金额——单价/总价下单快照)
- 采购入库 / 销售出库:**单据驱动 + 金额分档审批流**——提交生成待审批单据(不直接动库存),
  按金额路由:低于审批线系统自动通过,大额按规则到采购经理/超管;
  审批通过时**同一事务内"原子条件增减库存 + 状态迁移(CAS)"**,防超卖、防并发重复审批
- 单据全生命周期:待审批 / 已通过 / 已驳回 / 已作废;制单人可作废自己的待审批单据
- 库存乐观锁直扣演示接口(版本号 + 条件更新 + 重试)
- 报表:近 30 天出入库日报、库存价值榜

**权限与安全**

- JWT 登录(2h),连错 5 次自动锁定,首登强制改密,停用/锁定即时生效
- RBAC 操作权限:`@RequiresPermission` 注解 + 权限码(`资源:动作`),ADMIN 短路
- 数据权限三档:全部 / 本部门及以下(部门物化路径)/ 仅本人;多角色取最大范围
- 账号管理:创建 / 停用 / 解锁 / 重置密码 / 分配角色,**全部动作写审计日志**(含真实客户端 IP)
- 职责分离(SoD):制单人与审批人不能为同一人,内置于审批接口

**运维**

- 统一响应体 + 全局异常处理(业务错 400 带可读信息,异常不泄内部细节)
- 定时备份(空文件防护 + 7 天保留)与**恢复演练记录**(RTO≈4s)
- 库存**对账脚本**:实存 vs 流水推算,差异即告警(附故障案例库)
- Prometheus + Grafana 监控栈(独立 profile),存活巡检脚本
- 告警出口:对账差异/巡检异常/备份失败 webhook 通知(钉钉/企微/飞书通用格式)+ Prometheus 告警规则

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 17 · Spring Boot 4.0.8 · MyBatis(注解 SQL)· spring-security-crypto(BCrypt)· jjwt · Actuator + Micrometer |
| 数据 | MySQL 8.0(11 表:业务 4 + 权限/组织 6 + 审计 1,DDL 见 [docs/schema](docs/schema)) |
| 前端 | Vue 3 + Element Plus(单文件,免构建;登录页独立) |
| 部署 | Docker Compose(Nginx + 后端 + MySQL,多阶段构建)|
| 监控 | Prometheus + Grafana(profile 隔离)+ cron 巡检脚本 |

## 系统架构

```
浏览器 ──► Nginx :80 ──────────────────────────────► 静态页(index/login.html)
             │  封禁 /actuator、反代 /{product,inbound,
             │  outbound,stock,auth,user,role,dept,audit}/
             ▼
        Spring Boot :8080
        AuthInterceptor(JWT 校验 → UserContext → @RequiresPermission)
             │ Service(事务: 出库=扣库存+写流水;数据权限过滤)
             ▼
        MyBatis ──► MySQL 8.0(11 表)
             │ Actuator/Prometheus
             ▼
cron: backup.sh(每日备份) / reconcile.sh(每日对账,差异告警) / uptime-check.sh(每5分钟巡检)
Prometheus :9090 ──► Grafana :3000(--profile monitoring 启动)
```

## 快速开始

```bash
git clone <仓库地址> erp && cd erp
docker compose up -d --build          # 首次自动建表 + 灌入种子数据
docker compose --profile monitoring up -d   # 可选: 启动监控栈
```

- 访问 `http://<主机IP>/`,首次启动自动初始化 `admin` 账号,
  **初始密码在后端日志**:`docker logs erp-backend | grep 初始密码`
- Grafana:http://localhost:3000(账号见 `.env`,参考 [.env.example](.env.example))

完整步骤、运维命令、故障排查:[docs/deploy.md](docs/deploy.md)(按文档 30 分钟可复现)。

## 本地开发

```bash
# 1. MySQL 8.0 中执行(客户端加 --default-character-set=utf8mb4)
mysql < docs/schema/schema.sql
mysql -D erp < docs/schema/init-data.sql
# 2. 数据源默认 localhost:3306/erp,可用环境变量 SPRING_DATASOURCE_* 覆盖
./mvnw spring-boot:run
# 3. 浏览器直接打开 index.html(file:// 模式自动直连 localhost:8080)
```

## 文档索引

| 文档 | 内容 |
|---|---|
| [docs/deploy.md](docs/deploy.md) | 部署手册(一键脚本 + 分步命令 + 排查速查) |
| [docs/ops/recovery-drill.md](docs/ops/recovery-drill.md) | 备份恢复演练记录(RTO 实测) |
| [docs/ops/monitoring.md](docs/ops/monitoring.md) | 监控告警架构与踩坑记录 |
| [docs/ops/incident-001-stock-divergence.md](docs/ops/incident-001-stock-divergence.md) | 故障案例:库存账实脱节 |
| [docs/schema](docs/schema) | 全量 DDL / 种子数据 / 存量库增量升级 |
| [PLAN.md](PLAN.md) | 改造路线图与决策记录 |

## API 概览

统一响应:`{"code":0,"message":"操作成功","data":{}}`;除 `/auth/login` 外均需 `Authorization: Bearer <token>`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/login` | 登录(连错 5 次锁定) |
| GET | `/auth/me` | 当前用户信息/权限/数据范围 |
| GET | `/product/list` · POST `/product/add` · `/product/updatePrice` | 商品管理 |
| POST | `/inbound/create` · GET `/inbound/list` | 提交采购入库单(审批路由,数据权限过滤) |
| POST | `/outbound/create` · GET `/outbound/list` | 提交销售出库单(审批路由,防超卖) |
| POST | `/inbound/audit` · `/outbound/audit` | 审批单据(SoD:制单人≠审批人,通过才动库存) |
| POST | `/inbound/cancel` · `/outbound/cancel` | 作废待审批单据(制单人/管理员) |
| GET | `/approval/pending` | 审批中心待办(需审批权限) |
| GET | `/report/daily` · `/report/stock-value` | 报表(需 `report:view`) |
| POST | `/stock/deduct` | 乐观锁直扣演示接口 |
| GET/POST | `/user/*`、`/role/list`、`/dept/list` | 账号与组织管理(需相应权限,写审计) |
| GET | `/audit/list` | 审计日志(需 `audit:view`) |

## 路线图

- [x] 业务闭环与地基修复 · 部署 · 备份对账 · 监控 · RBAC/数据权限
- [x] 审批流:单据状态机(待审批/通过/驳回/作废,CAS 防并发)+ 金额分档自动路由 + 制单/审批职责分离(SoD)
- [x] 报表(出入库日报/库存价值榜) · webhook 告警 · Prometheus 告警规则
- [ ] Alertmanager 路由与通知渠道 · 简历定稿(见 PLAN.md 阶段7)
