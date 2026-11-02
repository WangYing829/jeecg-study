-- ============================================================
-- jeecg-study · 写作情境库页面菜单（增量脚本，F-WRT-03）
-- 目的：把已经写好的前端页面 `study/writing/scenario/index` 挂进菜单树，
--       否则页面有代码却**没有路由**，用户在界面上永远进不去。
-- 背景：`07-seed-role-menu.sql` 里只登记了 5 个孩子端页面，情境库页是后补的，
--       漏了菜单。铁律 2：变更走增量脚本，不改历史脚本，所以这里新增本文件。
-- 幂等：INSERT ... ON DUPLICATE KEY UPDATE，可重复执行。
-- 执行顺序：07-seed-role-menu.sql 之后。
-- ============================================================

-- ----------------------------------------------------------------- 1. 页面菜单
-- 挂在「学习助手」下，与「语文写作」平级（sort_no 4.50，排在语文写作 4.00 与护眼 5.00 之间）。
-- 选平级而不是子级：语文写作是叶子页（is_leaf=1），把它改成父节点会牵动既有路由树；
-- 情境库本身是独立学习模块，平级最省事也最稳。
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `menu_type`, `perms`, `perms_type`,
   `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `create_by`, `create_time`, `del_flag`, `status`)
VALUES
  ('st_menu_writing_scenario', 'st_menu_study', '写作情境', '/study/writing/scenario', 'study/writing/scenario/index', 1, 'StudyWritingScenario', 1, NULL, '0',
   4.50, 0, 'ant-design:experiment-outlined', 1, 0, 0, 'admin', NOW(), 0, '1')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `url` = VALUES(`url`), `component` = VALUES(`component`),
  `component_name` = VALUES(`component_name`), `sort_no` = VALUES(`sort_no`), `icon` = VALUES(`icon`);

-- ----------------------------------------------------------------- 2. 角色授权
-- 孩子角色：情境库页面本身 + 它用到的两个权限码。
-- 权限码 `study:writing:view` / `study:writing:train` 已在 07 号脚本里授给 study_kid，
-- 这里只补「页面」这一条路由授权，不需要新建按钮权限行。
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `operate_date`, `operate_ip`)
VALUES
  ('st_rp_kid_m_writing_scenario', 'st_role_kid', 'st_menu_writing_scenario', NOW(), '127.0.0.1')
ON DUPLICATE KEY UPDATE `operate_date` = VALUES(`operate_date`);
