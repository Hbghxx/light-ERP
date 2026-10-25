# 监控与告警(阶段4)

> 架构:Spring Boot Actuator(`/actuator/prometheus`)→ Prometheus(容器内网抓取)→ Grafana 预置仪表盘;
> 外层 Nginx 对公网封禁 `/actuator/*`(403);主机侧 cron 巡检脚本兜底存活告警。

## 1. 组件与访问入口

| 组件 | 启动方式 | 访问 | 说明 |
|---|---|---|---|
| 指标暴露 | 随后端 | `backend:8080/actuator/prometheus`(仅容器内网) | JVM/HTTP/HikariCP 指标 |
| Prometheus | `docker compose --profile monitoring up -d` | http://localhost:9090 | 抓取间隔 15s,目标 `backend:8080` |
| Grafana | 同上(profile) | http://localhost:3000(admin / 密码见 `.env` 的 `GRAFANA_ADMIN_PASSWORD`,默认 admin) | 预置数据源与"ERP 核心监控"仪表盘 |
| 存活巡检 | cron 每 5 分钟 | 日志 `logs/uptime.log` | 四项检查:nginx/后端/mysql/Prometheus 抓取 |

启动监控栈:`docker compose --profile monitoring up -d`(核心三容器不受影响;监控为独立 profile,可随时停)。

## 2. 仪表盘内容(已预置 `ERP 核心监控`)

1. 接口 QPS(按状态码/URI):`sum by (status, uri) (rate(http_server_requests_seconds_count[1m]))`
2. 5xx 错误速率:`sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))`
3. JVM 堆内存:`sum(jvm_memory_used_bytes{area="heap"})`
4. HikariCP 活跃/等待连接:`hikaricp_connections_active / pending`

## 3. 告警出口(当前形态与扩展点)

- **对账告警**:`scripts/reconcile.sh` 差异即退出码 1 + webhook 通知——业务正确性告警,见 `recovery-drill.md`;
- **存活告警**:`scripts/uptime-check.sh` 任一组件异常退出码 1 + webhook 通知;
- **webhook 通知**:在 `.env` 配置 `ALERT_WEBHOOK`(钉钉/企微/飞书自定义机器人地址,消息为 text 通用格式),
  三个脚本经 `scripts/alert-lib.sh` 统一发送;未配置时仅写日志;
- **Prometheus 告警规则**(已加载 `monitoring/alert.rules.yml`,规则状态可在 Prometheus UI 的 /alerts 查看;
  无 Alertmanager 时仅展示不外发,路由到邮件/webhook 需再加 Alertmanager):
  后端不可达(up=0 超 1 分钟)、5xx 速率 >1/s 持续 2 分钟、HikariCP pending 连接持续 2 分钟。

## 4. crontab 汇总(生产基线)

```cron
10 2 * * * /opt/erp/scripts/backup.sh      >> /opt/erp/logs/backup.log    2>&1
20 2 * * * /opt/erp/scripts/reconcile.sh   >> /opt/erp/logs/reconcile.log 2>&1
*/5 * * * * /opt/erp/scripts/uptime-check.sh >> /opt/erp/logs/uptime.log  2>&1
```

## 5. 踩坑记录

1. **只加 `micrometer-registry-prometheus` 不够**:actuator 端点本体来自 `spring-boot-starter-actuator`,
   漏了它 `/actuator/*` 全部 500(`NoResourceFoundException`),Prometheus `up=0`;
2. **tar 替换 bind-mount 的单文件后必须重启容器**:tar 解出的是新 inode,文件挂载仍指向旧 inode,
   nginx 改完配置不重启不生效;
3. **PromQL 里 `{}`/`"` 进 curl 必须 URL 编码**:用 `curl -G --data-urlencode`,直接拼 URL 会报
   `parse error`;巡检脚本对 `up` 值的断言要兼容时间戳小数(`[0-9.]*` 而非 `[0-9]*`)。
