-- =============================================================================
-- 07-seed-role-menu.sql · 运行时种子：角色 + 动态菜单 + 按钮权限 + 角色授权
-- =============================================================================
-- 用途：让 jeecg-study 的两种身份在**真实运行时**有菜单可进、有权限可用。
--
-- 为什么必须有（不是可有可无的演示数据）：
--   JeecgBoot 的前端路由是**动态菜单**（sys_permission 下发），不是前端写死的静态路由；
--   权限校验读的是 `sys_role_permission → sys_permission.perms`。
--   库里没有这两类记录 → 浏览器里进不去任何学习端页面，接口一律 403。
--   上一轮 235 条自动化用例全绿，是因为底座能力（用户 / 角色 / Redis）在测试里是 Mock，
--   这条链路**从未在真实环境跑过**。本脚本补上它。
--
-- 幂等：全部 ON DUPLICATE KEY UPDATE，重复执行不报错、不重复插。
-- 菜单结构（menu_type 语义取自底座 CommonConstant）：
--   0 = 一级菜单（目录）  1 = 子菜单（页面）  2 = 按钮权限
-- component 填**相对 src/views 的路径**（前端 dynamicImport 用 import.meta.glob 的 key 匹配），
-- 不带 `views/` 前缀。
--
-- 权限码真源：源码里的 @RequiresPermissions（含 MathConstants 常量），
-- 不是接口文档——接口文档会漏，源码不会。
-- =============================================================================

-- ----------------------------------------------------------------- 1. 角色
INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `description`, `create_by`, `create_time`, `tenant_id`)
VALUES
  ('st_role_kid', '学习者（孩子）', 'study_kid', 'jeecg-study 学习端身份：练习 / 背单词 / 语法 / 写作 / 护眼 / 错题复习', 'admin', NOW(), 0),
  ('st_role_parent', '家长', 'study_parent', 'jeecg-study 家长端身份：学习报告 / 错题分析 / 作文反馈 / 家庭与孩子管理', 'admin', NOW(), 0)
ON DUPLICATE KEY UPDATE
  `role_name` = VALUES(`role_name`),
  `description` = VALUES(`description`);

-- ----------------------------------------------------------------- 2. 一级目录
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `menu_type`, `perms`, `perms_type`,
   `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `create_by`, `create_time`, `del_flag`, `status`)
VALUES
  ('st_menu_study', '', '学习助手', '/study', 'layouts/default/index', 1, 0, NULL, '0',
   30.00, 1, 'ant-design:read-outlined', 0, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_parent', '', '家长端', '/study-parent', 'layouts/default/index', 1, 0, NULL, '0',
   40.00, 1, 'ant-design:team-outlined', 0, 0, 0, 'admin', NOW(), 0, '1')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `url` = VALUES(`url`), `icon` = VALUES(`icon`), `sort_no` = VALUES(`sort_no`);

-- ----------------------------------------------------------------- 3. 页面菜单
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `menu_type`, `perms`, `perms_type`,
   `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `create_by`, `create_time`, `del_flag`, `status`)
VALUES
  ('st_menu_practice', 'st_menu_study', '练习', '/study/practice', 'study/practice/index', 1, 'StudyPractice', 1, NULL, '0',
   1.00, 0, 'ant-design:edit-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_word', 'st_menu_study', '背单词', '/study/word', 'study/word/index', 1, 'StudyWord', 1, NULL, '0',
   2.00, 0, 'ant-design:book-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_grammar', 'st_menu_study', '英语语法', '/study/grammar', 'study/grammar/index', 1, 'StudyGrammar', 1, NULL, '0',
   3.00, 0, 'ant-design:translation-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_writing', 'st_menu_study', '语文写作', '/study/writing', 'study/writing/index', 1, 'StudyWriting', 1, NULL, '0',
   4.00, 0, 'ant-design:edit-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_eye', 'st_menu_study', '护眼', '/study/eye', 'study/eye/index', 1, 'StudyEye', 1, NULL, '0',
   5.00, 0, 'ant-design:eye-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_parent_report', 'st_menu_parent', '学习报告', '/study-parent/report', 'study/parent/report/index', 1, 'StudyParentReport', 1, NULL, '0',
   1.00, 0, 'ant-design:line-chart-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_parent_error', 'st_menu_parent', '错题分析', '/study-parent/error', 'study/parent/error/index', 1, 'StudyParentError', 1, NULL, '0',
   2.00, 0, 'ant-design:profile-outlined', 1, 0, 0, 'admin', NOW(), 0, '1'),
  ('st_menu_parent_feedback', 'st_menu_parent', '作文反馈', '/study-parent/feedback', 'study/parent/feedback/index', 1, 'StudyParentFeedback', 1, NULL, '0',
   3.00, 0, 'ant-design:message-outlined', 1, 0, 0, 'admin', NOW(), 0, '1')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `url` = VALUES(`url`), `component` = VALUES(`component`),
  `component_name` = VALUES(`component_name`), `sort_no` = VALUES(`sort_no`), `icon` = VALUES(`icon`);

