# ERP 项目改造计划(求职导向)

> 目标岗位:ERP 应用运维(校招,SAP/SRM/WMS/PLM/OA 方向)。
> 项目定位:证明"懂 ERP 业务流程 + 会 SQL 和脚本 + 有运维意识和文档习惯",开发深度亮点作为面试追问时的储备。
> 总工作量约 4–5 周业余时间。**阶段 0–3 完成即具备投递条件**,5/6/7 在面试期并行。

---

## 明确不做

- Redis 对比实验、微服务/Spring Cloud、前端工程化(Vite 迁移)
- 多租户、报表图表、消息队列

## 里程碑检查点

1. **阶段 0 验收**:所有接口正反用例通过;并发扣减不超卖 —— 开发叙事的锚点
2. **阶段 3 验收**:能现场演示"改坏库存 → 对账报警 → SQL 定位",恢复演练留痕 —— 运维叙事的锚点
3. **阶段 3 完成 → 开始投递**,后续阶段持续升级

---

## 阶段 0:地基修复(约 2–3 天)

- [x] 统一响应体 `Result<T>`(code/message/data)+ 全局异常处理器 `@RestControllerAdvice`,错误返回正确 HTTP 状态码
- [x] 出库事务化:同一事务内"扣库存 + 写流水",任一失败整体回滚
- [x] 出库校验:SKU 存在、库存充足、qty > 0
- [x] `/stock/deduct` 拦截非正数(堵住"负数加库存"漏洞)
- [x] 入库校验:商品必须已登记、库存记录必须存在(替换"插流水后靠回滚兜底"的写法)
- [x] 商品校验:SKU/名称非空、售价非负
- [x] 出库单号改 UUID 方案,与入库一致,消除并发撞号
- [x] 修前端 bug:按统一响应判断成败(消灭"失败弹绿色成功提示"),补齐缺失的 `.catch`
- [x] README 与实现对齐(版本号、出库行为、响应格式)

## 阶段 1:数据模型补全(约 1–2 天,阶段 5/6 的前提)

- [x] `purchase_inbound` / `sales_outbound` 加列:`created_by`、`dept_id`、`unit_price`、`total_amount`(下单时快照,审批按金额依赖它)
- [x] 完整 DDL 脚本(含新列)入 git,补上仓库缺失的建表脚本
- 落地说明:三份脚本已入库(全量 schema.sql、种子 init-data.sql、存量升级 upgrade-001-biz-columns.sql);本地 erp 库已应用迁移并灌入种子数据;客户端执行需加 --default-character-set=utf8mb4

## 阶段 2:Linux 部署(约 3–5 天,运维叙事的载体)

