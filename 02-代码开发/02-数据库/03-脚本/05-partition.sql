-- ============================================================
-- jeecg-study · 分表脚本 partition.sql
-- 由 05-公共资源\2026103017-DDL提取.py 从《数据库设计说明书》源稿生成
-- 目标库：jeecg-study（MySQL 8）· 表前缀 study_
-- 幂等：建表用 IF NOT EXISTS；改结构用 information_schema 判定后动态执行
-- ============================================================

USE `jeecg-study`;

-- 每月 1 日由定时任务执行（幂等）
SET @table = CONCAT('study_practice_record_', DATE_FORMAT(NOW(), '%Y%m'));

SET @ddl = CONCAT(
  'CREATE TABLE IF NOT EXISTS `', @table, '` (',
  '  `id` varchar(32) NOT NULL COMMENT ''主键'',',
  '  `child_id` varchar(32) DEFAULT NULL COMMENT ''所属孩子 study_child.id(多用户隔离)'',',
  '  `context_id` varchar(32) NOT NULL COMMENT ''全局上下文'',',
  '  `source_module` varchar(16) NOT NULL COMMENT ''来源模块'',',
  '  `content_type` varchar(24) NOT NULL COMMENT ''内容类型'',',
  '  `content_id` varchar(32) NOT NULL COMMENT ''内容业务ID'',',
  '  `knowledge_point_code` varchar(32) DEFAULT NULL COMMENT ''考点业务ID'',',
  '  `answer` varchar(1024) DEFAULT NULL COMMENT ''孩子作答'',',
  '  `correct` tinyint NOT NULL COMMENT ''是否正确'',',
  '  `judge_source` varchar(16) NOT NULL DEFAULT ''rule'' COMMENT ''判定来源'',',
  '  `error_cause` varchar(16) DEFAULT NULL COMMENT ''错因'',',
  '  `error_code` varchar(32) DEFAULT NULL COMMENT ''错题业务ID'',',
  '  `duration_ms` int DEFAULT NULL COMMENT ''耗时毫秒'',',
  '  `step_tags` json DEFAULT NULL COMMENT ''步骤错因打标'',',
  '  `practice_date` date NOT NULL COMMENT ''练习日期'',',
  '  `create_by` varchar(50) DEFAULT NULL COMMENT ''创建人'',',
  '  `create_time` datetime DEFAULT NULL COMMENT ''创建时间'',',
  '  `update_by` varchar(50) DEFAULT NULL COMMENT ''更新人'',',
  '  `update_time` datetime DEFAULT NULL COMMENT ''更新时间'',',
  '  `del_flag` tinyint NOT NULL DEFAULT 0 COMMENT ''删除标记 0正常 1删除'',',
  '  PRIMARY KEY (`id`),',
  '  KEY `idx_record_child` (`child_id`, `del_flag`),',
  '  KEY `idx_pr_date` (`practice_date`, `source_module`),',
  '  KEY `idx_pr_content` (`content_type`, `content_id`),',
  '  KEY `idx_pr_kp` (`knowledge_point_code`),',
  '  KEY `idx_pr_error` (`error_code`)',
  ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''作答记录(按月分表)'';'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

