#!/usr/bin/env bash
# =====================================================================
# 库存对账脚本（cron 用）：执行 docs/ops/reconcile.sql，存在差异行即退出码 1
# crontab 示例（每日 02:20，紧跟备份之后）:
#   20 2 * * * /opt/erp/scripts/reconcile.sh >> /opt/erp/logs/reconcile.log 2>&1
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

LOG_TAG="[reconcile $(date '+%F %T')]"
SQL_FILE="docs/ops/reconcile.sql"
source "$(dirname "$0")/alert-lib.sh"
PW="$(docker exec erp-mysql printenv MYSQL_ROOT_PASSWORD 2>/dev/null)" \
  || { echo "$LOG_TAG FAIL: 无法读取容器密码（容器未运行？）"; notify_alert "ERP对账失败: 无法连接数据库容器"; exit 1; }

OUT="$(docker exec -i -e MYSQL_PWD="$PW" erp-mysql sh -c \
  'mysql --default-character-set=utf8mb4 -uroot erp --table' < "$SQL_FILE")"

echo "$LOG_TAG 对账结果:"
echo "$OUT"

# 表头 3 行（提示+表头+分隔线），其余为数据行；diff 列非 0 即差异
DIFF_ROWS="$(echo "$OUT" | tail -n +4 | awk -F'|' 'NF>4 { gsub(/ /,"",$5); if ($5+0 != 0) c++ } END { print c+0 }')"

if [ "$DIFF_ROWS" -gt 0 ]; then
  echo "$LOG_TAG ALERT: 发现 $DIFF_ROWS 个 SKU 账实不符，请立即核查！"
  notify_alert "ERP库存对账告警: ${DIFF_ROWS} 个SKU账实不符，请立即核查"
  exit 1
fi
echo "$LOG_TAG OK: 账实相符"
