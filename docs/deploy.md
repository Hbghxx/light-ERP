# ERP 部署手册

> 目标:在一台全新 Linux 服务器上,30 分钟内把系统跑起来。
> 架构:Nginx(80,托管前端 + 反代 API)→ Spring Boot 后端(8080)→ MySQL 8.0,全部容器化,Docker Compose 一键编排。

## 0. 一键部署(推荐)

```bash
git clone <仓库地址> erp && cd erp
./deploy.sh                 # 等效: bash deploy.sh
```

脚本自动完成:环境检查 → **重部署前自动备份数据库**(压缩后保留 7 天)→ 构建启动 → 健康检查与接口自检,成功后打印访问地址。

| 子命令 | 作用 |
|---|---|
| `./deploy.sh status` | 容器状态与资源占用 |
| `./deploy.sh logs nginx` | 跟随日志(backend/mysql/nginx,默认 backend) |
| `./deploy.sh backup` | 手动全量备份到 `backups/` |
| `./deploy.sh down` | 停止(保留数据) |
| `./deploy.sh destroy` | 删除容器与数据卷(需输入 YES) |

以下为分步命令,与脚本等效,便于理解每一步在做什么。

## 1. 环境准备

- 一台 Linux 服务器(2C4G 起步,Ubuntu 22.04 / CentOS Stream 均可)
- 安装 Docker 与 Compose 插件:

```bash
# Ubuntu/Debian(发行版仓库安装,生产建议参考 Docker 官方文档的仓库配置方式)
sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
docker compose version   # 确认 v2 插件可用
```

## 2. 获取代码并启动

```bash
git clone <仓库地址> erp && cd erp
docker compose up -d --build
```

首次构建会下载 Maven 依赖,约 3–10 分钟(已用 Docker 层缓存优化:pom 不变时依赖只下一次)。完成后:

```bash
docker compose ps          # 三个容器应为 running,mysql 显示 healthy
curl http://localhost/product/list
# 期望返回: {"code":0,"message":"操作成功","data":[...]}
```

浏览器访问 `http://<服务器IP>/` 即可使用。

## 3. 数据初始化说明(重要)

- MySQL 容器**首次启动且数据卷为空**时,自动执行 `docs/schema/`(01 建表、02 种子数据);
- 之后重启/升级**不会**重复执行初始化脚本——这是 MySQL 官方镜像 `docker-entrypoint-initdb.d` 的行为;
- 结构变更请按序执行增量脚本(001 单据快照列 / 002 审批流 / 003 报表权限),以 002 为例:
  `docker exec -i erp-mysql sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" erp' < docs/schema/upgrade-002-approval.sql`;
- **客户端进容器执行 SQL 一律带 `--default-character-set=utf8mb4`**,否则中文乱码(实测踩过)。

## 4. 日常运维命令

```bash
docker compose ps                      # 状态
docker compose logs -f backend         # 跟后端日志(CTRL+C 退出)
docker compose restart backend         # 重启单个服务
docker compose down                    # 停止(数据卷保留)
docker compose down -v                 # ⚠️ 连数据卷一起删,等于清库
docker exec -it erp-mysql sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" erp'
```

## 5. 内置的运维保障

| 机制 | 实现 |
|---|---|
| 进程自愈 | 全部服务 `restart: unless-stopped`,异常退出自动拉起 |
| 启动顺序 | 后端等 MySQL healthcheck 通过才启动,避免连库失败 |
| 日志轮转 | json-file 驱动,单容器 10MB × 3 个文件,防磁盘打满 |
| 时区 | 容器统一 Asia/Shanghai,与 JDBC `serverTimezone` 一致 |
| 字符集 | MySQL 服务端 utf8mb4,与库表、连接参数三层一致 |

## 6. 备份(阶段 3 将自动化,当前手动版)

```bash
# 全量备份到宿主机当前目录
docker exec erp-mysql sh -c 'mysqldump --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction erp' > erp_$(date +%F_%H%M).sql

# 恢复演练: 先在测试库验证,再进生产
docker exec -i erp-mysql sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" erp' < erp_xxxx.sql
```

## 7. 故障排查速查

| 现象 | 排查 |
|---|---|
| 80 端口被占用 | `ss -lntp \| grep :80`,停掉宿主机 nginx/apache 或改 compose 端口映射 |
| 页面打得开但接口全红 | `docker compose logs backend` 看连库报错;`docker compose ps` 看 mysql 是否 healthy |
| 返回 500 | 后端日志找 `未处理异常`;数据库字段/数据问题居多 |
| 中文乱码 | 建表/种子脚本已内置 `SET NAMES utf8mb4`;手工执行 SQL 的客户端仍建议带 `--default-character-set=utf8mb4` |
| 外网访问不了 | 云服务器安全组/防火墙放行 80;`curl http://localhost/` 先本机自证 |
| **WSL 部署一段时间后 localhost 拒绝连接** | WSL 空闲约 1 分钟会回收整个发行版(容器一并停止)。保持一个 WSL 终端会话(或后台 `wsl -d Ubuntu-22.04 -- sleep infinity`)即可常驻;云服务器无此问题 |

### 登录与初始账号

- 首次启动若 `sys_user` 为空,自动初始化 `admin` 账号,**随机初始密码打印在后端日志**(`docker logs erp-backend \| grep 初始密码`),登录后请立即改密;
- 内置角色:ADMIN(超管)、AUDITOR(审计只读)、PURCHASER/PURCHASE_MANAGER/WAREHOUSE/FINANCE;
- 所有账号/权限变更动作(建号、停用、解锁、重置密码、分配角色)自动写入审计日志(“审计日志”页签,需 `audit:view` 权限)。

## 8. 安全基线(上线前必做)

1. 修改数据库密码:在项目根目录复制 `.env.example` 为 `.env`,设置 `MYSQL_ROOT_PASSWORD`(compose 与后端自动读取,**不要用仓库默认值**,也不要把 `.env` 提交进 git);
2. 生产环境不对外暴露 3306(当前配置仅容器网络内可连,勿擅自加端口映射);
3. 服务器只放行 80/443 与 SSH;
4. 阶段 5 上线登录后,所有写接口需携带 token,届时补充账号安全基线。
