-- =====================================================================
-- 增量升级 003: 报表权限(阶段7)
-- 执行方式: mysql --default-character-set=utf8mb4 -D erp -uroot -p < upgrade-003-report.sql
-- =====================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO sys_permission (id, perm_code, perm_name) VALUES
(14, 'report:view', '报表查看');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
(1, 14),  -- ADMIN
(2, 14),  -- AUDITOR
(4, 14),  -- PURCHASE_MANAGER
(6, 14);  -- FINANCE
