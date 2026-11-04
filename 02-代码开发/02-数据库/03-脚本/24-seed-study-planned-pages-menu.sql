-- =============================================================================
-- 24-seed-study-planned-pages-menu.sql · 增量：21 个规划页（含 1 个组件）+ 家长端 2 个拆分页
-- =============================================================================
-- 背景（2026-11-04 全量盘点）：
--   《前端页面清单》§2.2 规划 40 个页面，§2.1 已上线 23 个，
--   真实差集 **21 个**（按域拆分）：
--     · 学习端 18 个 —— 首页/复习 2 + 错题线 4 + 单词线 4 + 数学线 5 + 语法线 3
--     · 家长端  2 个 —— 周报 / 薄弱考点（原为总览页内区块，本轮拆为独立页）
--     · 组件    1 个 —— ChildSwitcher（孩子选择器，**组件不是页面 ⇒ 不建菜单**）
--   这批页面的前端代码与后端接口均已落地，本脚本把它们补进 sys_permission。
--
-- ⚠ 序号说明：本脚本原拟编 23，但 23 号已被
--   「23-seed-notification-preference-and-ai-feedback.sql」占用
--   （家长提醒偏好 + 解析申诉建表）⇒ 按「序号项目内全局唯一」规约改为 24。
--
-- 铁律 2：变更走增量脚本，不改历史脚本（07 / 16 / 17 / 19 / 20 / 21 / 22 / 23 号都不动）。
-- 幂等：全部 INSERT ... ON DUPLICATE KEY UPDATE + INSERT ... SELECT ... WHERE NOT EXISTS，
--       重复执行不报错、不重复插。
-- 执行顺序：07-seed-role-menu.sql → 19-seed-admin-study-menu.sql → 22 号 → 23 号 → 本脚本。
--
-- ─────────────────────────────────────────────────────────────────────────────
-- 路由参数说明（沿用 22 号的口径，理由见该脚本）
-- ─────────────────────────────────────────────────────────────────────────────
--   · 后端把 sys_permission.url 原样下发成 menu.path（SysPermissionController:794），
--     前端 joinParentPath 只在 path **不以 / 开头**时才拼父路径 ⇒ 本脚本 url 全用绝对路径。
--   · 带 :code / :unitCode 的路由**必须 hidden = 1**：它们是上级页面的下一级，
--     侧边栏不该出现一个字面量 ":code" 的链接。
--   · 学习端页面的 parent_id 统一挂 **st_menu_study**（「学习助手」一级目录），
--     家长端页面的 parent_id 统一挂 **st_menu_parent**（「家长端」一级目录）。
--
-- ─────────────────────────────────────────────────────────────────────────────
-- sort_no 占用（2026-11-04 实测库内现状，写死前必须先查）
-- ─────────────────────────────────────────────────────────────────────────────
--   st_menu_study 子项已占用：1.00 练习 / 2.00 背单词 / 3.00 英语语法 /
--                             3.50 语法玩法 / 4.00 语文写作 / 4.50 写作情境 / 5.00 护眼
--   ⇒ 本脚本新增的 18 个学习端页面从 **6.00** 起排（6.00…23.00），
--     **不得从 4.00 起**（会与「语文写作 4.00」「护眼 5.00」撞号 ⇒ 排序不确定）。
--   st_menu_parent 子项已占用：1.00…6.00 ⇒ 本脚本新增的 2 页从 **7.00** 起排。
--
-- ─────────────────────────────────────────────────────────────────────────────
-- 权限码说明
-- ─────────────────────────────────────────────────────────────────────────────
--   本脚本**不新增按钮权限**：这批页面用到的权限码在 07 / 19 号脚本里已存在
--   （study:kid:* / study:parent:* / study:math:view / study:word:* 等）。
--   本脚本只做**菜单可见性**与**角色授权**。
--
-- 授权口径：
--   · 学习端 18 页 → study_kid（孩子）+ admin（平台管理员演示）
--   · 家长端 2 页  → study_parent（家长）+ admin
--   · 孩子选择器是**组件**不是页面 ⇒ 不建菜单（见 §2 末尾说明）
-- =============================================================================

