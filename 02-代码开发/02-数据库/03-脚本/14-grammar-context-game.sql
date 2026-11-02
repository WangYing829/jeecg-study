-- ============================================================
-- jeecg-study · 语法语境库 + 趣味玩法 增量脚本 grammar-context-game.sql
-- 需求：F-GRM-05 语境库复用 / F-GRM-04 趣味玩法
-- 目标库：jeecg-study（MySQL 8）· 表前缀 study_
--
-- 作用：新建 4 张表 + 灌入首批语境与玩法种子
--
-- 变更方式（铁律 2）：**变更走增量脚本，不改历史脚本**。
--       02-schema.sql / 03-init.sql / 04-fix.sql / 05-partition.sql / 06-cleanup.sql
--       均为已登记的历史产物，本脚本**不触碰**它们。
--
-- 幂等：建表用 CREATE TABLE IF NOT EXISTS；插数据用 INSERT ... ON DUPLICATE KEY UPDATE
--       （唯一键 uk_sentence_code / uk_wc / uk_gq_code 兜底，重复执行只更新不重复插）
--
-- 数据范围：3 张内容表（语境库 / 关联表 / 玩法题库）**没有 child_id**；
--       只有 study_grammar_game_record 是行为表，child_id 必填。
--
-- 内容口径：
--   1) 主例句（TEXTBOOK）直接取背单词域 study_word.context_sentence ——
--      同一批例句，不是复制一份；zh_meaning 取词条释义。
--   2) 真题（EXAM）与原声素材（ORIGINAL）两类语境各一条自编例句，
--      带句子级中文意思，供语法变形闯关取题。
--   3) 玩法题库只存组合关系：正例句指向语境库业务ID，
--      只有句子侦探的错句是玩法自己的题目素材（服务端专用，取题不下发）。
--   4) 14 句错句逐句对过规则库：每句被且仅被一条确定性规则命中。
--
-- 版权：全部自编（copyright_type=SELF），不含真实个人信息。
--
-- 回滚：不删表（铁律 4）。如需清空种子数据，执行 15-grammar-context-game-cleanup.sql。
--
-- 迁移留痕：执行本脚本后，按《数据库设计说明书》§12.4 在 `study_schema_migration`
--       登记一条记录（脚本名 + 本文件内容的 SHA-256 + 执行时间 + 执行人）。
-- ============================================================

USE `jeecg-study`;

