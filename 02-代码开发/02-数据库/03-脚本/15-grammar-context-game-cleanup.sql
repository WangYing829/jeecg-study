-- ============================================================
-- jeecg-study · 语法语境库 + 趣味玩法 种子清理脚本
-- 需求：F-GRM-05 语境库复用 / F-GRM-04 趣味玩法
-- 目标库：jeecg-study（MySQL 8）· 表前缀 study_
--
-- 作用：清掉 14-grammar-context-game.sql 灌进来的种子数据（**保留表结构**）
-- 使用场景：仅测试环境 / 重置演示数据
--
-- 清理顺序：**先删行为表，再删内容表**（D-08：先删行为表，避免留下指向空父行的记录）
-- 幂等：DELETE 本身幂等，重复执行不报错
--
-- 注意：`study_id_sequence` 里 SCT / GAM 两个前缀**保留** ——
--       序列只增不复用，删掉前缀会让下一次分配从 1 重新开始，
--       与库里已用的业务ID 撞号。
-- ============================================================

USE `jeecg-study`;

-- 行为表：先删
DELETE FROM `study_grammar_game_record`   WHERE `id` LIKE 'SEED%';

-- 内容表：后删
DELETE FROM `study_grammar_game_question` WHERE `id` LIKE 'SEED%';
DELETE FROM `study_word_context`            WHERE `id` LIKE 'SEED%';
DELETE FROM `study_sentence_context`        WHERE `id` LIKE 'SEED%';
