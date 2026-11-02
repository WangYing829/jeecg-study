-- =============================================================================
-- 09-seed-content.sql · 运行时种子：坐标树 + 数学母题 + 题目 + 词表（业务完整版）
-- =============================================================================
-- 用途：让学习端每个页面都有**真实可判定的内容**。
--   没有内容 → 取题直接 400「该类型暂时没有可用题目」，背单词页空列表，
--   家长端趋势图无数据 —— 上一轮自动化用例全绿也测不到这一层（用的是测试夹具）。
--
-- 规模（按完整业务规模铺开，不做最小集裁剪）：
--   坐标树      39 个节点（3 学科 × 3 层：一级领域 / 二级知识块 / 三级考点）
--   数学母题     4 道（每道五段齐全：条件类型 / 关键关系 / 步骤链 / 易错点）
--   题目        32 道（数学 12 / 英语语法 8 / 语文写作 6 / 单词 6）
--   词表        36 条（课内 3 单元 × 8 + 课外 12）
--
-- 业务 ID 格式与运行时生成器完全一致（StudyIdSequenceServiceImpl）：
--   坐标树 KPT-<学科>-000001 / 母题 MTH-MA-000001 / 单词 WRD-000001
--   （ID 序列当前 next_value 均远高于种子编号，运行时不会撞号）
--
-- 答案格式与判定引擎对齐（JudgeEngine）：
--   NUMERIC       answer 为数值文本，answer_tolerance 为容差（差值 < 容差判对，Q-01）
--   MULTI_CHOICE  answer 为选项键集合，逗号分隔（如 "A" / "A,C"）
--   EXACT         answer 为标准文本，判定前做 trim + 小写归一
--   options 存 JSON 文本数组：[{"key":"A","label":"…"}]，前端按 key/label 渲染
--
-- 幂等：全部 ON DUPLICATE KEY UPDATE，重复执行不报错、不重复插。
-- =============================================================================

