-- =============================================================================
-- 19-seed-admin-study-menu.sql · 增量：把 study 菜单与权限授权给 admin（平台管理员）
-- =============================================================================
-- 缺陷来源（2026-11-01 管理端截图标注）：
--   「左侧边栏找不到『学习助手』『家长端』菜单」。
--
-- 根因（已逐条排除，不是数据缺失，也不是 parent_id / component / hidden / status 的问题）：
--   底座 SysPermissionServiceImpl.queryByUser(userId) 纯按
--     sys_user_role → sys_role_permission → sys_permission
--   取菜单，**admin 角色没有任何「兜底给全量」的逻辑**（上游只做角色授权）；
--   而 07 号种子只把 study 菜单绑给了 study_kid（8 条）与 study_parent（4 条），
--   admin 角色一条 study 权限都没有 → 管理端左侧菜单树里根本没有这两个一级菜单。
--   旁证：07 号脚本里的两个一级菜单 parent_id=''（空串）、component='layouts/default/index'、
--   menu_type=0、hidden=0、is_leaf=0、status='1'，与上游能正常显示的一级菜单字段完全一致；
--   前端 transformObjToRoute 对 DB 菜单一律包一层 LAYOUT，路由侧也没问题。
--
-- 本脚本做三件事：
--   1) 给 admin 角色授权全部 study 菜单（menu_type 0/1，共 12 条）
--      → 左侧边栏出现「学习助手」「家长端」两个一级菜单；
--   2) 给 admin 角色授权全部 study 按钮权限（menu_type 2，共 41 条）
--      → 管理端点开页面不会被 Shiro 拦成 403（study 域接口普遍带 @RequiresPermissions）；
--   3) 把 admin 挂进**演示家庭 A**（家庭成员 + 亲子关系）
--      → 管理端打开家长端三页（学习报告 / 错题分析 / 作文反馈）能看到真实演示数据。
--
-- 边界（写清楚，免得被误读成「放开隔离」）：
--   · admin 只是演示家庭 A 里的一个家长，家庭 B 的孩子对 admin 同样不可见；
--     家长 A 访问孩子 B 仍然返回 4010 —— 数据范围隔离红线一点没变。
--   · 学习端 5 个页面（练习 / 背单词 / 英语语法 / 语文写作 / 护眼）面向**孩子身份**，
--     admin 在管理端打开会命中 4010（没有孩子身份），这是设计内行为，不是缺陷。
--   · 本脚本**没有**改 ChildScopeServiceImpl：隔离语义仍然是「关系表求交集」，不加管理员旁路。
--
-- 幂等：全部 INSERT ... SELECT ... WHERE NOT EXISTS，重复执行不报错、不重复插。
--   sys_role_permission 主键 id 是 varchar(32)，而权限 id 最长 30 字符，
--   直接拼前缀会超长截断 → 用 MD5 前缀生成确定性主键（同样长度 23 字符）。
--   走 SELECT 而不是写死 53 行：以后新增 study 菜单/按钮，重跑本脚本即自动补授权。
-- =============================================================================

-- ----------------------------------------------------------------- 0. 变量（按 role_code / username 取，不写死 id）
SET @admin_role_id = (SELECT `id` FROM `sys_role` WHERE `role_code` = 'admin' LIMIT 1);
SET @admin_user_id = (SELECT `id` FROM `sys_user` WHERE `username` = 'admin' LIMIT 1);

-- ----------------------------------------------------------------- 1. 菜单 + 按钮权限授权
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

-- ----------------------------------------------------------------- 2. admin 进演示家庭 A（家庭成员）
-- member_role 取值域是 OWNER / PARENT（见 StudyConstants 与 StudyFamilyMember 实体注释），
-- admin 不是建家庭的人，所以是 PARENT；演示家长仍是唯一的 OWNER（is_primary=1）。
INSERT INTO `study_family_member`
  (`id`, `family_id`, `user_id`, `member_role`, `relation_label`, `status`, `del_flag`,
   `create_by`, `create_time`, `update_by`, `update_time`)
SELECT
  'ST-FAMMEMBER-DEMO-ADMIN', 'ST-FAMILY-DEMO-01', @admin_user_id, 'PARENT', '管理员', 1, 0,
  'admin', NOW(), 'admin', NOW()
WHERE @admin_user_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM (SELECT `family_id`, `user_id` FROM `study_family_member`) m
    WHERE m.`family_id` = 'ST-FAMILY-DEMO-01'
      AND m.`user_id` = @admin_user_id
  );

-- ----------------------------------------------------------------- 3. admin 与演示孩子的亲子关系
-- is_primary=0：主家长仍是演示家长，避免顶掉「妈妈」这个主关系。
INSERT INTO `study_parent_child_rel`
  (`id`, `parent_user_id`, `child_id`, `relation_label`, `is_primary`, `status`, `del_flag`,
   `create_by`, `create_time`, `update_by`, `update_time`)
SELECT
  'ST-PCREL-DEMO-ADMIN', @admin_user_id, 'ST-CHILD-DEMO-01', '管理员', 0, 1, 0,
  'admin', NOW(), 'admin', NOW()
WHERE @admin_user_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM (SELECT `parent_user_id`, `child_id` FROM `study_parent_child_rel`) r
    WHERE r.`parent_user_id` = @admin_user_id
      AND r.`child_id` = 'ST-CHILD-DEMO-01'
  );
