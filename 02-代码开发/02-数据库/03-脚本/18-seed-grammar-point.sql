-- ============================================================
-- jeecg-study · 语法点主表种子 增量脚本 grammar-point.sql
-- 需求：F-GRM-01 句子变形器 / F-GRM-02 错误即入口 / F-GRM-03 考纲锚定
-- 目标库：jeecg-study（MySQL 8）· 表前缀 study_
--
-- 作用：灌入首批语法点主表内容，并把玩法题与语境库关联到对应语法点
--
-- 背景（2026-10-31，P2 浏览器取证 TC-08-20 截图时发现）：
--   study_grammar_point 此前**一行都没有**——09-seed-content.sql 只灌了
--   考点 / 母题 / 题目 / 单词，14-grammar-context-game.sql 只灌了语境库与
--   玩法题 4 张新表。后果是学习端「英语语法」页直接显示空态
--   （暂时没有语法点），「句子变形闯关」也拿不到五态语料。
--
-- 变更方式（铁律 2）：**变更走增量脚本，不改历史脚本**。
--       本脚本**不触碰** 02-schema.sql / 03-init.sql / 04-fix.sql /
--       05-partition.sql / 06-cleanup.sql / 09 / 14 等任何已登记的历史产物。
--
-- 幂等：语法点按 grammar_code 唯一键 INSERT ... ON DUPLICATE KEY UPDATE；
--       玩法题与语境库的关联用 UPDATE 覆写（重复执行结果一致）。
--
-- 数据范围：study_grammar_point 是**内容表**（全局共享，没有 child_id）。
--
-- 内容口径：
--   1) 11 个语法点的业务ID 与代码里的确定性规则目录
--      （org.jeecg.modules.study.grammar.engine.GrammarErrorRuleCatalog）
--      **一一对应**：规则目录里每条规则都指着一个 GRP 编码，「错误即入口」
--      链路（/grammar/error-detail）要按这个编码回查语法点的三件套，
--      所以这 11 个编码必须存在，且讲解内容必须与规则目录口径一致
--      （最小讲解 + 同类例句 + 反例）。
--   2) 每个语法点都挂到考点树的三级考点（英语语法分支），
--      并标注中考题型（单选 / 完形 / 语法填空 / 书面表达）——F-GRM-03。
--   3) transform_variants 是「句子变形器」的语料：同一个中文意思的
--      五种说法（LITERAL / COLLOQUIAL / WRITTEN / EXAM_SAFE / WRONG）。
--      语料里的 meaning 必须与 study_sentence_context.zh_meaning **逐字一致**
--      （服务端归一化后比对：去空白、去中英文标点、转小写）。
--   4) 14 条句子侦探题的错句与规则目录 GR-ERR-01…14 逐条对应，
--      这里把题的 grammar_code 回填成该错句命中的语法点；
--      12 条变形闯关题按语料所属语法点回填。
--
-- 版权：全部自编（copyright_type=SELF），不含真实个人信息，
--       例句均为自写句子，不取自任何受版权保护的教材原文。
--
-- 回滚：不删表（铁律 4）。如需清空种子数据，
--       把本脚本里的 UPDATE 段注释掉即可；语法点本体保留不影响功能。
--
-- 迁移留痕：执行本脚本后，按《数据库设计说明书》§12.4 在 `study_schema_migration`
--       登记一条记录（脚本名 + 本文件内容的 SHA-256 + 执行时间 + 执行人）。
-- ============================================================

USE `jeecg-study`;