-- =============================================================================
-- 1. 坐标树 study_knowledge_point
--    level：1 = 一级领域，2 = 二级知识块，3 = 三级考点
-- =============================================================================
INSERT INTO `study_knowledge_point`
  (`id`, `point_code`, `parent_id`, `level`, `subject_code`, `name`, `path`,
   `question_type`, `score_weight`, `error_types`, `mother_question_code`, `version`,
   `effective_from`, `invalid_at`, `status`, `sort_no`, `del_flag`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  -- ---------- 数学 · 一级领域 ----------
  ('ST-KPT-001', 'KPT-MA-000001', NULL, 1, 'MA', '数与代数', '数学', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-002', 'KPT-MA-000002', NULL, 1, 'MA', '图形与几何', '数学', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 2, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-003', 'KPT-MA-000003', NULL, 1, 'MA', '统计与概率', '数学', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 3, 0, 'admin', NOW(), 'admin', NOW()),
  -- ---------- 英语 · 一级领域 ----------
  ('ST-KPT-004', 'KPT-EN-000001', NULL, 1, 'EN', '词汇', '英语', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 4, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-005', 'KPT-EN-000002', NULL, 1, 'EN', '语法', '英语', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 5, 0, 'admin', NOW(), 'admin', NOW()),
  -- ---------- 语文 · 一级领域 ----------
  ('ST-KPT-006', 'KPT-CN-000001', NULL, 1, 'CN', '阅读', '语文', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 6, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-007', 'KPT-CN-000002', NULL, 1, 'CN', '写作', '语文', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 7, 0, 'admin', NOW(), 'admin', NOW()),
  -- ---------- 数学 · 二级知识块 ----------
  ('ST-KPT-101', 'KPT-MA-000011', 'ST-KPT-001', 2, 'MA', '数的认识', '数学/数与代数', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-102', 'KPT-MA-000012', 'ST-KPT-001', 2, 'MA', '运算', '数学/数与代数', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 2, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-103', 'KPT-MA-000013', 'ST-KPT-002', 2, 'MA', '图形', '数学/图形与几何', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-104', 'KPT-MA-000014', 'ST-KPT-002', 2, 'MA', '长度与面积', '数学/图形与几何', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 2, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-105', 'KPT-MA-000015', 'ST-KPT-003', 2, 'MA', '统计图表', '数学/统计与概率', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  -- ---------- 英语 · 二级知识块 ----------
  ('ST-KPT-106', 'KPT-EN-000011', 'ST-KPT-004', 2, 'EN', '词表', '英语/词汇', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-107', 'KPT-EN-000012', 'ST-KPT-005', 2, 'EN', '词法', '英语/语法', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-108', 'KPT-EN-000013', 'ST-KPT-005', 2, 'EN', '句法', '英语/语法', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 2, 0, 'admin', NOW(), 'admin', NOW()),
  -- ---------- 语文 · 二级知识块 ----------
  ('ST-KPT-109', 'KPT-CN-000011', 'ST-KPT-006', 2, 'CN', '文体', '语文/阅读', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-110', 'KPT-CN-000012', 'ST-KPT-007', 2, 'CN', '文体', '语文/写作', NULL, NULL, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  -- ---------- 数学 · 三级考点 ----------
  ('ST-KPT-201', 'KPT-MA-000021', 'ST-KPT-101', 3, 'MA', '整数的认识', '数学/数与代数/数的认识', 'NUMERIC', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-202', 'KPT-MA-000022', 'ST-KPT-101', 3, 'MA', '四则运算的意义', '数学/数与代数/数的认识', 'NUMERIC', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 2, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-203', 'KPT-MA-000023', 'ST-KPT-102', 3, 'MA', '加减法笔算', '数学/数与代数/运算', 'NUMERIC', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-204', 'KPT-MA-000024', 'ST-KPT-102', 3, 'MA', '乘除法笔算', '数学/数与代数/运算', 'NUMERIC', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 2, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-205', 'KPT-MA-000025', 'ST-KPT-102', 3, 'MA', '混合运算顺序', '数学/数与代数/运算', 'NUMERIC', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 3, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-206', 'KPT-MA-000026', 'ST-KPT-102', 3, 'MA', '平均分与归一问题', '数学/数与代数/运算', 'NUMERIC', 1.00, NULL, 'MTH-MA-000001', 'v1',
   '2026-01-01', NULL, 1, 4, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-207', 'KPT-MA-000027', 'ST-KPT-102', 3, 'MA', '和差问题', '数学/数与代数/运算', 'NUMERIC', 1.00, NULL, 'MTH-MA-000002', 'v1',
   '2026-01-01', NULL, 1, 5, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-208', 'KPT-MA-000028', 'ST-KPT-103', 3, 'MA', '三角形的性质', '数学/图形与几何/图形', 'MULTI_CHOICE', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-209', 'KPT-MA-000029', 'ST-KPT-104', 3, 'MA', '长方形的周长与面积', '数学/图形与几何/长度与面积', 'NUMERIC', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-210', 'KPT-MA-000030', 'ST-KPT-105', 3, 'MA', '条形统计图', '数学/统计与概率/统计图表', 'MULTI_CHOICE', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  -- ---------- 英语 · 三级考点 ----------
  ('ST-KPT-211', 'KPT-EN-000021', 'ST-KPT-106', 3, 'EN', '课内单元词汇', '英语/词汇/词表', 'WORD', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-212', 'KPT-EN-000022', 'ST-KPT-106', 3, 'EN', '课外高频词汇', '英语/词汇/词表', 'WORD', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 2, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-213', 'KPT-EN-000023', 'ST-KPT-107', 3, 'EN', '一般现在时', '英语/语法/词法', 'GRAMMAR', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-214', 'KPT-EN-000024', 'ST-KPT-107', 3, 'EN', 'be 动词与实义动词', '英语/语法/词法', 'GRAMMAR', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 2, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-215', 'KPT-EN-000025', 'ST-KPT-107', 3, 'EN', '情态动词 can', '英语/语法/词法', 'GRAMMAR', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 3, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-216', 'KPT-EN-000026', 'ST-KPT-108', 3, 'EN', '名词单复数', '英语/语法/句法', 'GRAMMAR', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-217', 'KPT-EN-000027', 'ST-KPT-108', 3, 'EN', 'there be 句型', '英语/语法/句法', 'GRAMMAR', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 2, 0, 'admin', NOW(), 'admin', NOW()),
  -- ---------- 语文 · 三级考点 ----------
  ('ST-KPT-218', 'KPT-CN-000021', 'ST-KPT-109', 3, 'CN', '记叙文阅读', '语文/阅读/文体', 'MULTI_CHOICE', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-219', 'KPT-CN-000022', 'ST-KPT-109', 3, 'CN', '说明文阅读', '语文/阅读/文体', 'MULTI_CHOICE', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 2, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-220', 'KPT-CN-000023', 'ST-KPT-110', 3, 'CN', '记叙文写作', '语文/写作/文体', 'MULTI_CHOICE', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-221', 'KPT-CN-000024', 'ST-KPT-110', 3, 'CN', '细节描写', '语文/写作/文体', 'MULTI_CHOICE', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 2, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-KPT-222', 'KPT-CN-000025', 'ST-KPT-110', 3, 'CN', '开头与结尾', '语文/写作/文体', 'EXACT', 1.00, NULL, NULL, 'v1',
   '2026-01-01', NULL, 1, 3, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `level` = VALUES(`level`),
  `subject_code` = VALUES(`subject_code`),
  `name` = VALUES(`name`),
  `path` = VALUES(`path`),
  `question_type` = VALUES(`question_type`),
  `mother_question_code` = VALUES(`mother_question_code`),
  `status` = VALUES(`status`),
  `sort_no` = VALUES(`sort_no`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin',
  `update_time` = NOW();

-- =============================================================================
-- 2. 数学母题 study_mother_question
--    五段结构：标题 / 已知条件类型 / 关键关系 / 解题步骤链 / 易错点
--    步骤链里的 formula 是算式，变式答案由它算出（MotherVariantRuleEngine，R-02 零 AI）
--    算式只支持 + - * / ( ) 与标识符，标识符必须与条件 key 对得上
-- =============================================================================
INSERT INTO `study_mother_question`
  (`id`, `mother_code`, `title`, `condition_types`, `key_relation`, `steps`, `pitfalls`,
   `subject_code`, `knowledge_point_code`, `pass_variant_count`, `variant_count`, `order_no`,
   `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  ('ST-MTH-001', 'MTH-MA-000001', '同样多的一批东西平均分成若干份，求每一份是多少',
   '[{"key":"S","label":"总数","variants":["一共有多少","总数","总量"],"value":240},{"key":"n","label":"份数","variants":["平均分成几份","有几个人","分成几份"],"value":6}]',
   '总数 = 每份数 × 份数，所以 每份数 = 总数 ÷ 份数。',
   '[{"index":1,"title":"每份有多少","formula":"S / n","note":"总数除以份数"},{"index":2,"title":"核对总量","formula":"","note":"每份数乘回份数应等于总数"}]',
   '["把「总数 ÷ 份数」写成乘法","份数与每份数弄反"]',
   'MA', 'KPT-MA-000026', 3, 0, 1, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-MTH-002', 'MTH-MA-000002', '已知两个数的和与差，求较大的那个数',
   '[{"key":"s","label":"两数之和","variants":["一共是多少","和是多少"],"value":80},{"key":"d","label":"两数之差","variants":["相差多少","差是多少"],"value":12}]',
   '大数 = (和 + 差) ÷ 2；小数 = (和 − 差) ÷ 2。',
   '[{"index":1,"title":"较大的数是多少","formula":"(s + d) / 2","note":"先把和与差相加，再除以 2"},{"index":2,"title":"较小的数是多少","formula":"(s - d) / 2","note":"用和减去差再除以 2"}]',
   '["只把和除以 2","忘了先加差就直接除","把较大的数算成了较小的数"]',
   'MA', 'KPT-MA-000027', 3, 0, 2, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-MTH-003', 'MTH-MA-000003', '在一条路的一侧栽树，两端都栽时求一共栽多少棵',
   '[{"key":"n","label":"间隔数","variants":["一共分成几段","有几个间隔","路段数"],"value":20}]',
   '两端都栽：棵数 = 间隔数 + 1；只栽一端：棵数 = 间隔数；两端都不栽：棵数 = 间隔数 − 1。',
   '[{"index":1,"title":"一共要栽多少棵","formula":"n + 1","note":"两端都栽，棵数比间隔数多 1"}]',
   '["忘记两端都栽","把棵数当成与间隔数相等","没看清题目说的是哪一端栽树"]',
   'MA', 'KPT-MA-000026', 3, 0, 3, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-MTH-004', 'MTH-MA-000004', '两人从两地同时出发相向而行，求速度和',
   '[{"key":"s","label":"总路程","variants":["两地相距多远","一共要走多远","全程"],"value":240},{"key":"t","label":"相遇时间","variants":["走了几小时","几小时后相遇","用了多少时间"],"value":2}]',
   '路程 = 速度和 × 相遇时间，所以 速度和 = 总路程 ÷ 相遇时间。',
   '[{"index":1,"title":"速度和是多少","formula":"s / t","note":"总路程除以相遇时间"},{"index":2,"title":"其中一个的速度","formula":"s / t","note":"若两人速度相同，可再平均分成两份"}]',
   '["把速度和当成单个孩子的速度","时间单位没换算","忘了两人是同时出发"]',
   'MA', 'KPT-MA-000026', 3, 0, 4, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `title` = VALUES(`title`),
  `condition_types` = VALUES(`condition_types`),
  `key_relation` = VALUES(`key_relation`),
  `steps` = VALUES(`steps`),
  `pitfalls` = VALUES(`pitfalls`),
  `subject_code` = VALUES(`subject_code`),
  `knowledge_point_code` = VALUES(`knowledge_point_code`),
  `pass_variant_count` = VALUES(`pass_variant_count`),
  `order_no` = VALUES(`order_no`),
  `status` = VALUES(`status`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin',
  `update_time` = NOW();

-- =============================================================================
-- 3. 题目 study_question
-- =============================================================================
INSERT INTO `study_question`
  (`id`, `question_code`, `question_type`, `subject_code`, `knowledge_point_code`, `stem`,
   `options`, `answer`, `answer_tolerance`, `analysis`, `difficulty`, `source_type`, `source_ref`,
   `copyright_type`, `mother_question_code`, `variant_layer`, `status`, `del_flag`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  -- ---------------- 数学 · 数值题 ----------------
  ('ST-QST-MA-001', 'QST-MA-000001', 'NUMERIC', 'MA', 'KPT-MA-000023',
   '小星有 128 张贴纸，送给弟弟 37 张，又买回 45 张，现在一共有多少张贴纸？',
   NULL, '136', 0.01,
   '先算送出的部分：128 − 37 = 91（张）；再算买回的：91 + 45 = 136（张）。所以现在有 136 张贴纸。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-MA-002', 'QST-MA-000002', 'NUMERIC', 'MA', 'KPT-MA-000024',
   '每盒彩笔有 12 支，5 盒一共有多少支彩笔？',
   NULL, '60', 0.01,
   '求几个相同加数的和用乘法：12 × 5 = 60（支）。所以一共有 60 支彩笔。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-MA-003', 'QST-MA-000003', 'NUMERIC', 'MA', 'KPT-MA-000024',
   '96 本书平均分给 8 个小组，每组分到多少本？',
   NULL, '12', 0.01,
   '平均分用除法：96 ÷ 8 = 12（本）。所以每组分到 12 本书。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-MA-004', 'QST-MA-000004', 'NUMERIC', 'MA', 'KPT-MA-000025',
   '先算 24 × 3，再加 18，结果是多少？',
   NULL, '90', 0.01,
   '先乘后加：24 × 3 = 72；72 + 18 = 90。所以结果是 90。',
   3, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-MA-005', 'QST-MA-000005', 'NUMERIC', 'MA', 'KPT-MA-000025',
   '(120 − 36) ÷ 6 的结果是多少？',
   NULL, '14', 0.01,
   '先算括号里：120 − 36 = 84；再算 84 ÷ 6 = 14。所以结果是 14。',
   3, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-MA-006', 'QST-MA-000006', 'NUMERIC', 'MA', 'KPT-MA-000026',
   '把 240 颗糖平均分给 6 个小朋友，每人分到多少颗？',
   NULL, '40', 0.01,
   '平均分用除法：240 ÷ 6 = 40（颗）。所以每人分到 40 颗糖。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-MA-007', 'QST-MA-000007', 'NUMERIC', 'MA', 'KPT-MA-000027',
   '两个数的和是 80，差是 12，较大的数是多少？',
   NULL, '46', 0.01,
   '大数 = (和 + 差) ÷ 2 = (80 + 12) ÷ 2 = 92 ÷ 2 = 46。所以较大的数是 46。',
   3, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-MA-008', 'QST-MA-000008', 'NUMERIC', 'MA', 'KPT-MA-000027',
   '两个数的和是 80，差是 12，较小的数是多少？',
   NULL, '34', 0.01,
   '小数 = (和 − 差) ÷ 2 = (80 − 12) ÷ 2 = 68 ÷ 2 = 34。所以较小的数是 34。',
   3, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-MA-009', 'QST-MA-000009', 'NUMERIC', 'MA', 'KPT-MA-000029',
   '一个长方形长 8 厘米、宽 5 厘米，它的周长是多少厘米？',
   NULL, '26', 0.01,
   '长方形周长 = (长 + 宽) × 2 = (8 + 5) × 2 = 26（厘米）。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-MA-010', 'QST-MA-000010', 'NUMERIC', 'MA', 'KPT-MA-000029',
   '一个长方形长 8 厘米、宽 5 厘米，它的面积是多少平方厘米？',
   NULL, '40', 0.01,
   '长方形面积 = 长 × 宽 = 8 × 5 = 40（平方厘米）。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-MA-011', 'QST-MA-000011', 'MULTI_CHOICE', 'MA', 'KPT-MA-000028',
   '下面关于三角形的说法，正确的是？',
   '[{"key":"A","label":"三角形有三条边、三个角"},{"key":"B","label":"三角形一定有两条边相等"},{"key":"C","label":"直角三角形有两个直角"},{"key":"D","label":"三角形可以只有一个角"}]',
   'A', NULL,
   '三角形由三条线段首尾相连围成，有三条边、三个角；边是否相等不能一概而论；直角三角形只有一个直角；三角形至少有三个角。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-MA-012', 'QST-MA-000012', 'MULTI_CHOICE', 'MA', 'KPT-MA-000030',
   '要表示一个星期里每天读书的多少，最适合用哪种统计图？',
   '[{"key":"A","label":"条形统计图"},{"key":"B","label":"折线统计图"},{"key":"C","label":"扇形统计图"},{"key":"D","label":"都不合适"}]',
   'A', NULL,
   '条形统计图用直条的长短表示数量的多少，便于比较不同项目的多少，适合「每天读书多少」这类比较。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  -- ---------------- 英语语法 ----------------
  ('ST-QST-EN-001', 'QST-EN-000001', 'GRAMMAR', 'EN', 'KPT-EN-000023',
   'My sister ____ to school every day.',
   NULL, 'goes', NULL,
   '主语 My sister 是第三人称单数，一般现在时动词要加 -es，所以填 goes。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-EN-002', 'QST-EN-000002', 'GRAMMAR', 'EN', 'KPT-EN-000023',
   'They ____ football on Sunday.',
   NULL, 'play', NULL,
   '主语 They 是复数，一般现在时动词用原形，所以填 play。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-EN-003', 'QST-EN-000003', 'MULTI_CHOICE', 'EN', 'KPT-EN-000024',
   'I ____ a student.',
   '[{"key":"A","label":"am"},{"key":"B","label":"is"},{"key":"C","label":"are"},{"key":"D","label":"be"}]',
   'A', NULL,
   '主语 I 后面跟 be 动词要用 am，所以填 am。',
   1, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-EN-004', 'QST-EN-000004', 'GRAMMAR', 'EN', 'KPT-EN-000024',
   'She ____ her homework every evening.',
   NULL, 'does', NULL,
   '实义动词做谓语时第三人称单数用 does；这里句子缺谓语，所以填 does。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-EN-005', 'QST-EN-000005', 'GRAMMAR', 'EN', 'KPT-EN-000025',
   'You can ____ English very well.',
   NULL, 'speak', NULL,
   'can 是情态动词，后面跟动词原形，所以填 speak。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-EN-006', 'QST-EN-000006', 'GRAMMAR', 'EN', 'KPT-EN-000026',
   'There ____ two books on the desk.',
   NULL, 'are', NULL,
   'there be 句型看后面**紧挨着**的名词，two books 是复数，所以用 are。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-EN-007', 'QST-EN-000007', 'GRAMMAR', 'EN', 'KPT-EN-000026',
   'There ____ some milk in the cup.',
   NULL, 'is', NULL,
   'milk 是不可数名词，按单数处理，所以用 is。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-EN-008', 'QST-EN-000008', 'GRAMMAR', 'EN', 'KPT-EN-000027',
   'I have two ____ .',
   NULL, 'brothers', NULL,
   'two 后面要用复数名词，brother 的复数是 brothers。',
   3, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  -- ---------------- 语文写作 ----------------
  ('ST-QST-CN-001', 'QST-CN-000001', 'MULTI_CHOICE', 'CN', 'KPT-CN-000021',
   '下面哪一句用了比喻的修辞手法？',
   '[{"key":"A","label":"弯弯的月儿像一只小船"},{"key":"B","label":"他大步流星地走进教室"},{"key":"C","label":"天渐渐黑了下来"},{"key":"D","label":"桃花潭水深千尺"}]',
   'A', NULL,
   '比喻是「把 A 比作 B」，A 句用「像」把月儿比作小船；B 句是夸张，C 句是陈述，D 句是引用。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-CN-002', 'QST-CN-000002', 'MULTI_CHOICE', 'CN', 'KPT-CN-000022',
   '下面哪一项不是说明文常用的说明方法？',
   '[{"key":"A","label":"举例子"},{"key":"B","label":"打比方"},{"key":"C","label":"列数字"},{"key":"D","label":"抒发感受"}]',
   'D', NULL,
   '举例子、打比方、列数字都是说明方法；抒发感受属于抒情表达，是记叙或议论的成分，不是说明方法。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-CN-003', 'QST-CN-000003', 'MULTI_CHOICE', 'CN', 'KPT-CN-000023',
   '写一篇记叙文，开头最合适的做法是？',
   '[{"key":"A","label":"开门见山，点明要写的事"},{"key":"B","label":"大段罗列景物"},{"key":"C","label":"先把结尾写完"},{"key":"D","label":"照抄范文开头"}]',
   'A', NULL,
   '记叙文开头宜简洁点题，让读者立刻知道要写什么；大段景物容易拖沓，照抄范文则是套作。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-CN-004', 'QST-CN-000004', 'MULTI_CHOICE', 'CN', 'KPT-CN-000024',
   '写人物时，最能打动读者的细节是？',
   '[{"key":"A","label":"人物具体的动作和语言"},{"key":"B","label":"反复写「我非常感动」"},{"key":"C","label":"大段评价人物的品质"},{"key":"D","label":"用很多省略号"}]',
   'A', NULL,
   '细节要靠具体的动作、语言呈现，读者才能看见；直接喊「感动」是空洞评价。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-CN-005', 'QST-CN-000005', 'MULTI_CHOICE', 'CN', 'KPT-CN-000025',
   '作文结尾最忌讳的是？',
   '[{"key":"A","label":"空喊口号，说教套话"},{"key":"B","label":"点明自己的真实感受"},{"key":"C","label":"呼应开头的内容"},{"key":"D","label":"略写结尾，留出余味"}]',
   'A', NULL,
   '结尾宜自然收束、点明感受或呼应开头；空喊口号会显得空洞虚假。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-CN-006', 'QST-CN-000006', 'EXACT', 'CN', 'KPT-CN-000025',
   '记叙文的六要素（时间、地点、人物、起因、经过、结果）通常统称为什么？',
   NULL, '六要素', NULL,
   '记叙文六要素包括时间、地点、人物、起因、经过、结果，合称六要素。',
   2, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  -- ---------------- 单词 ----------------
  ('ST-QST-EN-W01', 'QST-EN-000021', 'WORD', 'EN', 'KPT-EN-000021',
   '单词 apple 的中文意思是？',
   NULL, '苹果', NULL,
   'apple 苹果；它的复数是 apples。',
   4, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-EN-W02', 'QST-EN-000022', 'WORD', 'EN', 'KPT-EN-000021',
   '单词 banana 的中文意思是？',
   NULL, '香蕉', NULL,
   'banana 香蕉。',
   4, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-EN-W03', 'QST-EN-000023', 'WORD', 'EN', 'KPT-EN-000022',
   '单词 library 的中文意思是？',
   NULL, '图书馆', NULL,
   'library 图书馆；a library 表示一所图书馆。',
   4, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-EN-W04', 'QST-EN-000024', 'WORD', 'EN', 'KPT-EN-000022',
   '单词 teacher 的中文意思是？',
   NULL, '老师', NULL,
   'teacher 老师；对应 learner 学习者。',
   4, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-EN-W05', 'QST-EN-000025', 'WORD', 'EN', 'KPT-EN-000022',
   '单词 weather 的中文意思是？',
   NULL, '天气', NULL,
   'weather 天气；与 whether（是否）拼写相近但意思不同。',
   4, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-QST-EN-W06', 'QST-EN-000026', 'WORD', 'EN', 'KPT-EN-000021',
   '单词 pencil 的中文意思是？',
   NULL, '铅笔', NULL,
   'pencil 铅笔；a pencil 表示一支铅笔。',
   4, 'SELF', NULL, 'SELF', NULL, NULL, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `question_type` = VALUES(`question_type`),
  `subject_code` = VALUES(`subject_code`),
  `knowledge_point_code` = VALUES(`knowledge_point_code`),
  `stem` = VALUES(`stem`),
  `options` = VALUES(`options`),
  `answer` = VALUES(`answer`),
  `answer_tolerance` = VALUES(`answer_tolerance`),
  `analysis` = VALUES(`analysis`),
  `difficulty` = VALUES(`difficulty`),
  `status` = VALUES(`status`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin',
  `update_time` = NOW();

-- =============================================================================
-- 4. 词表 study_word
--    课内词挂教材版本（人教版小学）+ 单元；课外词不挂教材版本。
--    line_type：IN = 课内线，OUT = 课外线
-- =============================================================================
INSERT INTO `study_word`
  (`id`, `word_code`, `spelling`, `phonetic`, `meaning`, `line_type`, `textbook_version_id`, `unit_code`,
   `unit_order`, `knowledge_point_code`, `pos`, `context_sentence`, `frequency`, `memory_strength`,
   `decay_lambda`, `feedback_count`, `last_feedback`, `known_flag`, `next_review_at`, `status`, `del_flag`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  -- 课内 U01（水果）
  ('ST-WRD-001', 'WRD-000001', 'apple', '/ˈæpl/', '苹果', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U01', 1, 'KPT-EN-000021', 'n.', 'I eat an apple every morning.', 10, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-002', 'WRD-000002', 'banana', '/bəˈnɑːnə/', '香蕉', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U01', 2, 'KPT-EN-000021', 'n.', 'The monkey likes bananas.', 10, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-003', 'WRD-000003', 'orange', '/ˈɒrɪndʒ/', '橙子', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U01', 3, 'KPT-EN-000021', 'n.', 'This orange is sweet.', 9, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-004', 'WRD-000004', 'pear', '/peə/', '梨', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U01', 4, 'KPT-EN-000021', 'n.', 'She wants a pear.', 9, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-005', 'WRD-000005', 'grape', '/ɡreɪp/', '葡萄', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U01', 5, 'KPT-EN-000021', 'n.', 'Grapes grow on the vine.', 8, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-006', 'WRD-000006', 'peach', '/piːtʃ/', '桃子', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U01', 6, 'KPT-EN-000021', 'n.', 'The peach is very juicy.', 8, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-007', 'WRD-000007', 'lemon', '/ˈlemən/', '柠檬', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U01', 7, 'KPT-EN-000021', 'n.', 'This lemon is sour.', 7, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-008', 'WRD-000008', 'melon', '/ˈmelən/', '甜瓜', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U01', 8, 'KPT-EN-000021', 'n.', 'We buy a melon in summer.', 7, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  -- 课内 U02（文具）
  ('ST-WRD-009', 'WRD-000009', 'pencil', '/ˈpensl/', '铅笔', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U02', 1, 'KPT-EN-000021', 'n.', 'This pencil is new.', 10, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-010', 'WRD-000010', 'pen', '/pen/', '钢笔', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U02', 2, 'KPT-EN-000021', 'n.', 'I write with a pen.', 10, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-011', 'WRD-000011', 'ruler', '/ˈruːlə/', '尺子', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U02', 3, 'KPT-EN-000021', 'n.', 'Draw a line with the ruler.', 9, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-012', 'WRD-000012', 'eraser', '/ɪˈreɪzə/', '橡皮', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U02', 4, 'KPT-EN-000021', 'n.', 'Where is my eraser?', 9, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-013', 'WRD-000013', 'book', '/bʊk/', '书', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U02', 5, 'KPT-EN-000021', 'n.', 'This book is about animals.', 10, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-014', 'WRD-000014', 'bag', '/bæɡ/', '书包', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U02', 6, 'KPT-EN-000021', 'n.', 'My bag is blue.', 10, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-015', 'WRD-000015', 'desk', '/desk/', '课桌', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U02', 7, 'KPT-EN-000021', 'n.', 'The book is on the desk.', 8, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-016', 'WRD-000016', 'chair', '/tʃeə/', '椅子', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U02', 8, 'KPT-EN-000021', 'n.', 'Sit on the chair, please.', 8, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  -- 课内 U03（职业）
  ('ST-WRD-017', 'WRD-000017', 'teacher', '/ˈtiːtʃə/', '老师', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U03', 1, 'KPT-EN-000021', 'n.', 'Our teacher is very kind.', 10, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-018', 'WRD-000018', 'student', '/ˈstjuːdnt/', '学生', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U03', 2, 'KPT-EN-000021', 'n.', 'I am a student.', 10, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-019', 'WRD-000019', 'doctor', '/ˈdɒktə/', '医生', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U03', 3, 'KPT-EN-000021', 'n.', 'The doctor helps sick people.', 9, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-020', 'WRD-000020', 'nurse', '/nɜːs/', '护士', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U03', 4, 'KPT-EN-000021', 'n.', 'The nurse works at night.', 8, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-021', 'WRD-000021', 'driver', '/ˈdraɪvə/', '司机', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U03', 5, 'KPT-EN-000021', 'n.', 'The driver drives a bus.', 9, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-022', 'WRD-000022', 'farmer', '/ˈfɑːmə/', '农民', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U03', 6, 'KPT-EN-000021', 'n.', 'The farmer works on the farm.', 8, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-023', 'WRD-000023', 'worker', '/ˈwɜːkə/', '工人', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U03', 7, 'KPT-EN-000021', 'n.', 'The worker builds houses.', 8, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-024', 'WRD-000024', 'cook', '/kʊk/', '厨师', 'IN', '6b36a3390df28f636ebf1b9470a522c8', 'U03', 8, 'KPT-EN-000021', 'n.', 'My uncle is a cook.', 7, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  -- 课外线
  ('ST-WRD-025', 'WRD-000025', 'library', '/ˈlaɪbrəri/', '图书馆', 'OUT', NULL, NULL, NULL, 'KPT-EN-000022', 'n.', 'We read books in the library.', 6, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-026', 'WRD-000026', 'weather', '/ˈweðə/', '天气', 'OUT', NULL, NULL, NULL, 'KPT-EN-000022', 'n.', 'The weather is fine today.', 6, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-027', 'WRD-000027', 'window', '/ˈwɪndəʊ/', '窗户', 'OUT', NULL, NULL, NULL, 'KPT-EN-000022', 'n.', 'Please open the window.', 6, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-028', 'WRD-000028', 'garden', '/ˈɡɑːdn/', '花园', 'OUT', NULL, NULL, NULL, 'KPT-EN-000022', 'n.', 'There are many flowers in the garden.', 5, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-029', 'WRD-000029', 'hospital', '/ˈhɒspɪtl/', '医院', 'OUT', NULL, NULL, NULL, 'KPT-EN-000022', 'n.', 'The hospital is near our school.', 6, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-030', 'WRD-000030', 'museum', '/mjuˈziːəm/', '博物馆', 'OUT', NULL, NULL, NULL, 'KPT-EN-000022', 'n.', 'We visited a museum last Sunday.', 5, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-031', 'WRD-000031', 'station', '/ˈsteɪʃn/', '车站', 'OUT', NULL, NULL, NULL, 'KPT-EN-000022', 'n.', 'The station is next to the park.', 5, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-032', 'WRD-000032', 'market', '/ˈmɑːkɪt/', '市场', 'OUT', NULL, NULL, NULL, 'KPT-EN-000022', 'n.', 'My mother goes to the market.', 5, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-033', 'WRD-000033', 'holiday', '/ˈhɒlədeɪ/', '假期', 'OUT', NULL, NULL, NULL, 'KPT-EN-000022', 'n.', 'We had a long holiday.', 5, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-034', 'WRD-000034', 'weekend', '/ˌwiːkˈend/', '周末', 'OUT', NULL, NULL, NULL, 'KPT-EN-000022', 'n.', 'I read books on the weekend.', 6, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-035', 'WRD-000035', 'festival', '/ˈfestɪvl/', '节日', 'OUT', NULL, NULL, NULL, 'KPT-EN-000022', 'n.', 'The Spring Festival is my favourite.', 4, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-WRD-036', 'WRD-000036', 'present', '/ˈpreznt/', '礼物', 'OUT', NULL, NULL, NULL, 'KPT-EN-000022', 'n.', 'I got a present on my birthday.', 5, 0.0000, 2.0000, 0, NULL, 0, NULL, 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `spelling` = VALUES(`spelling`),
  `phonetic` = VALUES(`phonetic`),
  `meaning` = VALUES(`meaning`),
  `line_type` = VALUES(`line_type`),
  `textbook_version_id` = VALUES(`textbook_version_id`),
  `unit_code` = VALUES(`unit_code`),
  `unit_order` = VALUES(`unit_order`),
  `knowledge_point_code` = VALUES(`knowledge_point_code`),
  `pos` = VALUES(`pos`),
  `context_sentence` = VALUES(`context_sentence`),
  `frequency` = VALUES(`frequency`),
  `status` = VALUES(`status`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin',
  `update_time` = NOW();

-- =============================================================================
-- 5. 业务 ID 序列（把序列抬到种子编号之上，避免运行时撞号）
-- =============================================================================
INSERT INTO `study_id_sequence` (`id`, `prefix`, `next_value`, `step`, `del_flag`)
VALUES
  ('ST-SEQ-KPT-DEMO', 'KPT', 1000, 1, 0),
  ('ST-SEQ-MTH-DEMO', 'MTH', 1000, 1, 0),
  ('ST-SEQ-WRD-DEMO', 'WRD', 1000, 1, 0)
ON DUPLICATE KEY UPDATE
  `next_value` = GREATEST(`next_value`, VALUES(`next_value`)),
  `step` = VALUES(`step`);
