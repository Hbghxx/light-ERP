-- =====================================================================
-- 增量升级 001: 存量库补齐单据快照列与索引
-- 适用: 在本脚本发布前已按旧版 4 表结构初始化的库
-- 全新环境直接执行 ../schema.sql,跳过本文件。
-- 注意: MySQL 8 不支持 ADD COLUMN IF NOT EXISTS,重复执行会报
--       Duplicate column name,属预期,可忽略。
-- 执行方式: mysql --default-character-set=utf8mb4 -D erp -uroot -p < upgrade-001-biz-columns.sql（不内置 USE，目标库由 -D 指定）
-- =====================================================================

-- 脚本自声明字符集：任何客户端执行 ALTER 中的中文 COMMENT 都不会乱码
SET NAMES utf8mb4;

ALTER TABLE purchase_inbound
    ADD COLUMN unit_price   DECIMAL(10,2) NULL COMMENT '下单时售价快照' AFTER quantity,
    ADD COLUMN total_amount DECIMAL(12,2) NULL COMMENT 'qty × unit_price,审批分档依据' AFTER unit_price,
    ADD COLUMN created_by   BIGINT NULL COMMENT '下单人 sys_user.id' AFTER status,
    ADD COLUMN dept_id      BIGINT NULL COMMENT '下单时部门快照(人转部门不影响历史归属)' AFTER created_by,
    ADD KEY idx_created_by (created_by),
    ADD KEY idx_dept (dept_id);

ALTER TABLE sales_outbound
    ADD COLUMN unit_price   DECIMAL(10,2) NULL COMMENT '下单时售价快照' AFTER quantity,
    ADD COLUMN total_amount DECIMAL(12,2) NULL COMMENT 'qty × unit_price,审批分档依据' AFTER unit_price,
    ADD COLUMN created_by   BIGINT NULL COMMENT '下单人 sys_user.id' AFTER status,
    ADD COLUMN dept_id      BIGINT NULL COMMENT '下单时部门快照' AFTER created_by,
    ADD KEY idx_created_by (created_by),
    ADD KEY idx_dept (dept_id);

-- 权限/组织/审计域 7 张表(建表提前,代码在阶段 5/6 接入):
-- 存量库执行完上面两条 ALTER 后,继续执行 ../schema.sql 即可——
-- 其中的 CREATE TABLE IF NOT EXISTS 只会补建缺失的表,不会动已有业务表。
