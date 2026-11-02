-- ============================================================
-- jeecg-study · 种子数据清理脚本 cleanup.sql
-- 由 05-公共资源\2026103017-DDL提取.py 从《数据库设计说明书》源稿生成
-- 目标库：jeecg-study（MySQL 8）· 表前缀 study_
-- 幂等：建表用 IF NOT EXISTS；改结构用 information_schema 判定后动态执行
-- ============================================================

USE `jeecg-study`;

-- 清理全部种子数据（保留结构）
DELETE FROM study_error_pool       WHERE error_code LIKE 'SEED%';
DELETE FROM study_review_schedule  WHERE error_code LIKE 'SEED%';
DELETE FROM study_question_variant WHERE variant_code LIKE 'SEED%';
DELETE FROM study_question         WHERE question_code LIKE 'SEED%';
DELETE FROM study_mother_question  WHERE mother_code LIKE 'SEED%';
DELETE FROM study_word             WHERE word_code LIKE 'SEED%';
DELETE FROM study_grammar_point    WHERE grammar_code LIKE 'SEED%';
DELETE FROM study_writing          WHERE writing_code LIKE 'SEED%';
DELETE FROM study_knowledge_point  WHERE point_code LIKE 'SEED%';

