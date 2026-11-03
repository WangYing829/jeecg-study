-- =============================================================================
-- 22-seed-study-interface-pages-menu.sql · 增量：§11.2「尚未上线的界面」全部 4 类
-- =============================================================================
-- 背景（《用户手册 · 系统功能使用说明》§11.2）：
--   此前有 4 类界面「接口已就绪、界面待开发」——
--     ① 孩子端登录页（两步：家庭码 → 选孩子 + PIN）
--     ② 家庭与成员管理
--     ③ 孩子档案编辑 / 关联管理
--     ④ 管理端内容维护页 9 个
--   本轮这 13 个页面的前端代码已落地（见基座 overlay 清单），
--   本脚本把其中**需要进菜单树**的 12 条补进 sys_permission。
--
-- 铁律 2：变更走增量脚本，不改历史脚本（07 / 16 / 17 / 19 / 20 / 21 号都不动）。
-- 幂等：全部 INSERT ... ON DUPLICATE KEY UPDATE + INSERT ... SELECT ... WHERE NOT EXISTS，
--       重复执行不报错、不重复插。
-- 执行顺序：07-seed-role-menu.sql → 19-seed-admin-study-menu.sql → 本脚本。
--
-- ─────────────────────────────────────────────────────────────────────────────
-- 重要：孩子端登录页**不建菜单**（唯一例外）
-- ─────────────────────────────────────────────────────────────────────────────
--   孩子没有账号密码，只有家庭码 + PIN，所以登录页必须在**无登录态**下可达。
--   而 JeecgBoot 的动态菜单（sys_permission → queryByUser）**必须先有 token** 才下发，
--   挂在菜单里的页面孩子根本进不去。
--   因此这一页走**静态路由**：jeecgboot-vue3/src/router/routes/index.ts 的
--   StudyKidLoginRoute（meta.ignoreAuth = true），已随基座改动入库。
--   ⚠ 不要为了「凑齐菜单」再给它插一条 sys_permission：
--     静态路由与动态菜单会注册出**同一个 path**（/study/kid/login），
--     vue-router 出现重复路由，后注册的会盖掉前一个，行为不可预期。
--
-- ─────────────────────────────────────────────────────────────────────────────
-- 路由参数说明（DB 菜单的 url 直接就是 vue-router 的 path）
-- ─────────────────────────────────────────────────────────────────────────────
--   后端把 sys_permission.url 原样下发成 menu.path（SysPermissionController:794），
--   前端 joinParentPath 只在 path **不以 / 开头**时才拼父路径——
--   本脚本所有 url 都以 / 开头，因此写绝对路径，与上游既有菜单口径一致。
--   带 :code 的两级路由（考点维护 / 考点版本历史 / 关联管理）**必须 hidden = 1**：
--   它们是上级页面的下一级，侧边栏不该出现一个字面量 ":code" 的链接。
--
-- ─────────────────────────────────────────────────────────────────────────────
-- 权限码说明
-- ─────────────────────────────────────────────────────────────────────────────
--   本脚本**不新增按钮权限**：9 个管理端页面用到的权限码在 07 号脚本里已经存在
--   （study:parent:context:* / study:parent:knowledge:* / study:parent:textbook:query /
--     study:parent:child:query / study:math:view / study:kid:word:view /
--     study:kid:grammar:view / study:writing:view），
--   家长端 3 页用到的 study:parent:family:* / study:parent:child:* 也已存在。
--   本脚本只做**菜单可见性**与**角色授权**。
--
-- 授权口径：
--   · 家长端 3 页 → study_parent（家长）+ admin（平台管理员演示）
--   · 管理端 9 页 → 仅 admin（内容维护是管理端职责，家长端不出现）
--   · admin 的授权用「按 id 列表补齐」的写法，不依赖重跑 19 号脚本
--     （19 号是历史脚本，本轮不改它；重跑它虽然也能补上，但把新脚本的完整性
--       寄托在另一个脚本的 WHERE 条件上太脆）。
-- =============================================================================

-- ----------------------------------------------------------------- 0. 变量
SET @admin_role_id = (SELECT `id` FROM `sys_role` WHERE `role_code` = 'admin' LIMIT 1);
SET @parent_role_id = (SELECT `id` FROM `sys_role` WHERE `role_code` = 'study_parent' LIMIT 1);

