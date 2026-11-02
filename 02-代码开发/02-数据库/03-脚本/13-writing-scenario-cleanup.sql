-- ============================================================
-- jeecg-study · 写作情境库种子清理脚本 writing-scenario-cleanup.sql
-- 需求：F-WRT-03《素材改情境库》
-- 目标库：jeecg-study（MySQL 8）· 表前缀 study_
--
-- 作用：清掉 `study_writing_scenario` 的**种子数据**（保留表结构）
--
-- 为什么单独一个文件：`06-cleanup.sql` 是**已登记的历史脚本**，
--       按铁律 2「变更走增量脚本，不改历史脚本」不能回改它，
--       因此本表的清理语句放在这个配套脚本里，由测试环境单独执行。
--
-- 幂等：重复执行不报错（删不到就是删不到）。
-- 回滚：种子数据可由 12-writing-scenario.sql 重新灌回（幂等 INSERT）。
-- ============================================================

USE `jeecg-study`;

-- 清理写作情境库的种子数据（业务ID 形如 SEED-SCN-{主题码}-{序号}）
DELETE FROM `study_writing_scenario` WHERE `scenario_code` LIKE 'SEED%';