USE `jeecg-study`;

-- ----------------------------------------------------------------- 0. 变量
SET @admin_role_id = (SELECT `id` FROM `sys_role` WHERE `role_code` = 'admin' LIMIT 1);
SET @kid_role_id = (SELECT `id` FROM `sys_role` WHERE `role_code` = 'study_kid' LIMIT 1);
SET @parent_role_id = (SELECT `id` FROM `sys_role` WHERE `role_code` = 'study_parent' LIMIT 1);

-- ----------------------------------------------------------------- 1. 学习端 18 页
-- 挂「学习助手」（st_menu_study）下，sort_no 6.00…23.00（避让已被占用的 1.00…5.00）。
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `menu_type`, `perms`, `perms_type`,
   `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `create_by`, `create_time`, `del_flag`, `status`)
VALUES
  -- 首页 / 复习 2 个
  ('st_menu_kid_home', 'st_menu_study', '今日首页', '/study/kid/home', 'study/kid/home/index', 1, 'StudyKidHomePage', 1, NULL, '0',
   6.00, 0, 'ant-design:home-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_kid_review', 'st_menu_study', '今日复习', '/study/kid/review', 'study/kid/review/index', 1, 'StudyKidReviewPage', 1, NULL, '0',
   7.00, 0, 'ant-design:schedule-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),

  -- 错题线 4 个
  ('st_menu_kid_error', 'st_menu_study', '我的错题本', '/study/kid/error', 'study/kid/error/index', 1, 'StudyKidErrorListPage', 1, NULL, '0',
   8.00, 0, 'ant-design:edit-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_kid_error_import', 'st_menu_study', '错题录入', '/study/kid/error/import', 'study/kid/error/import/index', 1, 'StudyKidErrorImportPage', 1, NULL, '0',
   9.00, 0, 'ant-design:scan-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  -- 错题详情带 :code ⇒ hidden=1
  ('st_menu_kid_error_detail', 'st_menu_study', '错题详情', '/study/kid/error/:code', 'study/kid/error/detail/index', 1, 'StudyKidErrorDetailPage', 1, NULL, '0',
   10.00, 0, 'ant-design:file-search-outlined', 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_menu_kid_error_stat', 'st_menu_study', '错题统计', '/study/kid/error/statistics', 'study/kid/error/statistics/index', 1, 'StudyKidErrorStatisticsPage', 1, NULL, '0',
   11.00, 0, 'ant-design:pie-chart-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),

  -- 单词线 4 个
  ('st_menu_kid_word_unit', 'st_menu_study', '课内单元闯关', '/study/kid/word/unit/:unitCode', 'study/kid/word/unit/index', 1, 'StudyKidWordUnitPage', 1, NULL, '0',
   12.00, 0, 'ant-design:trophy-outlined', 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_menu_kid_word_out', 'st_menu_study', '课外词线', '/study/kid/word/out', 'study/kid/word/out/index', 1, 'StudyKidWordOutdoorPage', 1, NULL, '0',
   13.00, 0, 'ant-design:global-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_kid_word_known', 'st_menu_study', '熟词测试', '/study/kid/word/known-test', 'study/kid/word/known-test/index', 1, 'StudyKidWordKnownTestPage', 1, NULL, '0',
   14.00, 0, 'ant-design:check-square-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_kid_word_stats', 'st_menu_study', '背诵统计', '/study/kid/word/stats', 'study/kid/word/stats/index', 1, 'StudyKidWordStatsPage', 1, NULL, '0',
   15.00, 0, 'ant-design:bar-chart-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),

  -- 数学线 5 个
  ('st_menu_kid_math', 'st_menu_study', '数学闯关', '/study/kid/math', 'study/kid/math/index', 1, 'StudyKidMathHomePage', 1, NULL, '0',
   16.00, 0, 'ant-design:rocket-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_kid_math_mother', 'st_menu_study', '母题关卡', '/study/kid/math/mother/:code', 'study/kid/math/mother/index', 1, 'StudyKidMathMotherPage', 1, NULL, '0',
   17.00, 0, 'ant-design:experiment-outlined', 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_menu_kid_math_teach', 'st_menu_study', '母题讲解', '/study/kid/math/mother/:code/teach', 'study/kid/math/mother/teach/index', 1, 'StudyKidMathMotherTeachPage', 1, NULL, '0',
   18.00, 0, 'ant-design:read-outlined', 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_menu_kid_math_variant', 'st_menu_study', '母题变式练习', '/study/kid/math/variant/:code', 'study/kid/math/variant/index', 1, 'StudyKidMathVariantPage', 1, NULL, '0',
   19.00, 0, 'ant-design:branches-outlined', 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_menu_kid_math_oral', 'st_menu_study', '口述确认', '/study/kid/math/oral/:code', 'study/kid/math/oral/index', 1, 'StudyKidMathOralPage', 1, NULL, '0',
   20.00, 0, 'ant-design:audio-outlined', 1, 0, 1, 'admin', NOW(), 0, '1'),

  -- 语法线 3 个
  ('st_menu_kid_grammar_detail', 'st_menu_study', '语法点详情', '/study/kid/grammar/:code', 'study/kid/grammar/detail/index', 1, 'StudyKidGrammarDetailPage', 1, NULL, '0',
   21.00, 0, 'ant-design:profile-outlined', 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_menu_kid_grammar_transform', 'st_menu_study', '句子变形器', '/study/kid/grammar/transform', 'study/kid/grammar/transform/index', 1, 'StudyKidGrammarTransformPage', 1, NULL, '0',
   22.00, 0, 'ant-design:swap-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_kid_grammar_collection', 'st_menu_study', '错句收藏馆', '/study/kid/grammar/collection', 'study/kid/grammar/collection/index', 1, 'StudyKidGrammarCollectionPage', 1, NULL, '0',
   23.00, 0, 'ant-design:star-outlined', 1, 0, 0, 'admin', NOW(), 0, '1')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `url` = VALUES(`url`), `component` = VALUES(`component`),
  `component_name` = VALUES(`component_name`), `sort_no` = VALUES(`sort_no`),
  `icon` = VALUES(`icon`), `hidden` = VALUES(`hidden`),
  `parent_id` = VALUES(`parent_id`), `is_leaf` = VALUES(`is_leaf`);

-- ----------------------------------------------------------------- 2. 家长端 2 页
-- 挂「家长端」（st_menu_parent）下，sort_no 7.00 / 8.00（避让已被占用的 1.00…6.00）。
-- ⚠ 孩子选择器 ChildSwitcher 是**组件**不是页面 ⇒ **不建菜单**
--   （组件由家长端页面内部使用，没有独立路由）。
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `menu_type`, `perms`, `perms_type`,
   `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `create_by`, `create_time`, `del_flag`, `status`)
