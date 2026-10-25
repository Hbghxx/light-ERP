-- =====================================================================
-- 增量升级 002: 审批流(阶段6)
-- 内容: 单据状态语义迁移 + 审批两张表 + 审批权限点 + 审批规则种子
-- 执行方式: mysql --default-character-set=utf8mb4 -D erp -uroot -p < upgrade-002-approval.sql
-- 注意: 已执行过的库重复执行会报 Duplicate,属预期,可忽略
-- =====================================================================
SET NAMES utf8mb4;

-- 1. 状态语义升级: 旧 1(已入库/已出库,无审批流时代) → 2(已通过/已入账)
UPDATE purchase_inbound SET status = 2 WHERE status = 1;
UPDATE sales_outbound   SET status = 2 WHERE status = 1;

-- 2. 审批两张表
CREATE TABLE IF NOT EXISTS approval_rule (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '规则ID',
    scene            VARCHAR(20)  NOT NULL COMMENT '场景: INBOUND/OUTBOUND',
    min_amount       DECIMAL(12,2) NULL COMMENT '金额下限(含),NULL为无界',
    max_amount       DECIMAL(12,2) NULL COMMENT '金额上限(不含),NULL为无界',
    approver_role_id BIGINT NULL COMMENT '审批角色 sys_role.id,NULL=自动通过',
    enabled          TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    KEY idx_scene (scene, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批规则(金额分档)';

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

-- 3. 审批权限点
INSERT IGNORE INTO sys_permission (id, perm_code, perm_name) VALUES
(12, 'inbound:audit',  '入库审批'),
(13, 'outbound:audit', '出库审批');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
(1, 12), (1, 13),   -- ADMIN
(4, 12), (4, 13);   -- PURCHASE_MANAGER

-- 4. 审批规则种子
INSERT IGNORE INTO approval_rule (id, scene, min_amount, max_amount, approver_role_id, enabled) VALUES
(1, 'INBOUND',  0,     999.99, NULL, 1),
(2, 'INBOUND',  1000,  9999.99, 4,    1),
(3, 'INBOUND',  10000, NULL,    1,    1),
(4, 'OUTBOUND', 0,     999.99, NULL, 1),
(5, 'OUTBOUND', 1000,  9999.99, 4,    1),
(6, 'OUTBOUND', 10000, NULL,    1,    1);
