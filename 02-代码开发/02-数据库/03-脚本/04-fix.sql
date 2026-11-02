-- ============================================================
-- jeecg-study · 增量修复脚本 fix.sql —— 由设计源稿生成
-- 由 05-公共资源\2026103017-DDL提取.py 从《数据库设计说明书》源稿生成
-- 目标库：jeecg-study（MySQL 8）· 表前缀 study_
-- 幂等：建表用 IF NOT EXISTS；改结构用 information_schema 判定后动态执行
-- ============================================================

USE `jeecg-study`;

-- 幂等加列：MySQL 8.0.29 之前不支持 ADD COLUMN IF NOT EXISTS，改用 information_schema 判定
SET @study_ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME   = 'study_context'
         AND COLUMN_NAME = 'child_id') = 0,
    'ALTER TABLE `study_context` ADD COLUMN `child_id` varchar(32) DEFAULT NULL COMMENT ''所属孩子 study_child.id(多用户隔离)'' AFTER `id`',
    'SELECT 1'
  )
);
PREPARE study_stmt FROM @study_ddl;
EXECUTE study_stmt;
DEALLOCATE PREPARE study_stmt;

-- 幂等加索引：先查 information_schema.STATISTICS 是否已存在
SET @study_ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME   = 'study_context'
         AND INDEX_NAME   = 'idx_context_child') = 0,
    'ALTER TABLE `study_context` ADD KEY `idx_context_child` (`child_id`, `del_flag`)',
    'SELECT 1'
  )
);
PREPARE study_stmt FROM @study_ddl;
EXECUTE study_stmt;
DEALLOCATE PREPARE study_stmt;

-- 幂等加列：MySQL 8.0.29 之前不支持 ADD COLUMN IF NOT EXISTS，改用 information_schema 判定
SET @study_ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME   = 'study_error_pool'
         AND COLUMN_NAME = 'child_id') = 0,
    'ALTER TABLE `study_error_pool` ADD COLUMN `child_id` varchar(32) DEFAULT NULL COMMENT ''所属孩子 study_child.id(多用户隔离)'' AFTER `id`',
    'SELECT 1'
  )
);
PREPARE study_stmt FROM @study_ddl;
EXECUTE study_stmt;
DEALLOCATE PREPARE study_stmt;

-- 幂等加索引：先查 information_schema.STATISTICS 是否已存在
SET @study_ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME   = 'study_error_pool'
         AND INDEX_NAME   = 'idx_error_child') = 0,
    'ALTER TABLE `study_error_pool` ADD KEY `idx_error_child` (`child_id`, `del_flag`)',
    'SELECT 1'
  )
);
PREPARE study_stmt FROM @study_ddl;
EXECUTE study_stmt;
DEALLOCATE PREPARE study_stmt;

-- 幂等加列：MySQL 8.0.29 之前不支持 ADD COLUMN IF NOT EXISTS，改用 information_schema 判定
SET @study_ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME   = 'study_review_schedule'
         AND COLUMN_NAME = 'child_id') = 0,
    'ALTER TABLE `study_review_schedule` ADD COLUMN `child_id` varchar(32) DEFAULT NULL COMMENT ''所属孩子 study_child.id(多用户隔离)'' AFTER `id`',
    'SELECT 1'
  )
);
PREPARE study_stmt FROM @study_ddl;
EXECUTE study_stmt;
DEALLOCATE PREPARE study_stmt;

-- 幂等加索引：先查 information_schema.STATISTICS 是否已存在
SET @study_ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME   = 'study_review_schedule'
         AND INDEX_NAME   = 'idx_schedule_child') = 0,
    'ALTER TABLE `study_review_schedule` ADD KEY `idx_schedule_child` (`child_id`, `del_flag`)',
    'SELECT 1'
  )
);
PREPARE study_stmt FROM @study_ddl;
EXECUTE study_stmt;
DEALLOCATE PREPARE study_stmt;