VALUES
  ('st_menu_parent_weekly', 'st_menu_parent', '周报', '/study-parent/weekly', 'study/parent/weekly/index', 1, 'StudyParentWeeklyPage', 1, NULL, '0',
   7.00, 0, 'ant-design:calendar-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_parent_weak', 'st_menu_parent', '薄弱考点', '/study-parent/weakpoint', 'study/parent/weakpoint/index', 1, 'StudyParentWeakPointPage', 1, NULL, '0',
   8.00, 0, 'ant-design:alert-outlined', 1, 0, 0, 'admin', NOW(), 0, '1')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `url` = VALUES(`url`), `component` = VALUES(`component`),
  `component_name` = VALUES(`component_name`), `sort_no` = VALUES(`sort_no`),
  `icon` = VALUES(`icon`), `hidden` = VALUES(`hidden`),
  `parent_id` = VALUES(`parent_id`), `is_leaf` = VALUES(`is_leaf`);

-- ----------------------------------------------------------------- 3. 角色授权
-- 学习端 18 页 → study_kid（孩子）
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `operate_date`, `operate_ip`)
SELECT
  CONCAT('st_rpk_', LEFT(MD5(CONCAT('study_kid|', p.`id`)), 16)),
  @kid_role_id,
  p.`id`,
  NOW(),
  '127.0.0.1'
