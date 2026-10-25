#!/usr/bin/env bash
# =====================================================================
# 告警通知公共库（被 backup/reconcile/uptime-check source）
# 用法: source "$(dirname "$0")/alert-lib.sh" 后调用 notify_alert "文本"
# ALERT_WEBHOOK 从项目根 .env 读取（钉钉/企微/飞书自定义机器人均可，
# 消息体为 {"msgtype":"text","text":{"content":"..."}} 通用格式）
# =====================================================================

load_alert_env(){
  local root
  root="$(cd "$(dirname "${BASH_SOURCE[1]}")/.." && pwd)"
  if [ -f "$root/.env" ]; then
    # 仅提取 ALERT_WEBHOOK，避免 source 整个 .env 引入意外变量
    ALERT_WEBHOOK="$(grep -E '^ALERT_WEBHOOK=' "$root/.env" | head -1 | cut -d= -f2- | tr -d '\"' | tr -d "'" || true)"
  fi
  export ALERT_WEBHOOK="${ALERT_WEBHOOK:-}"
}

notify_alert(){
  load_alert_env
  [ -n "$ALERT_WEBHOOK" ] || { echo "[alert-lib] 未配置 ALERT_WEBHOOK，仅记录日志"; return 0; }
  curl -s -m 5 -H 'Content-Type: application/json' \
    -d "{\"msgtype\":\"text\",\"text\":{\"content\":\"$1\"}}" "$ALERT_WEBHOOK" >/dev/null 2>&1 \
    || echo "[alert-lib] webhook 通知发送失败"
}