-- ----------------------------------------------------------------- 1. 家长端 3 页
-- 挂在「家长端」目录下（st_menu_parent）。
-- 侧边栏之外，家长端外壳 StudyParentShell 自带顶部导航（无侧栏，顶部条是唯一入口），
-- 两个入口都指向同一批页面。
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `menu_type`, `perms`, `perms_type`,
   `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `create_by`, `create_time`, `del_flag`, `status`)
VALUES
  ('st_menu_parent_family', 'st_menu_parent', '家庭成员', '/study-parent/family', 'study/parent/family/index', 1, 'StudyParentFamilyPage', 1, NULL, '0',
   4.00, 0, 'ant-design:team-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_parent_child', 'st_menu_parent', '孩子档案', '/study-parent/child', 'study/parent/child/index', 1, 'StudyParentChildProfilePage', 1, NULL, '0',
   5.00, 0, 'ant-design:idcard-twotone', 1, 0, 0, 'admin', NOW(), 0, '1'),
  -- 关联管理是「孩子档案」的下一级，路径带 :code ⇒ 侧边栏不展示（hidden=1）
  ('st_menu_parent_child_rel', 'st_menu_parent', '关联管理', '/study-parent/child/:code/relations', 'study/parent/child/relations', 1, 'StudyParentChildRelationPage', 1, NULL, '0',
   6.00, 0, 'ant-design:branches-outlined', 1, 0, 1, 'admin', NOW(), 0, '1')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `url` = VALUES(`url`), `component` = VALUES(`component`),
  `component_name` = VALUES(`component_name`), `sort_no` = VALUES(`sort_no`),
  `icon` = VALUES(`icon`), `hidden` = VALUES(`hidden`),
  `parent_id` = VALUES(`parent_id`), `is_leaf` = VALUES(`is_leaf`);

-- ----------------------------------------------------------------- 2. 管理端目录
-- 新增一级目录「内容维护」（sort_no 50.00，排在「家长端」40.00 之后）。
-- 为什么要独立一级目录而不是塞进「学习助手」：
--   管理端页面维护的是**全局共享内容**，与学习端（面向孩子身份）、家长端（面向家长身份）
--   是三类不同受众，混在一棵树上会让两端都多出一堆不该有的入口。
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `menu_type`, `perms`, `perms_type`,
   `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `create_by`, `create_time`, `del_flag`, `status`)
VALUES
  ('st_menu_manage', '', '内容维护', '/study-manage', 'layouts/default/index', 1, 0, NULL, '0',
   50.00, 0, 'ant-design:appstore-outlined', 0, 0, 0, 'admin', NOW(), 0, '1')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `url` = VALUES(`url`), `icon` = VALUES(`icon`), `sort_no` = VALUES(`sort_no`);