FROM `sys_permission` p
WHERE p.`id` IN (
    'st_menu_kid_home', 'st_menu_kid_review',
    'st_menu_kid_error', 'st_menu_kid_error_import',
    'st_menu_kid_error_detail', 'st_menu_kid_error_stat',
    'st_menu_kid_word_unit', 'st_menu_kid_word_out',
    'st_menu_kid_word_known', 'st_menu_kid_word_stats',
    'st_menu_kid_math', 'st_menu_kid_math_mother', 'st_menu_kid_math_teach',
    'st_menu_kid_math_variant', 'st_menu_kid_math_oral',
    'st_menu_kid_grammar_detail', 'st_menu_kid_grammar_transform', 'st_menu_kid_grammar_collection'
  )
  AND @kid_role_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM (SELECT `role_id`, `permission_id` FROM `sys_role_permission`) rp
    WHERE rp.`role_id` = @kid_role_id
      AND rp.`permission_id` = p.`id`
  );

-- 家长端 2 页 → study_parent（家长）
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `operate_date`, `operate_ip`)
SELECT
  CONCAT('st_rpp_', LEFT(MD5(CONCAT('study_parent|', p.`id`)), 16)),
  @parent_role_id,
  p.`id`,
  NOW(),
  '127.0.0.1'
FROM `sys_permission` p
WHERE p.`id` IN ('st_menu_parent_weekly', 'st_menu_parent_weak')
  AND @parent_role_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM (SELECT `role_id`, `permission_id` FROM `sys_role_permission`) rp
    WHERE rp.`role_id` = @parent_role_id
      AND rp.`permission_id` = p.`id`
  );

-- 学习端 18 页 + 家长端 2 页 → admin（平台管理员演示）
--
-- ⚠ 这里**不写死 20 个 id**，而是按 19 号的口径给 admin 补授**全部** study 菜单与按钮。
--   原因有二：
--     ① 本脚本排在迁移序列**最后**（22 → 23 → 24），23 号新建的按钮
--        （如 st_btn_parent_pref）在 19 号执行时**还不存在** ⇒ 首轮拿不到授权；
--     ② 基座**没有 admin 权限兜底**（SysPermissionServiceImpl 里没有 isAdmin 分支），
--        admin 的权限**只来自 sys_role_permission 行** ⇒ 漏授权就是真 403。
--   id 生成口径与 19 号完全一致（st_rpa_ + MD5('admin|' + 权限id)），
--   重复执行不会产生重复行（NOT EXISTS 按 role_id + permission_id 判重）。
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `operate_date`, `operate_ip`)
SELECT
  CONCAT('st_rpa_', LEFT(MD5(CONCAT('admin|', p.`id`)), 16)),
  @admin_role_id,
  p.`id`,
  NOW(),
  '127.0.0.1'
FROM `sys_permission` p
WHERE (p.`id` LIKE 'st\_menu%' OR p.`id` LIKE 'st\_btn%')
  AND p.`del_flag` = 0
  AND @admin_role_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM (SELECT `role_id`, `permission_id` FROM `sys_role_permission`) rp
    WHERE rp.`role_id` = @admin_role_id
      AND rp.`permission_id` = p.`id`
  );

-- ----------------------------------------------------------------- 4. 菜单树形状自愈
-- 与 22 号脚本同口径：父菜单 is_leaf=1 时，其子菜单会被**静默丢弃**
-- （SysPermissionController#getPermissionJsonArray 只在 is_leaf=0 时递归），
-- 表现为「页面 404 且无任何报错」。按实际有没有子节点重算 is_leaf 作为兜底。
UPDATE `sys_permission` p
SET p.`is_leaf` = (
  SELECT CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
  FROM (
    SELECT `parent_id` FROM `sys_permission` WHERE `del_flag` = 0 AND `status` = '1'
  ) c
  WHERE c.`parent_id` = p.`id`
)
WHERE p.`id` IN (
  SELECT `id` FROM (
    SELECT `id` FROM `sys_permission` WHERE `id` LIKE 'st\_menu\_%'
  ) x
);