-- ----------------------------------------------------------------- 4. 按钮权限
-- menu_type=2：进权限集合，不进路由树（底座 queryByUser 只把 0/1 组装成菜单）。
-- 无前端页面的管理类端点（坐标树 / 单词维护 / 母题维护 / 家庭与孩子管理）
-- 挂在语义最接近的页面下，保证权限码都有父节点、且不会在左侧菜单冒出裸条目。
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `menu_type`, `perms`, `perms_type`,
   `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `create_by`, `create_time`, `del_flag`, `status`)
VALUES
  -- 练习页
  ('st_btn_practice_query', 'st_menu_practice', '取题', NULL, NULL, 0, 2, 'study:kid:practice:query', '1',
   1.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_practice_add', 'st_menu_practice', '提交作答', NULL, NULL, 0, 2, 'study:kid:practice:add', '1',
   2.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_math_view', 'st_menu_practice', '母题查看', NULL, NULL, 0, 2, 'study:math:view', '1',
   3.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_math_edit', 'st_menu_practice', '母题维护', NULL, NULL, 0, 2, 'study:math:edit', '1',
   4.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_math_pass', 'st_menu_practice', '母题过关', NULL, NULL, 0, 2, 'study:math:pass', '1',
   5.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  -- 背单词页
  ('st_btn_word_view', 'st_menu_word', '词表查看', NULL, NULL, 0, 2, 'study:kid:word:view', '1',
   1.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_word_practice', 'st_menu_word', '单词练习', NULL, NULL, 0, 2, 'study:kid:word:practice', '1',
   2.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_word_feedback', 'st_menu_word', '记忆反馈', NULL, NULL, 0, 2, 'study:kid:word:feedback', '1',
   3.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_parent_word_view', 'st_menu_word', '家长查词表', NULL, NULL, 0, 2, 'study:parent:word:view', '1',
   4.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  -- 语法页
  ('st_btn_grammar_view', 'st_menu_grammar', '语法讲解', NULL, NULL, 0, 2, 'study:kid:grammar:view', '1',
   1.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_grammar_practice', 'st_menu_grammar', '语法练习', NULL, NULL, 0, 2, 'study:kid:grammar:practice', '1',
   2.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  -- 写作页
  ('st_btn_writing_view', 'st_menu_writing', '拆解库查看', NULL, NULL, 0, 2, 'study:writing:view', '1',
   1.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_writing_train', 'st_menu_writing', '写作训练', NULL, NULL, 0, 2, 'study:writing:train', '1',
   2.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  -- 护眼页
  ('st_btn_eye_usage', 'st_menu_eye', '用眼上报', NULL, NULL, 0, 2, 'study:kid:eye:usage', '1',
   1.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  -- 错题与复习（孩子端挂在练习页下：孩子端没有独立的错题页，入口在练习页）
  ('st_btn_kid_error_query', 'st_menu_practice', '查错题', NULL, NULL, 0, 2, 'study:kid:error:query', '1',
   6.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_kid_error_add', 'st_menu_practice', '录入错题', NULL, NULL, 0, 2, 'study:kid:error:add', '1',
   7.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_kid_review_query', 'st_menu_practice', '查复习队列', NULL, NULL, 0, 2, 'study:kid:review:query', '1',
   8.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_kid_review_add', 'st_menu_practice', '提交复习', NULL, NULL, 0, 2, 'study:kid:review:add', '1',
   9.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_kid_profile', 'st_menu_practice', '我的信息', NULL, NULL, 0, 2, 'study:kid:profile', '1',
   10.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_kid_logout', 'st_menu_practice', '退出', NULL, NULL, 0, 2, 'study:kid:logout', '1',
   11.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  -- 家长-学习报告页
  ('st_btn_report_view', 'st_menu_parent_report', '学习报告', NULL, NULL, 0, 2, 'study:parent:report:view', '1',
   1.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_context_query', 'st_menu_parent_report', '查上下文', NULL, NULL, 0, 2, 'study:parent:context:query', '1',
   2.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_context_edit', 'st_menu_parent_report', '配上下文', NULL, NULL, 0, 2, 'study:parent:context:edit', '1',
   3.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_textbook_query', 'st_menu_parent_report', '查教材版本', NULL, NULL, 0, 2, 'study:parent:textbook:query', '1',
   4.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_child_query', 'st_menu_parent_report', '查孩子', NULL, NULL, 0, 2, 'study:parent:child:query', '1',
   5.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_child_add', 'st_menu_parent_report', '加孩子', NULL, NULL, 0, 2, 'study:parent:child:add', '1',
   6.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_child_edit', 'st_menu_parent_report', '改孩子', NULL, NULL, 0, 2, 'study:parent:child:edit', '1',
   7.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_child_pin', 'st_menu_parent_report', '重置 PIN', NULL, NULL, 0, 2, 'study:parent:child:pin', '1',
   8.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_child_relation', 'st_menu_parent_report', '配亲子关系', NULL, NULL, 0, 2, 'study:parent:child:relation', '1',
   9.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_family_add', 'st_menu_parent_report', '建家庭', NULL, NULL, 0, 2, 'study:parent:family:add', '1',
   10.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_family_query', 'st_menu_parent_report', '查家庭', NULL, NULL, 0, 2, 'study:parent:family:query', '1',
   11.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_family_edit', 'st_menu_parent_report', '改家庭', NULL, NULL, 0, 2, 'study:parent:family:edit', '1',
   12.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_family_member', 'st_menu_parent_report', '家庭成员', NULL, NULL, 0, 2, 'study:parent:family:member', '1',
   13.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_knowledge_query', 'st_menu_parent_report', '查坐标树', NULL, NULL, 0, 2, 'study:parent:knowledge:query', '1',
   14.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_knowledge_edit', 'st_menu_parent_report', '改坐标树', NULL, NULL, 0, 2, 'study:parent:knowledge:edit', '1',
   15.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_knowledge_import', 'st_menu_parent_report', '导入坐标树', NULL, NULL, 0, 2, 'study:parent:knowledge:import', '1',
   16.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  -- 家长-错题分析页
  ('st_btn_parent_error_query', 'st_menu_parent_error', '查错题', NULL, NULL, 0, 2, 'study:parent:error:query', '1',
   1.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_parent_error_add', 'st_menu_parent_error', '代录错题', NULL, NULL, 0, 2, 'study:parent:error:add', '1',
   2.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_parent_error_edit', 'st_menu_parent_error', '改错题', NULL, NULL, 0, 2, 'study:parent:error:edit', '1',
   3.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  -- 家长-作文反馈页
  ('st_btn_writing_feedback', 'st_menu_parent_feedback', '留反馈', NULL, NULL, 0, 2, 'study:parent:writing:feedback', '1',
   1.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1'),
  ('st_btn_writing_query', 'st_menu_parent_feedback', '查作品与反馈', NULL, NULL, 0, 2, 'study:parent:writing:query', '1',
   2.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `perms` = VALUES(`perms`), `sort_no` = VALUES(`sort_no`);

-- ----------------------------------------------------------------- 5. 角色授权
-- 孩子：学习端 5 个页面 + 练习页上的错题/复习按钮 + 写作/护眼/背单词/语法按钮
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `operate_date`, `operate_ip`)
VALUES
  ('st_rp_kid_m_study', 'st_role_kid', 'st_menu_study', NOW(), '127.0.0.1'),
  ('st_rp_kid_m_practice', 'st_role_kid', 'st_menu_practice', NOW(), '127.0.0.1'),
  ('st_rp_kid_m_word', 'st_role_kid', 'st_menu_word', NOW(), '127.0.0.1'),
  ('st_rp_kid_m_grammar', 'st_role_kid', 'st_menu_grammar', NOW(), '127.0.0.1'),
  ('st_rp_kid_m_writing', 'st_role_kid', 'st_menu_writing', NOW(), '127.0.0.1'),
  ('st_rp_kid_m_eye', 'st_role_kid', 'st_menu_eye', NOW(), '127.0.0.1'),
  ('st_rp_kid_b_pq', 'st_role_kid', 'st_btn_practice_query', NOW(), '127.0.0.1'),
  ('st_rp_kid_b_pa', 'st_role_kid', 'st_btn_practice_add', NOW(), '127.0.0.1'),
  ('st_rp_kid_b_eq', 'st_role_kid', 'st_btn_kid_error_query', NOW(), '127.0.0.1'),
  ('st_rp_kid_b_ea', 'st_role_kid', 'st_btn_kid_error_add', NOW(), '127.0.0.1'),
  ('st_rp_kid_b_rq', 'st_role_kid', 'st_btn_kid_review_query', NOW(), '127.0.0.1'),
  ('st_rp_kid_b_ra', 'st_role_kid', 'st_btn_kid_review_add', NOW(), '127.0.0.1'),
  ('st_rp_kid_b_prof', 'st_role_kid', 'st_btn_kid_profile', NOW(), '127.0.0.1'),
  ('st_rp_kid_b_out', 'st_role_kid', 'st_btn_kid_logout', NOW(), '127.0.0.1'),
  ('st_rp_kid_b_wv', 'st_role_kid', 'st_btn_word_view', NOW(), '127.0.0.1'),
  ('st_rp_kid_b_wp', 'st_role_kid', 'st_btn_word_practice', NOW(), '127.0.0.1'),
  ('st_rp_kid_b_wf', 'st_role_kid', 'st_btn_word_feedback', NOW(), '127.0.0.1'),
  ('st_rp_kid_b_gv', 'st_role_kid', 'st_btn_grammar_view', NOW(), '127.0.0.1'),
  ('st_rp_kid_b_gp', 'st_role_kid', 'st_btn_grammar_practice', NOW(), '127.0.0.1'),
  ('st_rp_kid_b_wr_v', 'st_role_kid', 'st_btn_writing_view', NOW(), '127.0.0.1'),
  ('st_rp_kid_b_wr_t', 'st_role_kid', 'st_btn_writing_train', NOW(), '127.0.0.1'),
  ('st_rp_kid_b_eye', 'st_role_kid', 'st_btn_eye_usage', NOW(), '127.0.0.1')
ON DUPLICATE KEY UPDATE `operate_date` = VALUES(`operate_date`);

-- 家长：家长端 3 个页面 + 全部家长端按钮 + 作文域查看/训练（家长反馈页要读拆解库）
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `operate_date`, `operate_ip`)
VALUES
  ('st_rp_parent_m_parent', 'st_role_parent', 'st_menu_parent', NOW(), '127.0.0.1'),
  ('st_rp_parent_m_report', 'st_role_parent', 'st_menu_parent_report', NOW(), '127.0.0.1'),
  ('st_rp_parent_m_error', 'st_role_parent', 'st_menu_parent_error', NOW(), '127.0.0.1'),
  ('st_rp_parent_m_fb', 'st_role_parent', 'st_menu_parent_feedback', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_rv', 'st_role_parent', 'st_btn_report_view', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_cq', 'st_role_parent', 'st_btn_context_query', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_ce', 'st_role_parent', 'st_btn_context_edit', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_tq', 'st_role_parent', 'st_btn_textbook_query', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_chq', 'st_role_parent', 'st_btn_child_query', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_cha', 'st_role_parent', 'st_btn_child_add', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_che', 'st_role_parent', 'st_btn_child_edit', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_cpin', 'st_role_parent', 'st_btn_child_pin', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_crel', 'st_role_parent', 'st_btn_child_relation', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_fa', 'st_role_parent', 'st_btn_family_add', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_fq', 'st_role_parent', 'st_btn_family_query', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_fe', 'st_role_parent', 'st_btn_family_edit', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_fm', 'st_role_parent', 'st_btn_family_member', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_kq', 'st_role_parent', 'st_btn_knowledge_query', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_ke', 'st_role_parent', 'st_btn_knowledge_edit', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_ki', 'st_role_parent', 'st_btn_knowledge_import', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_eq', 'st_role_parent', 'st_btn_parent_error_query', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_ea', 'st_role_parent', 'st_btn_parent_error_add', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_ee', 'st_role_parent', 'st_btn_parent_error_edit', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_wf', 'st_role_parent', 'st_btn_writing_feedback', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_wq', 'st_role_parent', 'st_btn_writing_query', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_pw', 'st_role_parent', 'st_btn_parent_word_view', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_mv', 'st_role_parent', 'st_btn_math_view', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_me', 'st_role_parent', 'st_btn_math_edit', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_mp', 'st_role_parent', 'st_btn_math_pass', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_wv', 'st_role_parent', 'st_btn_writing_view', NOW(), '127.0.0.1'),
  ('st_rp_parent_b_wt', 'st_role_parent', 'st_btn_writing_train', NOW(), '127.0.0.1')
ON DUPLICATE KEY UPDATE `operate_date` = VALUES(`operate_date`);
