#!/usr/bin/env bash
# =====================================================================
# 存活巡检脚本（cron 用）：检查前端入口、后端接口、数据库容器
# crontab 示例（每 5 分钟）:
#   */5 * * * * /opt/erp/scripts/uptime-check.sh >> /opt/erp/logs/uptime.log 2>&1
# 异常时退出码 1 并输出 [ALERT]，可在此接入邮件/webhook 告警
# =====================================================================
set -uo pipefail
cd "$(dirname "$0")/.."

LOG_TAG="[uptime $(date '+%F %T')]"
source "$(dirname "$0")/alert-lib.sh"
fail=0

# 1. 前端入口（nginx）
code=$(curl -s -o /dev/null -m 5 -w '%{http_code}' http://localhost/login.html || echo 000)
if [ "$code" = 200 ]; then echo "$LOG_TAG OK nginx"; else echo "$LOG_TAG ALERT nginx 不可达 ($code)"; notify_alert "ERP巡检告警: nginx 不可达 ($code)"; fail=1; fi

# 2. 后端接口（预期 401=活着且鉴权正常）
code=$(curl -s -o /dev/null -m 5 -w '%{http_code}' http://localhost/product/list || echo 000)
if [ "$code" = 401 ]; then echo "$LOG_TAG OK backend"; else echo "$LOG_TAG ALERT backend 异常 ($code)"; notify_alert "ERP巡检告警: backend 异常 ($code)"; fail=1; fi

# 3. 数据库容器健康
st=$(docker inspect -f '{{.State.Health.Status}}' erp-mysql 2>/dev/null || echo none)
if [ "$st" = healthy ]; then echo "$LOG_TAG OK mysql"; else echo "$LOG_TAG ALERT mysql 状态=$st"; notify_alert "ERP巡检告警: mysql 状态=$st"; fail=1; fi

# 4. Prometheus 抓取目标（监控栈启动时）
if docker ps --format '{{.Names}}' | grep -q '^erp-prometheus$'; then
  up=$(curl -sG -m 5 'http://localhost:9090/api/v1/query' --data-urlencode 'query=up{job="erp-backend"}' \
      | grep -c '"value":\[[0-9.]*,"1"')
  if [ "$up" -ge 1 ]; then echo "$LOG_TAG OK prometheus 抓取正常"; else echo "$LOG_TAG ALERT prometheus 抓取失败"; notify_alert "ERP巡检告警: prometheus 抓取失败"; fail=1; fi
fi

exit $fail