- [x] 真实 Linux 环境部署验证通过(WSL2 Ubuntu 22.04 全流程:Docker 安装、镜像加速、一键部署、备份、乱码根治;云服务器同流程)
- [x] Nginx 托管 index.html + 按业务前缀反代 API,前端改相对路径(兼容 file:// 直开),根治 CORS
- [x] 重启策略(unless-stopped)+ 日志轮转(docker json-file 10MB×3)
- [x] 《部署手册》docs/deploy.md:按文档从零 30 分钟内可复现

## 阶段 3:备份恢复 + 对账(约 2–3 天,运维岗核心弹药)

- [x] mysqldump 每日定时备份脚本 + 保留 7 天策略
- [x] 做一次恢复演练,写《恢复演练记录》
- [x] 对账 SQL:库存表 vs 流水表汇总比对差异;定时对账任务 + 差异告警
- [x] 《故障案例库》第一篇:出库不减库存 → 对账发现 → SQL 定位 → 修复 → 监控预防

## 阶段 4:监控告警(约 2–3 天)

- [x] Actuator 健康检查 + Prometheus + Grafana(JVM、QPS、错误率),仪表盘截图进文档
- [x] 进程/接口存活告警(Alertmanager 或简单脚本)

## 阶段 5:RBAC + 组织 + 数据权限 + 账号权限分层(约 5–7 天,两岗通用)

**RBAC 骨架**

- [x] sys_user 表 + BCrypt 密码 + 登录接口 + JWT + 拦截器 + ThreadLocal 用户上下文
- [x] sys_role / sys_permission / sys_user_role / sys_role_permission 五表接入(建表与种子已落地 docs/schema/)
- [x] `@RequiresPermission` 注解鉴权(权限码:`资源:动作`)

**组织与数据权限**

- [x] sys_dept 树(parent_id + path 物化路径,查子树用 `path LIKE`)
- [x] `role.data_scope`(SELF / DEPT_AND_CHILD / ALL)+ DataScope 对象统一接入列表查询
- [x] 单据表 `dept_id`/`created_by` 下单快照(人转部门不影响历史归属)

**账号权限分层(企业级补充)**

- [x] 账号类型:内置 `ADMIN`(超管短路)/ 业务角色(采购员/采购经理/仓库员/财务)/ `AUDITOR`(审计账号:只读审计日志与单据)
- [x] sys_user.status:正常 / 停用 / 锁定;连续 5 次密码错误自动锁定;首登改密 + 密码复杂度(简版)
- [x] 账号管理页:创建、启用/停用、重置密码、分配角色
- [x] **权限变更留痕**:角色分配/账号停用等管理动作本身写审计日志(谁、何时、给谁、加了什么权限)

## 阶段 6:审批流 + 审计日志(约 4–5 天)✅ 2026-10-05 完成

- [x] 单据状态机:1 待审批 → 2 已通过(触发库存入账)→ 3 驳回;1 → 4 作废;CAS 更新(`UPDATE ... WHERE status = 期望值`)防并发重复审批
- [x] approval_rule 金额分档规则表 + 提交时快照生成 approval_record(规则改动不影响历史单据)
- [x] 审批通过同事务:状态 CAS + 加/扣库存(入库复用 increaseStock,出库复用条件更新)
- [x] **职责分离(SoD)**:制单人与审批人不能为同一人(对接 SAP GRC/SOD 概念)
- [x] `@AuditLog` 注解 + AOP 切面写审计日志(参数/返回值快照),埋点:单据提交/审批/作废
- [x] 前端:提交/审批/驳回/作废交互与状态展示

**验收**:"小额单自动过、大额单经理审"完整演示路径 —— 本地 23/23、WSL 线上 9/9 全部通过(含 SoD/CAS/越权场景)。

## 阶段 7:求职配套(与项目并行,不占开发时间)

- [ ] SAP 概念速成(2–3 天):MM/SD/PP/QM/PS 各管什么;物料主数据、采购订单、收货过账、交货单、移动类型;SRM/WMS/PLM/OA 定位(需本人学习,材料已备)
- [x] 概念映射表:已并入 INTERVIEW.md 第五节(入库单≈MM 收货过账、审批流≈Release Strategy、账号管理≈SU01、角色维护≈PFCG 等 11 项)
- [ ] 简历双版本:运维版(业务流程+备份恢复+监控+文档+SQL)/ 开发版(乐观锁+事务+RBAC+AOP)——素材已齐(见 INTERVIEW.md),成稿需本人定稿
- [x] STAR 故事:①账实脱节对账排查 ②乐观锁防超卖 ③恢复演练 ④WSL 502 排障(INTERVIEW.md 第四节)
- [ ] (可选)50 并发扣减压测数据,放开发版简历
- [x] 面试话术:三员分立(AUDITOR 角色为活例)、职责分离(审批流已实现)、权限变更可追溯(INTERVIEW.md)
- [x] 报表:近 30 天出入库日报 + 库存价值榜(`report:view` 权限,前端报表页)
- [x] 告警扩展:webhook 通知(钉钉/企微/飞书通用格式,ALERT_WEBHOOK 配置于 .env)+ Prometheus 告警规则文件(后端宕机/5xx 突增/连接池等待)

---

## 参考项目(读设计不抄代码)

| 用途 | 项目 |
|---|---|
| 进销存业务模型 | jshERP(gitee.com/jishenghua/jshERP,GPL-3,注意其权限实现有历史漏洞,勿照抄) |
| RBAC/数据权限标准实现 | 若依 RuoYi-Vue(sys_dept.ancestors、role.data_scope、@DataScope 切面) |
| 审批流参考 | ruoyi-vue-pro / JeecgBoot(Flowable 节点设计) |
| 功能设计教科书 | Dolibarr / ERPNext / Odoo(单据状态机、审批、审计轨迹) |
| 库存文档驱动理念 | Apache OFBiz(只看理念) |