-- 幂等加列：MySQL 8.0.29 之前不支持 ADD COLUMN IF NOT EXISTS，改用 information_schema 判定
SET @study_ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME   = 'study_question_variant'
         AND COLUMN_NAME = 'child_id') = 0,
    'ALTER TABLE `study_question_variant` ADD COLUMN `child_id` varchar(32) DEFAULT NULL COMMENT ''所属孩子 study_child.id(多用户隔离)'' AFTER `id`',
    'SELECT 1'
  )
);
PREPARE study_stmt FROM @study_ddl;
EXECUTE study_stmt;
DEALLOCATE PREPARE study_stmt;

-- 幂等加索引：先查 information_schema.STATISTICS 是否已存在
SET @study_ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME   = 'study_question_variant'
         AND INDEX_NAME   = 'idx_variant_child') = 0,
    'ALTER TABLE `study_question_variant` ADD KEY `idx_variant_child` (`child_id`, `del_flag`)',
    'SELECT 1'
  )
);
PREPARE study_stmt FROM @study_ddl;
EXECUTE study_stmt;
DEALLOCATE PREPARE study_stmt;

-- 幂等加列：MySQL 8.0.29 之前不支持 ADD COLUMN IF NOT EXISTS，改用 information_schema 判定
SET @study_ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME   = 'study_writing'
         AND COLUMN_NAME = 'child_id') = 0,
    'ALTER TABLE `study_writing` ADD COLUMN `child_id` varchar(32) DEFAULT NULL COMMENT ''所属孩子 study_child.id(多用户隔离)'' AFTER `id`',
    'SELECT 1'
  )
);
PREPARE study_stmt FROM @study_ddl;
EXECUTE study_stmt;
DEALLOCATE PREPARE study_stmt;

-- 幂等加索引：先查 information_schema.STATISTICS 是否已存在
SET @study_ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME   = 'study_writing'
         AND INDEX_NAME   = 'idx_writing_child') = 0,
    'ALTER TABLE `study_writing` ADD KEY `idx_writing_child` (`child_id`, `del_flag`)',
    'SELECT 1'
  )
);
PREPARE study_stmt FROM @study_ddl;
EXECUTE study_stmt;
DEALLOCATE PREPARE study_stmt;

-- 幂等加列：MySQL 8.0.29 之前不支持 ADD COLUMN IF NOT EXISTS，改用 information_schema 判定
SET @study_ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME   = 'study_practice_record_202610'
         AND COLUMN_NAME = 'child_id') = 0,
    'ALTER TABLE `study_practice_record_202610` ADD COLUMN `child_id` varchar(32) DEFAULT NULL COMMENT ''所属孩子 study_child.id(多用户隔离)'' AFTER `id`',
    'SELECT 1'
  )
);
PREPARE study_stmt FROM @study_ddl;
EXECUTE study_stmt;
DEALLOCATE PREPARE study_stmt;

-- 幂等加索引：先查 information_schema.STATISTICS 是否已存在
SET @study_ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME   = 'study_practice_record_202610'
         AND INDEX_NAME   = 'idx_record_child') = 0,
    'ALTER TABLE `study_practice_record_202610` ADD KEY `idx_record_child` (`child_id`, `del_flag`)',
    'SELECT 1'
  )
);
PREPARE study_stmt FROM @study_ddl;
EXECUTE study_stmt;
DEALLOCATE PREPARE study_stmt;

-- 幂等加列：MySQL 8.0.29 之前不支持 ADD COLUMN IF NOT EXISTS，改用 information_schema 判定
SET @study_ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME   = 'study_eye_usage'
         AND COLUMN_NAME = 'child_id') = 0,
    'ALTER TABLE `study_eye_usage` ADD COLUMN `child_id` varchar(32) DEFAULT NULL COMMENT ''所属孩子 study_child.id(多用户隔离)'' AFTER `id`',
    'SELECT 1'
  )
);
PREPARE study_stmt FROM @study_ddl;
EXECUTE study_stmt;
DEALLOCATE PREPARE study_stmt;

-- 幂等加索引：先查 information_schema.STATISTICS 是否已存在
SET @study_ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME   = 'study_eye_usage'
         AND INDEX_NAME   = 'idx_eye_child') = 0,
    'ALTER TABLE `study_eye_usage` ADD KEY `idx_eye_child` (`child_id`, `del_flag`)',
    'SELECT 1'
  )
);
PREPARE study_stmt FROM @study_ddl;
EXECUTE study_stmt;
DEALLOCATE PREPARE study_stmt;