-- ----------------------------------------------------------------- 3. 管理端 9 页
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `menu_type`, `perms`, `perms_type`,
   `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `create_by`, `create_time`, `del_flag`, `status`)
VALUES
  ('st_menu_manage_context', 'st_menu_manage', '上下文配置', '/study/manage/context', 'study/manage/context/index', 1, 'StudyManageContextPage', 1, NULL, '0',
   1.00, 0, 'ant-design:setting-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_manage_coordinate', 'st_menu_manage', '坐标树', '/study/manage/coordinate', 'study/manage/coordinate/index', 1, 'StudyManageCoordinatePage', 1, NULL, '0',
   2.00, 0, 'ant-design:deployment-unit-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  -- 考点维护 / 版本历史挂在「内容维护」下（**不挂「坐标树」**）——理由见文件末尾 §6
  -- ⚠ id 长度上限 32（sys_permission.id 是 varchar(32)），这里用短名不用长名
  ('st_menu_manage_point', 'st_menu_manage', '考点维护', '/study/manage/coordinate/:code', 'study/manage/coordinate/detail', 1, 'StudyManageCoordinateDetailPage', 1, NULL, '0',
   3.00, 0, 'ant-design:partition-outlined', 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_menu_manage_point_hist', 'st_menu_manage', '考点版本历史', '/study/manage/coordinate/:code/history', 'study/manage/coordinate/history', 1, 'StudyManageCoordinateHistoryPage', 1, NULL, '0',
   4.00, 0, 'ant-design:history-outlined', 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_menu_manage_question', 'st_menu_manage', '题目维护', '/study/manage/question', 'study/manage/question/index', 1, 'StudyManageQuestionPage', 1, NULL, '0',
   5.00, 0, 'ant-design:form-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_manage_mother', 'st_menu_manage', '母题维护', '/study/manage/mother', 'study/manage/mother/index', 1, 'StudyManageMotherPage', 1, NULL, '0',
   6.00, 0, 'ant-design:rocket-filled', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_manage_word', 'st_menu_manage', '词表维护', '/study/manage/word', 'study/manage/word/index', 1, 'StudyManageWordPage', 1, NULL, '0',
   7.00, 0, 'ant-design:book-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_manage_grammar', 'st_menu_manage', '语法点维护', '/study/manage/grammar', 'study/manage/grammar/index', 1, 'StudyManageGrammarPage', 1, NULL, '0',
   8.00, 0, 'ant-design:translation-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_manage_writing', 'st_menu_manage', '拆解库维护', '/study/manage/writing', 'study/manage/writing/index', 1, 'StudyManageWritingPage', 1, NULL, '0',
   9.00, 0, 'ant-design:file-word-outlined', 1, 0, 0, 'admin', NOW(), 0, '1')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `url` = VALUES(`url`), `component` = VALUES(`component`),
  `component_name` = VALUES(`component_name`), `sort_no` = VALUES(`sort_no`),
  `icon` = VALUES(`icon`), `hidden` = VALUES(`hidden`),
  -- parent_id / is_leaf 必须一并回写：菜单树是按 parent_id 递归的，
  -- 存量库里如果 parent_id 挂在「坐标树」下、而坐标树 is_leaf=1，
  -- 这两条菜单会被**静默丢掉**（路由不注册 → 页面 404），重跑本脚本也救不回来。
  `parent_id` = VALUES(`parent_id`), `is_leaf` = VALUES(`is_leaf`);

-- ----------------------------------------------------------------- 4. 角色授权
-- 家长端 3 页 → study_parent（家长）
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `operate_date`, `operate_ip`)
SELECT
  CONCAT('st_rpp_', LEFT(MD5(CONCAT('study_parent|', p.`id`)), 16)),
  @parent_role_id,
  p.`id`,
  NOW(),
  '127.0.0.1'
FROM `sys_permission` p
WHERE p.`id` IN ('st_menu_parent_family', 'st_menu_parent_child', 'st_menu_parent_child_rel')
  AND @parent_role_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM (SELECT `role_id`, `permission_id` FROM `sys_role_permission`) rp
    WHERE rp.`role_id` = @parent_role_id
      AND rp.`permission_id` = p.`id`
  );

-- 家长端 3 页 + 管理端目录与 9 页 → admin（平台管理员）
-- admin 的按钮权限在 19 号脚本里已全量授权（study 域全部 st_btn 行），
-- 这里只补**菜单可见性**。
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `operate_date`, `operate_ip`)
SELECT
  CONCAT('st_rpa_', LEFT(MD5(CONCAT('admin|', p.`id`)), 16)),
  @admin_role_id,
  p.`id`,
  NOW(),
  '127.0.0.1'
FROM `sys_permission` p
WHERE p.`id` IN (
    'st_menu_parent_family', 'st_menu_parent_child', 'st_menu_parent_child_rel',
    'st_menu_manage',
    'st_menu_manage_context', 'st_menu_manage_coordinate',
    'st_menu_manage_point', 'st_menu_manage_point_hist',
    'st_menu_manage_question', 'st_menu_manage_mother',
    'st_menu_manage_word', 'st_menu_manage_grammar', 'st_menu_manage_writing'
  )
  AND @admin_role_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM (SELECT `role_id`, `permission_id` FROM `sys_role_permission`) rp
    WHERE rp.`role_id` = @admin_role_id
      AND rp.`permission_id` = p.`id`
  );

-- ----------------------------------------------------------------- 5. 菜单树形状自愈
-- ⚠ 下面这段是本脚本**最容易被忽略、后果最严重**的一条规则，必须写在这里：
--
--   JeecgBoot 的菜单树不是按 parent_id 无条件递归的，
--   SysPermissionController#getPermissionJsonArray 里写的是：
--
--       if (parentJson == null && isEmpty(tempPid)) {
--           jsonArray.add(json);
--           if (!permission.isLeaf()) {            // ← 只有 is_leaf=0 才递归下去
--               getPermissionJsonArray(jsonArray, metaList, json);
--           }
--       }
--
--   也就是说：**父菜单 is_leaf=1（叶子）时，它下面的子菜单根本不会被遍历到**，
--   菜单树里查无此节点 → 前端拿不到这条路由 → 打开该路径直接 404，
--   而且**不报任何错**（没有 4xx、没有 console error、页面上只有一张 404 插画）。
--
--   本脚本第一版把「考点维护 / 考点版本历史」挂在「坐标树」下面，
--   而「坐标树」同时被写成 is_leaf=1 ⇒ 两条菜单被**静默丢弃**，
--   浏览器实测时点开 /study/manage/coordinate/:code 才发现是 404。
--
--   修法有两条，本脚本采用第 ② 条：
--     ① 把父菜单的 is_leaf 改成 0（父菜单自己也是页面、又有子菜单，路由树会多一层壳）；
--     ② 把带 :code 的菜单直接挂在**目录**（st_menu_manage）下面，与其它页面平级 ——
--        这正是「关联管理」在家长端分支里已经跑通的写法（挂在 st_menu_parent 下），
--        而且它们的 url 是**绝对路径**，前端 joinParentPath 不会拼接父路径，路由注册正常。
--
--   下面这条 UPDATE 按「实际有没有子节点」重算 is_leaf，作为兜底自愈：
--   以后再加子菜单忘了改 is_leaf，重跑本脚本就能修回来。
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
