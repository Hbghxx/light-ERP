#!/usr/bin/env bash
# =====================================================================
# 数据库恢复脚本（恢复演练记录 #001 改进项的固化）
# 用法: ./scripts/restore.sh backups/erp_YYYYMMDD_HHMMSS.sql.gz
# 安全机制: 恢复前自动对当前状态再做一次兜底备份;恢复后自动执行库存对账
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

BK="${1:-}"
[ -n "$BK" ] && [ -f "$BK" ] || { echo "用法: $0 backups/erp_YYYYMMDD_HHMMSS.sql.gz"; exit 1; }
LOG_TAG="[restore $(date '+%F %T')]"

PW="$(docker exec erp-mysql printenv MYSQL_ROOT_PASSWORD 2>/dev/null)" \
  || { echo "$LOG_TAG FAIL: 无法读取 erp-mysql 容器密码（容器未运行？）"; exit 1; }

# 1. 恢复前兜底:先把当前(可能已损坏的)状态备份一份,保留事故现场
echo "$LOG_TAG 恢复前兜底备份当前状态..."
./scripts/backup.sh

# 2. 停写
echo "$LOG_TAG 停止后端(防恢复期间脏写)..."
docker compose stop backend >/dev/null

# 3. 灌入备份(.gz 自动解压,纯 .sql 也支持)
T0=$(date +%s)
case "$BK" in
  *.gz) gunzip -c "$BK" ;;
  *)    cat "$BK" ;;
esac | docker exec -i -e MYSQL_PWD="$PW" erp-mysql \
  sh -c 'mysql --default-character-set=utf8mb4 -uroot erp'
T1=$(date +%s)
echo "$LOG_TAG 灌库耗时 $((T1-T0))s"

# 4. 起后端并等待就绪(探后端接口而非 nginx 静态页,确保真正可用)
docker compose start backend >/dev/null
for _ in $(seq 1 45); do
  code=$(curl -s -o /dev/null -m 3 -w '%{http_code}' http://localhost/product/list 2>/dev/null || echo 000)
  [ "$code" = 401 ] && break
  sleep 2
done

# 5. 对账复核
echo "$LOG_TAG 恢复后对账复核:"
if ./scripts/reconcile.sh; then
  echo "$LOG_TAG 恢复完成 ✔ 数据与对账均正常"
else
  echo "$LOG_TAG 恢复完成,但对账存在差异(若备份本身产生于差异时期属预期),请人工核查"
fi