-- ------------------------------------------------------------
-- 1. 语法点主表（11 个）
--    业务ID 与 GrammarErrorRuleCatalog 的规则一一对应
-- ------------------------------------------------------------
INSERT INTO `study_grammar_point`
(`id`, `grammar_code`, `title`, `rule`, `example`, `counter_example`,
 `exam_type`, `subject_code`, `knowledge_point_code`, `transform_variants`,
 `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES

-- 第三人称单数要加 -s/-es（GR-ERR-05）
('SEED-GRP-0007', 'GRP-0007', '第三人称单数要加 -s/-es',
 '第三人称单数（he / she / it）后面的动词要加 -s 或 -es',
 'He goes to school every day.', 'He go to school every day.',
 '语法填空', 'EN', 'KPT-EN-000023',
 '[{"meaning":"我们夏天买瓜，因为它便宜。","grammarCode":"GRP-0007","variants":[{"type":"LITERAL","text":"We buy a melon in summer because it is cheap."},{"type":"COLLOQUIAL","text":"We get melon in the summer because it is cheaper."},{"type":"WRITTEN","text":"Melons are purchased during the summer months, as they are comparatively inexpensive."},{"type":"EXAM_SAFE","text":"We buy a melon in summer because it is cheap."},{"type":"WRONG","text":"We buy a melon in summer because it cheap."}]},{"meaning":"我用钢笔写，而不是用铅笔。","grammarCode":"GRP-0007","variants":[{"type":"LITERAL","text":"I write with a pen instead of a pencil."},{"type":"COLLOQUIAL","text":"I use a pen, not a pencil."},{"type":"WRITTEN","text":"Writing is carried out with a fountain pen rather than a pencil."},{"type":"EXAM_SAFE","text":"I write with a pen instead of a pencil."},{"type":"WRONG","text":"I write with a pen instead of pencil."}]}]',
 1, 0, 'admin', NOW(), 'admin', NOW()),

-- 比较级不能用 more + -er（GR-ERR-10）
('SEED-GRP-0009', 'GRP-0009', '比较级不要 more 加 -er',
 '比较级不能用 more 和 -er 同时出现',
 'This question is easier than that one.', 'This question is more easier than that one.',
 '单选', 'EN', 'KPT-EN-000023',
 '[{"meaning":"猴子更喜欢香蕉，而不是苹果。","grammarCode":"GRP-0009","variants":[{"type":"LITERAL","text":"The monkey likes bananas better than apples."},{"type":"COLLOQUIAL","text":"The monkey prefers bananas to apples."},{"type":"WRITTEN","text":"The monkey shows a stronger preference for bananas than for apples."},{"type":"EXAM_SAFE","text":"The monkey likes bananas better than apples."},{"type":"WRONG","text":"The monkey likes bananas more better than apples."}]},{"meaning":"这个橙子甜，但那个很酸。","grammarCode":"GRP-0009","variants":[{"type":"LITERAL","text":"This orange is sweet, but that one is sour."},{"type":"COLLOQUIAL","text":"This orange is sweet, that one is sour though."},{"type":"WRITTEN","text":"This orange is sweet, whereas that one is sour."},{"type":"EXAM_SAFE","text":"This orange is sweet, but that one is sour."},{"type":"WRONG","text":"This orange is sweet, but that one are sour."}]}]',
 1, 0, 'admin', NOW(), 'admin', NOW()),

-- 冠词 a / an（GR-ERR-06 / GR-ERR-07）
('SEED-GRP-0012', 'GRP-0012', 'a 还是 an',
 '元音音素开头的单词前面用 an，辅音音素开头的用 a',
 'I saw an apple this morning.', 'I saw a apple this morning.',
 '语法填空', 'EN', 'KPT-EN-000026',
 '[{"meaning":"医生让我每天早上吃一个苹果。","grammarCode":"GRP-0012","variants":[{"type":"LITERAL","text":"The doctor asked me to eat an apple every morning."},{"type":"COLLOQUIAL","text":"The doctor told me to have an apple every morning."},{"type":"WRITTEN","text":"The doctor instructed me to consume an apple every single morning."},{"type":"EXAM_SAFE","text":"The doctor advised me to eat an apple every morning."},{"type":"WRONG","text":"The doctor asked me eat an apple every morning."}]}]',
 1, 0, 'admin', NOW(), 'admin', NOW()),

-- how many 后接可数名词复数（GR-ERR-11）
('SEED-GRP-0014', 'GRP-0014', 'how many 后接复数',
 'how many 后面接可数名词的复数形式',
 'How many books do you have?', 'How many book do you have?',
 '单选', 'EN', 'KPT-EN-000026',
 '[{"meaning":"我的橡皮在哪里？我数学考试要用它。","grammarCode":"GRP-0014","variants":[{"type":"LITERAL","text":"Where is my eraser? I need it for the maths test."},{"type":"COLLOQUIAL","text":"Where has my eraser gone? I need it for the maths test."},{"type":"WRITTEN","text":"Could you indicate the whereabouts of my eraser? It is required for the mathematics examination."},{"type":"EXAM_SAFE","text":"Where is my eraser? I need it for the maths test."},{"type":"WRONG","text":"Where my eraser is? I need it for the maths test."}]}]',
 1, 0, 'admin', NOW(), 'admin', NOW()),

-- discuss 后面不加 about（GR-ERR-12）
('SEED-GRP-0016', 'GRP-0016', 'discuss 后面不加 about',
 'discuss 后面直接跟宾语，不再加 about',
 'Let''s discuss the problem tomorrow.', 'Let''s discuss about the problem tomorrow.',
 '完形', 'EN', 'KPT-EN-000012',
 '[{"meaning":"用尺子画一条线，然后把纸剪开。","grammarCode":"GRP-0016","variants":[{"type":"LITERAL","text":"Draw a line with the ruler, and then cut the paper."},{"type":"COLLOQUIAL","text":"Use the ruler to draw a line, then cut the paper."},{"type":"WRITTEN","text":"A line is to be drawn with the ruler, after which the paper is cut along it."},{"type":"EXAM_SAFE","text":"Draw a line with the ruler, and then cut the paper."},{"type":"WRONG","text":"Draw a line with ruler, and then cut the paper."}]}]',
 1, 0, 'admin', NOW(), 'admin', NOW()),

-- 助动词 / 情态动词后用动词原形（GR-ERR-03 / GR-ERR-04）
('SEED-GRP-0018', 'GRP-0018', '助动词后面用动词原形',
 'does / did 后面用动词原形，情态动词后面也用动词原形',
 'Does he go to school every day?', 'Does he goes to school every day?',
 '语法填空', 'EN', 'KPT-EN-000024',
 '[{"meaning":"这支铅笔是新的，所以我还没用过。","grammarCode":"GRP-0018","variants":[{"type":"LITERAL","text":"This pencil is new, so I have not used it yet."},{"type":"COLLOQUIAL","text":"This pencil is brand new, so I have not used it yet."},{"type":"WRITTEN","text":"This pencil is newly acquired; accordingly, it has not yet been put to use."},{"type":"EXAM_SAFE","text":"This pencil is new, so I have not used it yet."},{"type":"WRONG","text":"This pencil is new, so I have not use it yet."}]}]',
 1, 0, 'admin', NOW(), 'admin', NOW()),

-- 不可数名词不能加 -s（GR-ERR-09）
('SEED-GRP-0021', 'GRP-0021', '不可数名词不能加 -s',
 'information / advice / people 这类词不能加 -s',
 'I need some information about the test.', 'I need some informations about the test.',
 '单选', 'EN', 'KPT-EN-000026',
 '[{"meaning":"葡萄在夏天长在藤上。","grammarCode":"GRP-0021","variants":[{"type":"LITERAL","text":"Grapes grow on the vine in summer."},{"type":"COLLOQUIAL","text":"Grapes grow on vines in the summer."},{"type":"WRITTEN","text":"Grapes are cultivated on the vine during the summer months."},{"type":"EXAM_SAFE","text":"Grapes grow on the vine in summer."},{"type":"WRONG","text":"Grapes grows on the vine in summer."}]}]',
 1, 0, 'admin', NOW(), 'admin', NOW()),

-- there be 与后面的名词保持一致（GR-ERR-14）
('SEED-GRP-0024', 'GRP-0024', 'there be 与后面的名词一致',
 'many / several 后面跟复数，用 there are',
 'There are many students in the classroom.', 'There is many students in the classroom.',
 '语法填空', 'EN', 'KPT-EN-000027',
 NULL,
 1, 0, 'admin', NOW(), 'admin', NOW()),

-- 不规则动词的过去式（GR-ERR-08）
('SEED-GRP-0025', 'GRP-0025', '不规则动词的过去式',
 'go 的过去式是 went，不规则动词要单独记',
 'We went to the park yesterday.', 'We goed to the park yesterday.',
 '语法填空', 'EN', 'KPT-EN-000023',
 NULL,
 1, 0, 'admin', NOW(), 'admin', NOW()),

-- very 不能修饰动词（GR-ERR-13）
('SEED-GRP-0028', 'GRP-0028', 'very 不能修饰动词',
 'very 不能直接修饰动词，改用 really 或者 very much',
 'I really like English.', 'I very like English.',
 '完形', 'EN', 'KPT-EN-000012',
 NULL,
 1, 0, 'admin', NOW(), 'admin', NOW()),

-- want to do sth 的用法（GR-ERR-01 / GR-ERR-02）
('SEED-GRP-0031', 'GRP-0031', 'want to do sth 的用法',
 'want to do sth 里 want 后面跟动词原形，不能加 -ing；前面也不能再加 be 动词',
 'I want to go home.', 'I want going home.',
 '语法填空', 'EN', 'KPT-EN-000024',
 '[{"meaning":"她想要一个梨，因为她喜欢吃甜水果。","grammarCode":"GRP-0031","variants":[{"type":"LITERAL","text":"She wants a pear because she likes sweet fruit."},{"type":"COLLOQUIAL","text":"She wants a pear because she likes sweet stuff."},{"type":"WRITTEN","text":"She would like a pear, as she has a preference for sweet fruit."},{"type":"EXAM_SAFE","text":"She wants a pear because she likes sweet fruit."},{"type":"WRONG","text":"She want a pear because she likes sweet fruit."}]},{"meaning":"这个桃子很多汁，所以我想现在就吃掉。","grammarCode":"GRP-0031","variants":[{"type":"LITERAL","text":"The peach is very juicy, so I want to eat it now."},{"type":"COLLOQUIAL","text":"This peach is really juicy, so I want to eat it right now."},{"type":"WRITTEN","text":"The peach is highly succulent; accordingly, I wish to consume it immediately."},{"type":"EXAM_SAFE","text":"The peach is very juicy, so I want to eat it now."},{"type":"WRONG","text":"The peach is very juicy, so I want eating it now."}]},{"meaning":"这个柠檬很酸，但我还是想试试。","grammarCode":"GRP-0031","variants":[{"type":"LITERAL","text":"This lemon is sour, but I still want to try it."},{"type":"COLLOQUIAL","text":"This lemon is really sour, but I still want to give it a try."},{"type":"WRITTEN","text":"This lemon is exceedingly tart; nevertheless, I remain willing to sample it."},{"type":"EXAM_SAFE","text":"This lemon is sour, but I still want to try it."},{"type":"WRONG","text":"This lemon is sour, but I still want trying it."}]}]',
 1, 0, 'admin', NOW(), 'admin', NOW())

ON DUPLICATE KEY UPDATE
  `title` = VALUES(`title`),
  `rule` = VALUES(`rule`),
  `example` = VALUES(`example`),
  `counter_example` = VALUES(`counter_example`),
  `exam_type` = VALUES(`exam_type`),
  `subject_code` = VALUES(`subject_code`),
  `knowledge_point_code` = VALUES(`knowledge_point_code`),
  `transform_variants` = VALUES(`transform_variants`),
  `status` = VALUES(`status`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin', `update_time` = NOW();

-- ------------------------------------------------------------
-- 2. 句子侦探题回填 grammar_code
--    14 条错句与规则目录 GR-ERR-01…14 逐条对应，
--    回填后「错误即入口」能按语法点给出最小讲解
-- ------------------------------------------------------------
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0007', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000001';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0031', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000002';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0018', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000003';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0018', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000004';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0012', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000005';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0012', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000006';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0025', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000007';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0021', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000008';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0009', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000009';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0014', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000010';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0016', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000011';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0028', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000012';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0024', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000013';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0031', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000014';

-- ------------------------------------------------------------
-- 3. 变形闯关题回填 grammar_code
--    语料所属语法点：与 transform_variants 里的 grammarCode 一致
-- ------------------------------------------------------------
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0012', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000101';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0009', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000102';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0009', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000103';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0031', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000104';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0021', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000105';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0031', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000106';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0031', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000107';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0007', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000108';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0018', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000109';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0007', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000110';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0016', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000111';
UPDATE `study_grammar_game_question` SET `grammar_code` = 'GRP-0014', `update_by` = 'admin', `update_time` = NOW()
WHERE `question_code` = 'GAM-000112';

-- ------------------------------------------------------------
-- 4. 语境库回填 grammar_code（只回填被变形闯关题直接引用的那批）
--    这批语境是变形闯关的出题素材，meaning 与语法点语料逐字对应
-- ------------------------------------------------------------
UPDATE `study_sentence_context` SET `grammar_code` = 'GRP-0012', `update_by` = 'admin', `update_time` = NOW()
WHERE `sentence_code` = 'SCT-000101';
UPDATE `study_sentence_context` SET `grammar_code` = 'GRP-0009', `update_by` = 'admin', `update_time` = NOW()
WHERE `sentence_code` = 'SCT-000102';
UPDATE `study_sentence_context` SET `grammar_code` = 'GRP-0009', `update_by` = 'admin', `update_time` = NOW()
WHERE `sentence_code` = 'SCT-000103';
UPDATE `study_sentence_context` SET `grammar_code` = 'GRP-0031', `update_by` = 'admin', `update_time` = NOW()
WHERE `sentence_code` = 'SCT-000104';
UPDATE `study_sentence_context` SET `grammar_code` = 'GRP-0021', `update_by` = 'admin', `update_time` = NOW()
WHERE `sentence_code` = 'SCT-000105';
UPDATE `study_sentence_context` SET `grammar_code` = 'GRP-0031', `update_by` = 'admin', `update_time` = NOW()
WHERE `sentence_code` = 'SCT-000106';
UPDATE `study_sentence_context` SET `grammar_code` = 'GRP-0031', `update_by` = 'admin', `update_time` = NOW()
WHERE `sentence_code` = 'SCT-000107';
UPDATE `study_sentence_context` SET `grammar_code` = 'GRP-0007', `update_by` = 'admin', `update_time` = NOW()
WHERE `sentence_code` = 'SCT-000108';
UPDATE `study_sentence_context` SET `grammar_code` = 'GRP-0018', `update_by` = 'admin', `update_time` = NOW()
WHERE `sentence_code` = 'SCT-000109';
UPDATE `study_sentence_context` SET `grammar_code` = 'GRP-0007', `update_by` = 'admin', `update_time` = NOW()
WHERE `sentence_code` = 'SCT-000110';
UPDATE `study_sentence_context` SET `grammar_code` = 'GRP-0016', `update_by` = 'admin', `update_time` = NOW()
WHERE `sentence_code` = 'SCT-000111';
UPDATE `study_sentence_context` SET `grammar_code` = 'GRP-0014', `update_by` = 'admin', `update_time` = NOW()
WHERE `sentence_code` = 'SCT-000112';

-- ------------------------------------------------------------
-- 5. 业务ID 序列登记：GRP（语法点）
--    next_value 从 100 起，避开上面已用的 GRP-0007…GRP-0031
-- ------------------------------------------------------------
INSERT INTO `study_id_sequence` (`id`, `prefix`, `next_value`, `step`, `del_flag`,
 `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('SEED-SEQ-GRP', 'GRP', 100, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `next_value` = VALUES(`next_value`),
  `step` = VALUES(`step`),
  `update_by` = 'admin', `update_time` = NOW();
