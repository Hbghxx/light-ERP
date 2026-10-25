#!/usr/bin/env bash
# =====================================================================
# ERP 数据库定时备份脚本（cron 用，独立于 deploy.sh）
# crontab 示例（每日 02:10）:
#   10 2 * * * /opt/erp/scripts/backup.sh >> /opt/erp/logs/backup.log 2>&1
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

BACKUP_DIR="${BACKUP_DIR:-backups}"
KEEP_DAYS="${KEEP_DAYS:-7}"
LOG_TAG="[backup $(date '+%F %T')]"
source "$(dirname "$0")/alert-lib.sh"

mkdir -p "$BACKUP_DIR" logs

PW="$(docker exec erp-mysql printenv MYSQL_ROOT_PASSWORD 2>/dev/null)" \
  || { echo "$LOG_TAG FAIL: 无法读取 erp-mysql 容器密码（容器未运行？）"; notify_alert "ERP备份失败: 无法连接数据库容器"; exit 1; }

F="$BACKUP_DIR/erp_$(date +%Y%m%d_%H%M%S).sql"
docker exec -e MYSQL_PWD="$PW" erp-mysql \
  mysqldump --default-character-set=utf8mb4 -uroot --single-transaction erp > "$F"

# 空备份防护：正常应远大于 1KB
if [ "$(stat -c%s "$F")" -lt 1024 ]; then
  echo "$LOG_TAG FAIL: 备份文件异常偏小($F)，请人工检查"
  notify_alert "ERP备份告警: 备份文件异常偏小，请人工检查"
  exit 1
fi

gzip -f "$F"
find "$BACKUP_DIR" -name 'erp_*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "$LOG_TAG OK: $F.gz ($(stat -c%s "$F.gz") bytes)"