-- ------------------------------------------------------------------
-- 建表：4 张新表
--   study_sentence_context       例句语境库（内容表，无 child_id）
--   study_word_context           词条 ↔ 例句关联（内容表，无 child_id）
--   study_grammar_game_question  语法玩法题库（内容表，无 child_id）
--   study_grammar_game_record    语法玩法作答记录（行为表，必带 child_id）
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `study_sentence_context` (
  `id`             varchar(32) NOT NULL COMMENT '主键',
  `sentence_code`  varchar(32) NOT NULL COMMENT '业务ID SCT-{序号}',
  `sentence`       text NOT NULL COMMENT '例句正文(唯一语料)',
  `zh_meaning`     varchar(256) DEFAULT NULL COMMENT '中文意思',
  `context_type`   varchar(16) NOT NULL COMMENT '语境类型 TEXTBOOK课文/EXAM真题/ORIGINAL原声素材',
  `word_code`      varchar(32) DEFAULT NULL COMMENT '关联词条业务ID',
  `grammar_code`   varchar(32) DEFAULT NULL COMMENT '关联语法点业务ID',
  `usage_note`     varchar(256) DEFAULT NULL COMMENT '用法说明(同一词在不同语境下的差别)',
  `source_ref`     varchar(256) DEFAULT NULL COMMENT '出处',
  `copyright_type` varchar(16) NOT NULL DEFAULT 'SELF' COMMENT '版权类型 SELF/PUBLIC/LICENSE',
  `status`         tinyint NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
  `del_flag`       tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_by`      varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time`    datetime DEFAULT NULL COMMENT '创建时间',
  `update_by`      varchar(50) DEFAULT NULL COMMENT '更新人',
  `update_time`    datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sentence_code` (`sentence_code`),
  KEY `idx_sc_word` (`word_code`, `context_type`, `status`),
  KEY `idx_sc_grammar` (`grammar_code`, `status`),
  KEY `idx_sc_type` (`context_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='例句语境库(背单词与语法共用,内容表全局共享)';

CREATE TABLE IF NOT EXISTS `study_word_context` (
  `id`            varchar(32) NOT NULL COMMENT '主键',
  `word_code`     varchar(32) NOT NULL COMMENT '词条业务ID',
  `sentence_code` varchar(32) NOT NULL COMMENT '例句业务ID',
  `relation_type` varchar(16) NOT NULL DEFAULT 'MAIN' COMMENT '关联类型 MAIN主例句/EXTEND拓展用法',
  `sort_no`       int NOT NULL DEFAULT 1 COMMENT '展示顺序',
  `status`        tinyint NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
  `del_flag`      tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_by`     varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time`   datetime DEFAULT NULL COMMENT '创建时间',
  `update_by`     varchar(50) DEFAULT NULL COMMENT '更新人',
  `update_time`   datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wc` (`word_code`, `sentence_code`),
  KEY `idx_wc_word` (`word_code`, `relation_type`, `sort_no`),
  KEY `idx_wc_sentence` (`sentence_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='词条与例句关联(同一批例句的连接处)';

CREATE TABLE IF NOT EXISTS `study_grammar_game_question` (
  `id`                   varchar(32) NOT NULL COMMENT '主键',
  `question_code`        varchar(32) NOT NULL COMMENT '业务ID GAM-{序号}',
  `game_type`            varchar(16) NOT NULL COMMENT '玩法类型 DETECTIVE句子侦探/TRANSFORM变形闯关',
  `grammar_code`         varchar(32) DEFAULT NULL COMMENT '关联语法点业务ID',
  `sentence_code`        varchar(32) DEFAULT NULL COMMENT '目标表达所在的语境例句业务ID',
  `clean_sentence_codes` json DEFAULT NULL COMMENT '正例句业务ID列表JSON(指向语境库)',
  `wrong_sentence`       text DEFAULT NULL COMMENT '这一关的错句(服务端专用,取题接口不下发)',
  `wrong_position`       tinyint DEFAULT NULL COMMENT '错句在第几句(1起)',
  `difficulty`           tinyint NOT NULL DEFAULT 1 COMMENT '难度 1起',
  `judge_source`         varchar(16) NOT NULL DEFAULT 'rule' COMMENT '判定来源(恒为rule)',
  `status`               tinyint NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
  `del_flag`             tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_by`            varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time`          datetime DEFAULT NULL COMMENT '创建时间',
  `update_by`            varchar(50) DEFAULT NULL COMMENT '更新人',
  `update_time`          datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gq_code` (`question_code`),
  KEY `idx_gq_type` (`game_type`, `status`, `difficulty`),
  KEY `idx_gq_grammar` (`grammar_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='语法玩法题库(只存组合关系,内容表全局共享)';

CREATE TABLE IF NOT EXISTS `study_grammar_game_record` (
  `id`            varchar(32) NOT NULL COMMENT '主键',
  `child_id`      varchar(32) NOT NULL COMMENT '所属孩子 study_child.id(多用户隔离)',
  `game_type`     varchar(16) NOT NULL COMMENT '玩法类型 DETECTIVE/TRANSFORM',
  `question_code` varchar(32) NOT NULL COMMENT '题目业务ID',
  `grammar_code`  varchar(32) DEFAULT NULL COMMENT '关联语法点业务ID',
  `submitted`     varchar(512) DEFAULT NULL COMMENT '孩子的作答',
  `correct`       tinyint NOT NULL COMMENT '是否通过 1是 0否',
  `judge_source`  varchar(16) NOT NULL DEFAULT 'rule' COMMENT '判定来源(恒为rule)',
  `error_cause`   varchar(16) DEFAULT NULL COMMENT '错因',
  `error_code`    varchar(32) DEFAULT NULL COMMENT '统一错题池里的错题业务ID',
  `duration_ms`   int DEFAULT NULL COMMENT '本关耗时(毫秒)',
  `play_date`     date NOT NULL COMMENT '作答日期',
  `del_flag`      tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  `create_by`     varchar(50) DEFAULT NULL COMMENT '创建人',
  `create_time`   datetime DEFAULT NULL COMMENT '创建时间',
  `update_by`     varchar(50) DEFAULT NULL COMMENT '更新人',
  `update_time`   datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ggr_child` (`child_id`, `play_date`),
  KEY `idx_ggr_question` (`child_id`, `question_code`),
  KEY `idx_ggr_type` (`game_type`, `play_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='语法玩法作答记录(行为表,按child_id隔离)';

-- ------------------------------------------------------------
-- 1. 主例句（课文语境）：正文与释义直接取背单词域，同一批例句
-- ------------------------------------------------------------
INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`)
SELECT CONCAT('SEED-SCT-', SUBSTRING(w.`word_code`, 5)),
       CONCAT('SCT-', SUBSTRING(w.`word_code`, 5)),
       w.`context_sentence`,
       w.`meaning`,
       'TEXTBOOK',
       w.`word_code`,
       NULL,
       '课文里的主例句，与背单词域共用同一句',
       '课内例句（自编）',
       'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()
FROM `study_word` w
WHERE w.`word_code` LIKE 'WRD-%' AND w.`del_flag` = 0
  AND w.`context_sentence` IS NOT NULL AND w.`context_sentence` <> ''
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

-- ------------------------------------------------------------
-- 2. 真题语境（EXAM）与原声素材语境（ORIGINAL）：自编例句 + 句子级中文意思
-- ------------------------------------------------------------
INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000101', 'SCT-000101', 'The doctor asked me to eat an apple every morning.',
 '医生让我每天早上吃一个苹果。', 'EXAM', 'WRD-000001', NULL,
 '真题里 apple 常出现在健康建议语境，作可数名词用单数，前面的不定冠词用 an。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000201', 'SCT-000201', 'I bite into this apple every single morning.',
 '我每天早上都要咬一口这个苹果。', 'ORIGINAL', 'WRD-000001', NULL,
 '日常口语里 apple 更随意，常用 bite into 这类动词。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000102', 'SCT-000102', 'The monkey likes bananas better than apples.',
 '猴子更喜欢香蕉，而不是苹果。', 'EXAM', 'WRD-000002', NULL,
 '真题里常用 better than 引出比较对象。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000202', 'SCT-000202', 'I usually grab a banana on my way to school.',
 '我去学校的路上通常会拿一根香蕉。', 'ORIGINAL', 'WRD-000002', NULL,
 '口语里 grab 表示「顺手拿」，比 take 更生活化。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000103', 'SCT-000103', 'This orange is sweet, but that one is sour.',
 '这个橙子甜，但那个很酸。', 'EXAM', 'WRD-000003', NULL,
 '真题里用 but 引导两个并列的对比句。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000203', 'SCT-000203', 'My grandma squeezes an orange for me every morning.',
 '我奶奶每天早上给我榨一个橙子。', 'ORIGINAL', 'WRD-000003', NULL,
 '口语里 squeeze 表示「挤汁」，是日常动作。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000104', 'SCT-000104', 'She wants a pear because she likes sweet fruit.',
 '她想要一个梨，因为她喜欢吃甜水果。', 'EXAM', 'WRD-000004', NULL,
 '真题里 because 引出原因，是最常见的因果句式。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000204', 'SCT-000204', 'I bit into a pear this morning and it was juicy.',
 '今天早上我咬了一口梨，水分很足。', 'ORIGINAL', 'WRD-000004', NULL,
 '口语里 bit into 描述「咬一口尝尝」。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000105', 'SCT-000105', 'Grapes grow on the vine in summer.',
 '葡萄在夏天长在藤上。', 'EXAM', 'WRD-000005', NULL,
 '真题里 in summer 作时间状语，说明季节条件。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000205', 'SCT-000205', 'We pick grapes together every autumn.',
 '我们每年秋天一起摘葡萄。', 'ORIGINAL', 'WRD-000005', NULL,
 '口语里 pick 兼有「采摘」与「挑选」两义。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000106', 'SCT-000106', 'The peach is very juicy, so I want to eat it now.',
 '这个桃子很多汁，所以我想现在就吃掉。', 'EXAM', 'WRD-000006', NULL,
 '真题里 so 引导结果，与 because 的用法正好相反。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000206', 'SCT-000206', 'I washed the peach and put it in my bag.',
 '我把桃子洗了，放进了书包里。', 'ORIGINAL', 'WRD-000006', NULL,
 '口语里 put ... in ... 是「放进某处」的高频结构。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000107', 'SCT-000107', 'This lemon is sour, but I still want to try it.',
 '这个柠檬很酸，但我还是想试试。', 'EXAM', 'WRD-000007', NULL,
 '真题里 but 之后加 still 表示「尽管如此仍然」。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000207', 'SCT-000207', 'I put a lemon slice in my water every day.',
 '我每天都在水里放一片柠檬。', 'ORIGINAL', 'WRD-000007', NULL,
 '口语里 slice 是「一片」，a slice of 是固定搭配。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000108', 'SCT-000108', 'We buy a melon in summer because it is cheap.',
 '我们夏天买瓜，因为它便宜。', 'EXAM', 'WRD-000008', NULL,
 '真题里 because 引出买瓜的原因。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000208', 'SCT-000208', 'My dad cuts the melon into small pieces for us.',
 '我爸把瓜切成小块给我们吃。', 'ORIGINAL', 'WRD-000008', NULL,
 '口语里 cut ... into ... 表示「切成若干块」。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000109', 'SCT-000109', 'This pencil is new, so I have not used it yet.',
 '这支铅笔是新的，所以我还没用过。', 'EXAM', 'WRD-000009', NULL,
 '真题里 not ... yet 表示「尚未」，是现在完成时的常见考点。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000209', 'SCT-000209', 'I always keep a pencil in my pencil case.',
 '我总在铅笔盒里放一支铅笔。', 'ORIGINAL', 'WRD-000009', NULL,
 '口语里 keep 表示「习惯性地放在某处」。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000110', 'SCT-000110', 'I write with a pen instead of a pencil.',
 '我用钢笔写，而不是用铅笔。', 'EXAM', 'WRD-000010', NULL,
 '真题里 instead of 表示「而不是」，注意后面接名词或动名词。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000210', 'SCT-000210', 'Can you pass me your pen? I cannot find mine.',
 '你能把你的笔递给我吗？我找不到我的了。', 'ORIGINAL', 'WRD-000010', NULL,
 '口语里 pass me 是「递给我」，mine 代替 my pen。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000111', 'SCT-000111', 'Draw a line with the ruler, and then cut the paper.',
 '用尺子画一条线，然后把纸剪开。', 'EXAM', 'WRD-000011', NULL,
 '真题里 and then 表示动作先后顺序。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000211', 'SCT-000211', 'My brother lost his ruler again yesterday.',
 '我弟弟昨天又把尺子弄丢了。', 'ORIGINAL', 'WRD-000011', NULL,
 '口语里 again 表示「又一次」，带出无奈的语气。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000112', 'SCT-000112', 'Where is my eraser? I need it for the maths test.',
 '我的橡皮在哪里？我数学考试要用它。', 'EXAM', 'WRD-000012', NULL,
 '真题里 for 表示用途，是介词用法的常考点。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000212', 'SCT-000212', 'There is an eraser under the book, I think.',
 '我想橡皮应该在书下面。', 'ORIGINAL', 'WRD-000012', NULL,
 '口语里 I think 放句尾，表示「我猜」，语气更软。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000113', 'SCT-000113', 'This book is about animals, and it has many pictures.',
 '这本书讲的是动物，里面有很多图片。', 'EXAM', 'WRD-000013', NULL,
 '真题里 be about 表示「关于」，and 连接两个并列信息。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000213', 'SCT-000213', 'I read a book before I go to bed every night.',
 '我每天睡前都会读一本书。', 'ORIGINAL', 'WRD-000013', NULL,
 '口语里 before I go to bed 是「睡前」最自然的说法。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000114', 'SCT-000114', 'My bag is blue, and my friend''s bag is red.',
 '我的书包是蓝色的，我朋友的书包是红色的。', 'EXAM', 'WRD-000014', NULL,
 '真题里 one''s 代替重复的名词，避免啰嗦。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000214', 'SCT-000214', 'I put my lunch in the bag before I leave.',
 '出门前我把午饭放进书包。', 'ORIGINAL', 'WRD-000014', NULL,
 '口语里 before I leave 表示「出门之前」。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000115', 'SCT-000115', 'The book is on the desk, next to the computer.',
 '书在桌子上，电脑的旁边。', 'EXAM', 'WRD-000015', NULL,
 '真题里 next to 表示「紧挨着」，是方位介词考点。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000215', 'SCT-000215', 'I always keep my desk tidy before a test.',
 '考试前我总会把课桌收拾干净。', 'ORIGINAL', 'WRD-000015', NULL,
 '口语里 keep ... tidy 表示「保持整洁」。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000116', 'SCT-000116', 'Sit on the chair, please, and wait for the doctor.',
 '请坐在椅子上，等医生来。', 'EXAM', 'WRD-000016', NULL,
 '真题里祈使句 + please 表示请求，and 连接两个动作。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000216', 'SCT-000216', 'Pull the chair a little closer to the desk.',
 '把椅子往桌子那边挪近一点。', 'ORIGINAL', 'WRD-000016', NULL,
 '口语里 a little 表示「一点点」，是程度副词。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000117', 'SCT-000117', 'Our teacher is very kind, and she helps us a lot.',
 '我们老师很和善，帮了我们很多。', 'EXAM', 'WRD-000017', NULL,
 '真题里 and 连接两个好词，是并列结构的基本考法。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000217', 'SCT-000217', 'My teacher told me to read the text again.',
 '老师让我再读一遍课文。', 'ORIGINAL', 'WRD-000017', NULL,
 '口语里 tell sb to do 是「让某人做某事」。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000118', 'SCT-000118', 'I am a student, and I study English every day.',
 '我是一名学生，每天都学英语。', 'EXAM', 'WRD-000018', NULL,
 '真题里 I am a ... 引出身份，后面再补一个并列动作。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000218', 'SCT-000218', 'As a student, I walk to school every day.',
 '作为一名学生，我每天走路上学。', 'ORIGINAL', 'WRD-000018', NULL,
 '口语里 As a student 放句首，先亮身份再讲习惯。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000119', 'SCT-000119', 'The doctor helps sick people in our community.',
 '这位医生帮助我们社区的病人。', 'EXAM', 'WRD-000019', NULL,
 '真题里 in our community 表示范围，是介词考点。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000219', 'SCT-000219', 'You look tired. You should see a doctor.',
 '你看起来很累，该去看医生了。', 'ORIGINAL', 'WRD-000019', NULL,
 '口语里 You should ... 是给建议最常用的说法。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000120', 'SCT-000120', 'The nurse works at night in the big hospital.',
 '这位护士夜里在那家大医院上班。', 'EXAM', 'WRD-000020', NULL,
 '真题里 at night 作时间状语，in the big hospital 作地点状语。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000220', 'SCT-000220', 'The nurse told me to take the medicine after dinner.',
 '护士让我饭后吃药。', 'ORIGINAL', 'WRD-000020', NULL,
 '口语里 after dinner 是「饭后」的口语说法。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000121', 'SCT-000121', 'The driver drives a bus for ten years.',
 '这位司机开公交已经十年了。', 'EXAM', 'WRD-000021', NULL,
 '真题里 for + 一段时间，表示持续多久。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000221', 'SCT-000221', 'The driver waited for me when it started to rain.',
 '下雨的时候，司机在等我。', 'ORIGINAL', 'WRD-000021', NULL,
 '口语里 wait for sb 是「等某人」，后面直接跟人。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000122', 'SCT-000122', 'The farmer works on the farm from morning to night.',
 '农民从早到晚在农场干活。', 'EXAM', 'WRD-000022', NULL,
 '真题里 from ... to ... 表示「从……到……」。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000222', 'SCT-000222', 'The farmer gave us fresh milk this morning.',
 '农民今天早上给了我们新鲜牛奶。', 'ORIGINAL', 'WRD-000022', NULL,
 '口语里 give sb sth 表示「给某人某物」。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000123', 'SCT-000123', 'The worker builds houses with his team.',
 '这名工人和工友一起盖房子。', 'EXAM', 'WRD-000023', NULL,
 '真题里 with 表示「和……一起」。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000223', 'SCT-000223', 'My uncle is a worker in a small factory.',
 '我叔叔是一家小工厂的工人。', 'ORIGINAL', 'WRD-000023', NULL,
 '口语里 a small factory 用来补足职业的具体场景。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000124', 'SCT-000124', 'My uncle is a cook in a busy restaurant.',
 '我叔叔是一家忙碌餐厅的厨师。', 'EXAM', 'WRD-000024', NULL,
 '真题里 a cook 表示职业身份，in a ... restaurant 表示工作场所。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000224', 'SCT-000224', 'My dad can cook, and he cooks noodles very well.',
 '我爸爸会做饭，而且面条做得很好。', 'ORIGINAL', 'WRD-000024', NULL,
 '口语里 cook 既可当动词也可当名词，这里是一词两用。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000125', 'SCT-000125', 'We read books in the library every weekend.',
 '我们每个周末都在图书馆看书。', 'EXAM', 'WRD-000025', NULL,
 '真题里 every weekend 表示频率，是时间状语的常考点。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000225', 'SCT-000225', 'The library is quiet, so I do my homework there.',
 '图书馆很安静，所以我在那儿写作业。', 'ORIGINAL', 'WRD-000025', NULL,
 '口语里 do one''s homework 是「做作业」的固定说法。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000126', 'SCT-000126', 'The weather is fine today, so we have a class outside.',
 '今天天气很好，所以我们在外面上课。', 'EXAM', 'WRD-000026', NULL,
 '真题里 so 引出结果，是因果句型的另一半。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000226', 'SCT-000226', 'The weather changed suddenly and the game was cancelled.',
 '天气突然变了，比赛取消了。', 'ORIGINAL', 'WRD-000026', NULL,
 '口语里 was cancelled 表示「被取消」，是被动语态。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000127', 'SCT-000127', 'Please open the window, because the room is too hot.',
 '请打开窗户，因为房间太热了。', 'EXAM', 'WRD-000027', NULL,
 '真题里 too + 形容词表示「太……了」。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000227', 'SCT-000227', 'I opened the window and felt the cold air.',
 '我打开窗户，感到一阵凉风。', 'ORIGINAL', 'WRD-000027', NULL,
 '口语里 feel the cold air 表示身体感受。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000128', 'SCT-000128', 'There are many flowers in the garden behind our school.',
 '我们学校后面的花园里有许多花。', 'EXAM', 'WRD-000028', NULL,
 '真题里 many + 复数名词，There are 与复数一致。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000228', 'SCT-000228', 'My mum waters the garden every evening.',
 '我妈妈每天傍晚给花园浇水。', 'ORIGINAL', 'WRD-000028', NULL,
 '口语里 water 作动词表示「浇水」，watering 更日常。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000129', 'SCT-000129', 'The hospital is near our school and it is clean.',
 '医院在我们学校附近，而且很干净。', 'EXAM', 'WRD-000029', NULL,
 '真题里 near 表示「靠近」，and 连接两个谓语。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000229', 'SCT-000229', 'We went to the hospital to see my grandma.',
 '我们去医院看了奶奶。', 'ORIGINAL', 'WRD-000029', NULL,
 '口语里 go to the hospital to see sb 是自然的表达。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000130', 'SCT-000130', 'We visited a museum last Sunday and learned a lot.',
 '上周日我们参观了博物馆，学到了很多。', 'EXAM', 'WRD-000030', NULL,
 '真题里 last Sunday 是过去的时间状语，learn a lot 是结果。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000230', 'SCT-000230', 'The museum has a big model of our city.',
 '博物馆里有一个我们城市的大模型。', 'ORIGINAL', 'WRD-000030', NULL,
 '口语里 a model of ... 表示「……的模型」。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000131', 'SCT-000131', 'The station is next to the park, so it is easy to find.',
 '车站在公园旁边，所以很好找。', 'EXAM', 'WRD-000031', NULL,
 '真题里 so 与 because 配对出现，考点常在因果方向。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000231', 'SCT-000231', 'Meet me at the station at eight.',
 '八点在车站见我。', 'ORIGINAL', 'WRD-000031', NULL,
 '口语里 Meet me at ... 是约时间的省略说法。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000132', 'SCT-000132', 'My mother goes to the market and buys fresh vegetables.',
 '我妈妈去市场买新鲜蔬菜。', 'EXAM', 'WRD-000032', NULL,
 '真题里 and 连接两个谓语，主语相同可以共用。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000232', 'SCT-000232', 'The market is full of people on Sunday morning.',
 '周日上午市场里挤满了人。', 'ORIGINAL', 'WRD-000032', NULL,
 '口语里 be full of 表示「挤满了……」。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000133', 'SCT-000133', 'We had a long holiday and visited our grandparents.',
 '我们放了个长假，还去看了爷爷奶奶。', 'EXAM', 'WRD-000033', NULL,
 '真题里 have a holiday 表示「放假」，是固定搭配。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000233', 'SCT-000233', 'I read three books during the holiday.',
 '假期里我读了三本书。', 'ORIGINAL', 'WRD-000033', NULL,
 '口语里 during the holiday 表示「假期期间」。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000134', 'SCT-000134', 'I read books on the weekend and play football on Saturday.',
 '我周末看书，周六踢足球。', 'EXAM', 'WRD-000034', NULL,
 '真题里 on the weekend 与 on Saturday 的区别是常考点。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000234', 'SCT-000234', 'What do you usually do on the weekend?',
 '你周末通常做什么？', 'ORIGINAL', 'WRD-000034', NULL,
 '口语里 usually 与 often 的用法差别在这里体现。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000135', 'SCT-000135', 'The Spring Festival is my favourite festival of the year.',
 '春节是我一年里最喜欢的节日。', 'EXAM', 'WRD-000035', NULL,
 '真题里 one of the ... 与 the most ... 的区别是考点。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000235', 'SCT-000235', 'The festival in our town has a big dragon dance.',
 '我们镇上的节日有舞龙表演。', 'ORIGINAL', 'WRD-000035', NULL,
 '口语里 in our town 表示「在我们镇上」。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_sentence_context`
(`id`, `sentence_code`, `sentence`, `zh_meaning`, `context_type`, `word_code`,
 `grammar_code`, `usage_note`, `source_ref`, `copyright_type`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SCT-000136', 'SCT-000136', 'I got a present on my birthday and it was a book.',
 '我生日时收到一份礼物，是一本书。', 'EXAM', 'WRD-000036', NULL,
 '真题里 got 是 get 的过去式，on one''s birthday 是时间状语。', '中考真题风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SCT-000236', 'SCT-000236', 'She made a present for her teacher by hand.',
 '她手工给老师做了一份礼物。', 'ORIGINAL', 'WRD-000036', NULL,
 '口语里 by hand 表示「手工」，与 by machine 相对。', '原声素材风格改写（自编）', 'SELF', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence` = VALUES(`sentence`),
  `zh_meaning` = VALUES(`zh_meaning`),
  `usage_note` = VALUES(`usage_note`),
  `source_ref` = VALUES(`source_ref`),
  `update_by` = 'admin', `update_time` = NOW();

-- ------------------------------------------------------------
-- 3. 词条 ↔ 例句关联：MAIN 指向主例句，EXTEND 指向真题句与原声句
--    这张表就是「同一批例句」的连接处
-- ------------------------------------------------------------
INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`)
SELECT CONCAT('SEED-WCT-', SUBSTRING(w.`word_code`, 5)),
       w.`word_code`,
       CONCAT('SCT-', SUBSTRING(w.`word_code`, 5)),
       'MAIN', 1, 1, 0, 'admin', NOW(), 'admin', NOW()
FROM `study_word` w
WHERE w.`word_code` LIKE 'WRD-%' AND w.`del_flag` = 0
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000101', 'WRD-000001', 'SCT-000101', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000201', 'WRD-000001', 'SCT-000201', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000102', 'WRD-000002', 'SCT-000102', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000202', 'WRD-000002', 'SCT-000202', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000103', 'WRD-000003', 'SCT-000103', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000203', 'WRD-000003', 'SCT-000203', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000104', 'WRD-000004', 'SCT-000104', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000204', 'WRD-000004', 'SCT-000204', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000105', 'WRD-000005', 'SCT-000105', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000205', 'WRD-000005', 'SCT-000205', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000106', 'WRD-000006', 'SCT-000106', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000206', 'WRD-000006', 'SCT-000206', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000107', 'WRD-000007', 'SCT-000107', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000207', 'WRD-000007', 'SCT-000207', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000108', 'WRD-000008', 'SCT-000108', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000208', 'WRD-000008', 'SCT-000208', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000109', 'WRD-000009', 'SCT-000109', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000209', 'WRD-000009', 'SCT-000209', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000110', 'WRD-000010', 'SCT-000110', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000210', 'WRD-000010', 'SCT-000210', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000111', 'WRD-000011', 'SCT-000111', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000211', 'WRD-000011', 'SCT-000211', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000112', 'WRD-000012', 'SCT-000112', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000212', 'WRD-000012', 'SCT-000212', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000113', 'WRD-000013', 'SCT-000113', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000213', 'WRD-000013', 'SCT-000213', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000114', 'WRD-000014', 'SCT-000114', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000214', 'WRD-000014', 'SCT-000214', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000115', 'WRD-000015', 'SCT-000115', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000215', 'WRD-000015', 'SCT-000215', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000116', 'WRD-000016', 'SCT-000116', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000216', 'WRD-000016', 'SCT-000216', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000117', 'WRD-000017', 'SCT-000117', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000217', 'WRD-000017', 'SCT-000217', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000118', 'WRD-000018', 'SCT-000118', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000218', 'WRD-000018', 'SCT-000218', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000119', 'WRD-000019', 'SCT-000119', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000219', 'WRD-000019', 'SCT-000219', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000120', 'WRD-000020', 'SCT-000120', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000220', 'WRD-000020', 'SCT-000220', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000121', 'WRD-000021', 'SCT-000121', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000221', 'WRD-000021', 'SCT-000221', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000122', 'WRD-000022', 'SCT-000122', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000222', 'WRD-000022', 'SCT-000222', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000123', 'WRD-000023', 'SCT-000123', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000223', 'WRD-000023', 'SCT-000223', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000124', 'WRD-000024', 'SCT-000124', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000224', 'WRD-000024', 'SCT-000224', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000125', 'WRD-000025', 'SCT-000125', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000225', 'WRD-000025', 'SCT-000225', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000126', 'WRD-000026', 'SCT-000126', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000226', 'WRD-000026', 'SCT-000226', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000127', 'WRD-000027', 'SCT-000127', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000227', 'WRD-000027', 'SCT-000227', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000128', 'WRD-000028', 'SCT-000128', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000228', 'WRD-000028', 'SCT-000228', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000129', 'WRD-000029', 'SCT-000129', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000229', 'WRD-000029', 'SCT-000229', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000130', 'WRD-000030', 'SCT-000130', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000230', 'WRD-000030', 'SCT-000230', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000131', 'WRD-000031', 'SCT-000131', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000231', 'WRD-000031', 'SCT-000231', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000132', 'WRD-000032', 'SCT-000132', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000232', 'WRD-000032', 'SCT-000232', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000133', 'WRD-000033', 'SCT-000133', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000233', 'WRD-000033', 'SCT-000233', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000134', 'WRD-000034', 'SCT-000134', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000234', 'WRD-000034', 'SCT-000234', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000135', 'WRD-000035', 'SCT-000135', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000235', 'WRD-000035', 'SCT-000235', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_word_context`
(`id`, `word_code`, `sentence_code`, `relation_type`, `sort_no`, `status`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-WCT-SCT-000136', 'WRD-000036', 'SCT-000136', 'EXTEND', 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-WCT-SCT-000236', 'WRD-000036', 'SCT-000236', 'EXTEND', 3, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_type` = VALUES(`relation_type`),
  `sort_no` = VALUES(`sort_no`),
  `update_by` = 'admin', `update_time` = NOW();

-- ------------------------------------------------------------
-- 4. 玩法题库 · 句子侦探：错句 + 三句正例句（正例句指向语境库）
-- ------------------------------------------------------------
INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000001', 'GAM-000001', 'DETECTIVE', NULL, NULL,
 '["SCT-000101", "SCT-000102", "SCT-000103"]',
 'He go to school every day.', 1, 1,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `clean_sentence_codes` = VALUES(`clean_sentence_codes`),
  `wrong_sentence` = VALUES(`wrong_sentence`),
  `wrong_position` = VALUES(`wrong_position`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000002', 'GAM-000002', 'DETECTIVE', NULL, NULL,
 '["SCT-000104", "SCT-000105", "SCT-000106"]',
 'I am want to go home.', 2, 2,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `clean_sentence_codes` = VALUES(`clean_sentence_codes`),
  `wrong_sentence` = VALUES(`wrong_sentence`),
  `wrong_position` = VALUES(`wrong_position`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000003', 'GAM-000003', 'DETECTIVE', NULL, NULL,
 '["SCT-000107", "SCT-000108", "SCT-000109"]',
 'He does goes to school every day.', 3, 3,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `clean_sentence_codes` = VALUES(`clean_sentence_codes`),
  `wrong_sentence` = VALUES(`wrong_sentence`),
  `wrong_position` = VALUES(`wrong_position`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000004', 'GAM-000004', 'DETECTIVE', NULL, NULL,
 '["SCT-000110", "SCT-000111", "SCT-000112"]',
 'He can swims very fast.', 4, 1,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `clean_sentence_codes` = VALUES(`clean_sentence_codes`),
  `wrong_sentence` = VALUES(`wrong_sentence`),
  `wrong_position` = VALUES(`wrong_position`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000005', 'GAM-000005', 'DETECTIVE', NULL, NULL,
 '["SCT-000113", "SCT-000114", "SCT-000115"]',
 'I saw a apple this morning.', 1, 2,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `clean_sentence_codes` = VALUES(`clean_sentence_codes`),
  `wrong_sentence` = VALUES(`wrong_sentence`),
  `wrong_position` = VALUES(`wrong_position`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000006', 'GAM-000006', 'DETECTIVE', NULL, NULL,
 '["SCT-000116", "SCT-000117", "SCT-000118"]',
 'I read an book last night.', 2, 3,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `clean_sentence_codes` = VALUES(`clean_sentence_codes`),
  `wrong_sentence` = VALUES(`wrong_sentence`),
  `wrong_position` = VALUES(`wrong_position`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000007', 'GAM-000007', 'DETECTIVE', NULL, NULL,
 '["SCT-000119", "SCT-000120", "SCT-000121"]',
 'We goed to the park yesterday.', 3, 1,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `clean_sentence_codes` = VALUES(`clean_sentence_codes`),
  `wrong_sentence` = VALUES(`wrong_sentence`),
  `wrong_position` = VALUES(`wrong_position`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000008', 'GAM-000008', 'DETECTIVE', NULL, NULL,
 '["SCT-000122", "SCT-000123", "SCT-000124"]',
 'I need some informations about the test.', 4, 2,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `clean_sentence_codes` = VALUES(`clean_sentence_codes`),
  `wrong_sentence` = VALUES(`wrong_sentence`),
  `wrong_position` = VALUES(`wrong_position`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000009', 'GAM-000009', 'DETECTIVE', NULL, NULL,
 '["SCT-000125", "SCT-000126", "SCT-000127"]',
 'This question is more easier than that one.', 1, 3,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `clean_sentence_codes` = VALUES(`clean_sentence_codes`),
  `wrong_sentence` = VALUES(`wrong_sentence`),
  `wrong_position` = VALUES(`wrong_position`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000010', 'GAM-000010', 'DETECTIVE', NULL, NULL,
 '["SCT-000128", "SCT-000129", "SCT-000130"]',
 'How many book do you have?', 2, 1,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `clean_sentence_codes` = VALUES(`clean_sentence_codes`),
  `wrong_sentence` = VALUES(`wrong_sentence`),
  `wrong_position` = VALUES(`wrong_position`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000011', 'GAM-000011', 'DETECTIVE', NULL, NULL,
 '["SCT-000131", "SCT-000132", "SCT-000133"]',
 'Let''s discuss about the problem tomorrow.', 3, 2,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `clean_sentence_codes` = VALUES(`clean_sentence_codes`),
  `wrong_sentence` = VALUES(`wrong_sentence`),
  `wrong_position` = VALUES(`wrong_position`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000012', 'GAM-000012', 'DETECTIVE', NULL, NULL,
 '["SCT-000134", "SCT-000135", "SCT-000136"]',
 'I very like English.', 4, 3,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `clean_sentence_codes` = VALUES(`clean_sentence_codes`),
  `wrong_sentence` = VALUES(`wrong_sentence`),
  `wrong_position` = VALUES(`wrong_position`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000013', 'GAM-000013', 'DETECTIVE', NULL, NULL,
 '["SCT-000101", "SCT-000102", "SCT-000103"]',
 'There is many students in the classroom.', 1, 1,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `clean_sentence_codes` = VALUES(`clean_sentence_codes`),
  `wrong_sentence` = VALUES(`wrong_sentence`),
  `wrong_position` = VALUES(`wrong_position`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000014', 'GAM-000014', 'DETECTIVE', NULL, NULL,
 '["SCT-000104", "SCT-000105", "SCT-000106"]',
 'I want to going home.', 2, 2,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `clean_sentence_codes` = VALUES(`clean_sentence_codes`),
  `wrong_sentence` = VALUES(`wrong_sentence`),
  `wrong_position` = VALUES(`wrong_position`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

-- ------------------------------------------------------------
-- 5. 玩法题库 · 语法变形闯关：目标表达取语境库真题句（含句子级中文意思）
-- ------------------------------------------------------------
INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000101', 'GAM-000101', 'TRANSFORM', NULL, 'SCT-000101',
 NULL, NULL, NULL, 1,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence_code` = VALUES(`sentence_code`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000102', 'GAM-000102', 'TRANSFORM', NULL, 'SCT-000102',
 NULL, NULL, NULL, 2,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence_code` = VALUES(`sentence_code`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000103', 'GAM-000103', 'TRANSFORM', NULL, 'SCT-000103',
 NULL, NULL, NULL, 3,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence_code` = VALUES(`sentence_code`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000104', 'GAM-000104', 'TRANSFORM', NULL, 'SCT-000104',
 NULL, NULL, NULL, 1,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence_code` = VALUES(`sentence_code`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000105', 'GAM-000105', 'TRANSFORM', NULL, 'SCT-000105',
 NULL, NULL, NULL, 2,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence_code` = VALUES(`sentence_code`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000106', 'GAM-000106', 'TRANSFORM', NULL, 'SCT-000106',
 NULL, NULL, NULL, 3,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence_code` = VALUES(`sentence_code`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000107', 'GAM-000107', 'TRANSFORM', NULL, 'SCT-000107',
 NULL, NULL, NULL, 1,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence_code` = VALUES(`sentence_code`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000108', 'GAM-000108', 'TRANSFORM', NULL, 'SCT-000108',
 NULL, NULL, NULL, 2,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence_code` = VALUES(`sentence_code`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000109', 'GAM-000109', 'TRANSFORM', NULL, 'SCT-000109',
 NULL, NULL, NULL, 3,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence_code` = VALUES(`sentence_code`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000110', 'GAM-000110', 'TRANSFORM', NULL, 'SCT-000110',
 NULL, NULL, NULL, 1,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence_code` = VALUES(`sentence_code`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000111', 'GAM-000111', 'TRANSFORM', NULL, 'SCT-000111',
 NULL, NULL, NULL, 2,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence_code` = VALUES(`sentence_code`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

INSERT INTO `study_grammar_game_question`
(`id`, `question_code`, `game_type`, `grammar_code`, `sentence_code`,
 `clean_sentence_codes`, `wrong_sentence`, `wrong_position`, `difficulty`,
 `judge_source`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-GAM-000112', 'GAM-000112', 'TRANSFORM', NULL, 'SCT-000112',
 NULL, NULL, NULL, 3,
 'rule', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `sentence_code` = VALUES(`sentence_code`),
  `difficulty` = VALUES(`difficulty`),
  `update_by` = 'admin', `update_time` = NOW();

-- ------------------------------------------------------------
-- 6. 业务ID 序列登记：SCT（例句）/ GAM（玩法题）
--    next_value 从 1000 起，避开上面已用的业务ID
-- ------------------------------------------------------------
INSERT INTO `study_id_sequence` (`id`, `prefix`, `next_value`, `step`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SEQ-SCT', 'SCT', 1000, 1, 0, 'admin', NOW(), 'admin', NOW()),
('SEED-SEQ-GAM', 'GAM', 1000, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `next_value` = VALUES(`next_value`),
  `step` = VALUES(`step`),
  `update_by` = 'admin', `update_time` = NOW();
