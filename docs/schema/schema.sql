-- =====================================================================
-- ERP 全量建表脚本（全新环境一次性执行）
-- 执行方式: mysql --default-character-set=utf8mb4 -uroot -p < schema.sql
-- 存量库升级请使用 upgrade/ 下的增量脚本,不要重复执行本文件
--
-- 约定:
--   1. 权限/组织/审计域表统一 sys_ 前缀(user 是 MySQL 保留字,且与主流
--      开源 ERP/RBAC 实现的命名习惯对齐);业务表保持原命名。
--   2. 表间为逻辑外键关联,不建物理外键;关联一致性由应用层校验保证。
--   3. 应用层所有 SQL 一律使用参数绑定(#{} 占位),禁止字符串拼接。
--   4. 全库 utf8mb4,时间戳使用 DATETIME + CURRENT_TIMESTAMP 默认值。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS erp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE erp;

-- 脚本自声明字符集：任何客户端/容器 entrypoint 执行都不会中文乱码
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 一、业务域
-- ---------------------------------------------------------------------

-- 1. 商品主数据表
CREATE TABLE IF NOT EXISTS product_info (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '商品ID',
    sku        VARCHAR(50)  NOT NULL UNIQUE COMMENT '商品SKU编码',
    name       VARCHAR(100) NOT NULL COMMENT '商品名称',
    price      DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '基础售价',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品主表';

-- 2. 实时库存表 (含乐观锁)
CREATE TABLE IF NOT EXISTS inventory_stock (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    product_sku VARCHAR(50) NOT NULL UNIQUE COMMENT '关联商品SKU',
    stock_qty   INT NOT NULL DEFAULT 0 COMMENT '当前可用库存量',
    version     INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP() ON UPDATE CURRENT_TIMESTAMP() COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存表';

-- 3. 采购入库流水表 (含权限/审批所需的快照列)
CREATE TABLE IF NOT EXISTS purchase_inbound (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '入库单ID',
    inbound_no   VARCHAR(50) NOT NULL UNIQUE COMMENT '采购入库单号',
    product_sku  VARCHAR(50) NOT NULL COMMENT '商品SKU',
    quantity     INT NOT NULL COMMENT '采购数量',
    unit_price   DECIMAL(10,2) NULL COMMENT '下单时售价快照',
    total_amount DECIMAL(12,2) NULL COMMENT 'qty × unit_price,审批分档依据',
    supplier_name VARCHAR(100) COMMENT '供应商名称',
    status       TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1待审批 2已通过(已入账) 3已驳回 4已作废',
    created_by   BIGINT NULL COMMENT '下单人 sys_user.id',
    dept_id      BIGINT NULL COMMENT '下单时部门快照(人转部门不影响历史归属)',
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '入库时间',
    KEY idx_created_by (created_by),
    KEY idx_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购入库记录';

-- 4. 销售出库流水表 (快照列同上)
CREATE TABLE IF NOT EXISTS sales_outbound (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '出库单ID',
    outbound_no  VARCHAR(50) NOT NULL UNIQUE COMMENT '销售出库单号',
    product_sku  VARCHAR(50) NOT NULL COMMENT '商品SKU',
    quantity     INT NOT NULL COMMENT '销售出库数量',
    unit_price   DECIMAL(10,2) NULL COMMENT '下单时售价快照',
    total_amount DECIMAL(12,2) NULL COMMENT 'qty × unit_price,审批分档依据',
    customer_name VARCHAR(100) COMMENT '客户名称',
    status       TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1待审批 2已通过(已出账) 3已驳回 4已作废',
    created_by   BIGINT NULL COMMENT '下单人 sys_user.id',
    dept_id      BIGINT NULL COMMENT '下单时部门快照',
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '出库时间',
    KEY idx_created_by (created_by),
    KEY idx_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售出库记录';

-- ---------------------------------------------------------------------
-- 二、组织域
-- ---------------------------------------------------------------------

-- 5. 部门表 (邻接表 + 物化路径,查子树: path LIKE CONCAT(#{path}, '%'))
CREATE TABLE IF NOT EXISTS sys_dept (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '部门ID',
    name      VARCHAR(100) NOT NULL COMMENT '部门名称',
    parent_id BIGINT NULL COMMENT '上级部门ID,根节点为NULL',
    path      VARCHAR(200) NOT NULL COMMENT '物化路径,如 /1/3/7/',
    level     INT NOT NULL COMMENT '层级,根为1',
    KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门树:集团-公司-部门-小组';

-- ---------------------------------------------------------------------
-- 三、权限域 (RBAC + 账号分层)
-- ---------------------------------------------------------------------

-- 6. 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username        VARCHAR(50)  NOT NULL UNIQUE COMMENT '登录名',
    password        VARCHAR(100) NOT NULL COMMENT 'BCrypt哈希(60字符)',
    real_name       VARCHAR(50) COMMENT '姓名',
    dept_id         BIGINT NOT NULL COMMENT '所属部门 sys_dept.id',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0停用 2锁定',
    error_count     INT NOT NULL DEFAULT 0 COMMENT '连续密码错误次数,达5次置为锁定',
    need_change_pwd TINYINT NOT NULL DEFAULT 1 COMMENT '首次登录强制改密: 1是 0否',
    last_login_at   DATETIME NULL COMMENT '最近登录时间',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 7. 角色表 (账号分层通过角色表达:ADMIN/AUDITOR/业务角色,不设 user_type 列)
CREATE TABLE IF NOT EXISTS sys_role (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    role_code  VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码:ADMIN/AUDITOR/PURCHASER/PURCHASE_MANAGER/WAREHOUSE/FINANCE',
    role_name  VARCHAR(50) NOT NULL COMMENT '角色名称',
    data_scope VARCHAR(20) NOT NULL DEFAULT 'SELF' COMMENT '数据权限: ALL全部/DEPT_AND_CHILD本部门及以下/SELF仅本人',
    is_builtin TINYINT NOT NULL DEFAULT 0 COMMENT '内置角色不可删除: 1是 0否',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 8. 权限表 (功能权限,权限码 = 资源:动作)
CREATE TABLE IF NOT EXISTS sys_permission (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '权限ID',
    perm_code VARCHAR(100) NOT NULL UNIQUE COMMENT '权限码,如 inbound:create / product:price:update',
    perm_name VARCHAR(50) NOT NULL COMMENT '权限名称'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 9. 用户-角色关联 (联合主键天然防重复授权)
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL COMMENT 'sys_user.id',
    role_id BIGINT NOT NULL COMMENT 'sys_role.id',
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

-- 10. 角色-权限关联
CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id       BIGINT NOT NULL COMMENT 'sys_role.id',
    permission_id BIGINT NOT NULL COMMENT 'sys_permission.id',
    PRIMARY KEY (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联';

-- ---------------------------------------------------------------------
-- 四、审计域
-- ---------------------------------------------------------------------

-- 11. 审计日志 (只增不改;覆盖业务单据动作与权限变更留痕)
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    user_id    BIGINT NOT NULL COMMENT '操作人 sys_user.id',
    action     VARCHAR(20) NOT NULL COMMENT 'CREATE/UPDATE/AUDIT/REJECT/CANCEL/GRANT_ROLE/DISABLE_USER等',
    doc_type   VARCHAR(30) NULL COMMENT '单据/对象类型: INBOUND/OUTBOUND/USER/ROLE',
    doc_id     BIGINT NULL COMMENT '对象ID',
    doc_no     VARCHAR(50) NULL COMMENT '单据号(如有)',
    detail     JSON NULL COMMENT '变更前后快照',
    ip         VARCHAR(45) NULL COMMENT '操作来源IP(兼容IPv6)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '操作时间',
    KEY idx_doc (doc_type, doc_id),
    KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志';

-- ---------------------------------------------------------------------
-- 五、审批域 (阶段6: 金额分档审批流)
-- ---------------------------------------------------------------------

-- 12. 审批规则表 (按单据场景 + 金额区间分档;approver_role_id 为 NULL 表示自动通过)
CREATE TABLE IF NOT EXISTS approval_rule (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '规则ID',
    scene            VARCHAR(20)  NOT NULL COMMENT '场景: INBOUND/OUTBOUND',
    min_amount       DECIMAL(12,2) NULL COMMENT '金额下限(含),NULL为无界',
    max_amount       DECIMAL(12,2) NULL COMMENT '金额上限(不含),NULL为无界',
    approver_role_id BIGINT NULL COMMENT '审批角色 sys_role.id,NULL=自动通过',
    enabled          TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    KEY idx_scene (scene, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批规则(金额分档)';

-- 13. 审批记录表 (提交时快照命中规则,规则日后改动不影响历史单据;只增不改)
CREATE TABLE IF NOT EXISTS approval_record (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    doc_type         VARCHAR(20) NOT NULL COMMENT 'INBOUND/OUTBOUND',
    doc_id           BIGINT NOT NULL COMMENT '单据ID',
    rule_id          BIGINT NULL COMMENT '命中规则快照',
    approver_role_id BIGINT NULL COMMENT '审批角色快照(NULL=自动通过)',
    applicant_id     BIGINT NOT NULL COMMENT '制单人',
    approver_id      BIGINT NULL COMMENT '实际审批人(NULL=待审/系统)',
    action           VARCHAR(20) NOT NULL COMMENT 'SUBMIT/AUTO_PASS/APPROVE/REJECT/CANCEL',
    opinion          VARCHAR(200) NULL COMMENT '审批意见/驳回原因',
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '时间',
    KEY idx_doc (doc_type, doc_id),
    KEY idx_approver (approver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录';
