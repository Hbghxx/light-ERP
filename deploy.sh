#!/usr/bin/env bash
# =====================================================================
# ERP 一键部署脚本（Linux + Docker Compose）
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")"

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; NC='\033[0m'
info(){ echo -e "${GREEN}[INFO]${NC} $*"; }
warn(){ echo -e "${YELLOW}[WARN]${NC} $*"; }
fail(){ echo -e "${RED}[FAIL]${NC} $*"; exit 1; }

COMPOSE="docker compose"
BACKUP_DIR="backups"
KEEP_DAYS=7

usage(){
  cat <<'EOF'
用法: ./deploy.sh [子命令]

  deploy    (默认)环境检查 -> 重部署前自动备份 -> 构建启动 -> 健康检查与接口自检
  status    查看容器状态与资源占用
  logs      跟随服务日志: ./deploy.sh logs [backend|mysql|nginx]，默认 backend
  backup    手动全量备份数据库到 backups/（自动清理 7 天前的备份）
  down      停止全部服务（保留数据卷）
  destroy   停止并删除数据卷（数据库清空，需输入 YES 确认）
  help      显示本帮助
EOF
}

check_env(){
  [ -f docker-compose.yml ] || fail "请在项目根目录执行本脚本"
  [ -f docs/schema/schema.sql ] || fail "缺少 docs/schema/schema.sql"
  command -v docker >/dev/null 2>&1 || fail "未安装 Docker，安装方法见 docs/deploy.md 第 1 节"
  $COMPOSE version >/dev/null 2>&1 || fail "未安装 Docker Compose v2 插件，安装方法见 docs/deploy.md 第 1 节"
  docker info >/dev/null 2>&1 || fail "当前用户无 Docker 权限，请用 sudo 运行或将用户加入 docker 组"
  command -v curl >/dev/null 2>&1 || fail "未安装 curl: apt install -y curl 或 yum install -y curl"
  if ss -lnt 2>/dev/null | grep -q ':80 '; then
    docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^erp-nginx$' \
      || fail "80 端口已被其他进程占用，请释放或修改 docker-compose.yml 端口映射"
  fi
}

# 数据库密码从容器环境变量读取，与 docker-compose.yml/.env 保持单一来源，脚本内不落字面量
mysql_password(){
  local pw
  pw=$(docker exec erp-mysql printenv MYSQL_ROOT_PASSWORD 2>/dev/null) \
    || fail "无法从 erp-mysql 容器读取 MYSQL_ROOT_PASSWORD（容器未运行？）"
  echo "$pw"
}

do_backup(){
  mkdir -p "$BACKUP_DIR"
  local pw f
  pw="$(mysql_password)"
  f="$BACKUP_DIR/erp_$(date +%Y%m%d_%H%M%S).sql"
  info "备份数据库 -> $f"
  docker exec -e MYSQL_PWD="$pw" erp-mysql \
    mysqldump --default-character-set=utf8mb4 -uroot --single-transaction erp > "$f"
  gzip -f "$f"
  find "$BACKUP_DIR" -name 'erp_*.sql.gz' -mtime +"$KEEP_DAYS" -delete
  info "备份完成(已压缩), 保留最近 ${KEEP_DAYS} 天:"
  ls -lh "$BACKUP_DIR" | tail -3
}

wait_healthy(){
  info "等待服务就绪（首次启动含建库建表与依赖下载，约 30-90 秒）..."
  for _ in $(seq 1 45); do
    if curl -sf http://localhost/product/list >/dev/null 2>&1; then
      info "服务已就绪"
      return 0
    fi
    sleep 2
  done
  warn "90 秒未就绪，请排查: docker compose logs backend"
  return 1
}

verify(){
  info "接口自检: $(curl -s http://localhost/product/list | head -c 40)..."
  local ip
  ip=$(hostname -I 2>/dev/null | awk '{print $1}')
  info "部署完成 ✔  浏览器访问: http://${ip:-localhost}/"
}

# 预拉构建基础镜像:镜像加速器偶发连接僵死,重试 + 重启 docker 兜底;
# 基础镜像本地化后,构建阶段不再依赖外网(WSL2 实测踩坑后的固化)
pre_pull_bases(){
  local img ok i
  for img in maven:3.9-eclipse-temurin-17 eclipse-temurin:17-jre; do
    docker image inspect "$img" >/dev/null 2>&1 && continue
    ok=0
    for i in 1 2 3; do
      info "预拉基础镜像 $img (第 $i 次)"
      if timeout 300 docker pull "$img"; then ok=1; break; fi
      warn "拉取失败,重启 docker 后重试"
      systemctl restart docker
      sleep 3
    done
    [ "$ok" -eq 1 ] || fail "$img 预拉失败,请检查网络或镜像加速配置后重试"
  done
}

cmd_deploy(){
  check_env
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^erp-mysql$'; then
    info "检测到已有部署，先备份数据库"
    do_backup
  else
    info "首次部署（不备份）"
  fi
  pre_pull_bases
  info "构建并启动（首次构建约 3-10 分钟）..."
  $COMPOSE up -d --build
  if ! wait_healthy; then
    $COMPOSE ps
    exit 1
  fi
  verify
}

cmd_status(){
  check_env
  $COMPOSE ps
  docker system df 2>/dev/null || true
}

cmd_logs(){
  check_env
  $COMPOSE logs -f "${2:-backend}"
}

cmd_down(){
  check_env
  $COMPOSE down
  info "已停止（数据卷保留），重新部署执行 ./deploy.sh deploy"
}

cmd_destroy(){
  check_env
  echo -e "${RED}将删除全部容器与数据卷（数据库清空，不可恢复）！输入 YES 确认:${NC}"
  read -r ans
  [ "$ans" = "YES" ] || { warn "已取消"; exit 0; }
  $COMPOSE down -v
  info "已销毁"
}

case "${1:-deploy}" in
  deploy)  cmd_deploy ;;
  status)  cmd_status ;;
  logs)    cmd_logs "$@" ;;
  backup)  check_env; do_backup ;;
  down)    cmd_down ;;
  destroy) cmd_destroy ;;
  help|-h|--help) usage ;;
  *)       usage; exit 1 ;;
esac
