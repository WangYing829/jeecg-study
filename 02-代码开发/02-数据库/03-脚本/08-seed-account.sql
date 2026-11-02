-- =============================================================================
-- 08-seed-account.sql · 运行时种子：家长账号 + 孩子账号 + 家庭 + 亲子关系 + 学习上下文
-- =============================================================================
-- 用途：让两种身份在真实运行时能登录、能进页面、能取到自己的数据。
--
-- 为什么必须有（不是可有可无的演示数据）：
--   上一轮 235 条自动化用例全绿，是因为底座能力（用户 / 角色 / Redis）在测试里是 Mock，
--   「登录 → 角色 → 菜单 → 路由 → 端点」这条链路从未在真实环境跑过。
--   07-seed-role-menu.sql 补的是菜单与权限，本脚本补的是**账号与业务数据**。
--
-- 幂等：全部 ON DUPLICATE KEY UPDATE / 固定主键，重复执行不报错、不重复插。
--
-- 演示凭据（仅演示用，不是真实个人信息；账号名统一带 demo 后缀）：
--   家长  study_parent_demo / study@2026     （底座 POST /sys/login）
--   孩子  study_kid_demo    / study@2026     （底座账号，孩子实际走家庭码 + PIN 登录）
--   孩子 PIN 1234（4 位，满足 4–6 位约束）
--
-- 密码哈希口径（与底座登录校验完全一致）：
--   sys_user.password = PasswordUtil.encrypt(username, 明文密码, salt)
--     —— PBEWithMD5AndDES，数据是 username、密钥是明文密码（见 LoginController:113）
--   study_child.pin_hash = PBKDF2-HMAC-SHA256(PIN, pin_salt)，10 万次迭代
--   study_family.family_code = HMAC-SHA256(pepper, 家庭码明文)
--
-- 家庭码 pepper 是**部署级机密**（配置项 jeecg.study.family-code-pepper），
-- 默认取 application-dev.yml 的占位值 jeecg-study-dev-pepper-please-override。
-- 本脚本把哈希值写死，并用 @family_code 留出覆盖口：
--   部署若换了 pepper，执行前先 SET @family_code = '<用该 pepper 算出的哈希>';
--   （pepper 不入库、不进版本库，哈希可用一次性工具按 StudySecretUtil 复算）
-- =============================================================================

-- 家庭码明文 STU026（6 位大写字母数字，与 StudySecretUtil.newFamilyCode 的格式一致）
SET @family_code = COALESCE(
  @family_code,
  'cd9d1172ce58213880186b72d6de560ad5197deb2d2425ba1746dbf70cfceccf'
);

-- ----------------------------------------------------------------- 1. 账号
-- 唯一键：uniq_sys_user_username（username）
INSERT INTO `sys_user`
  (`id`, `username`, `realname`, `password`, `salt`, `avatar`, `sex`, `email`, `phone`,
   `org_code`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  ('ST-USER-PARENT-DEMO-01', 'study_parent_demo', '演示家长',
   '1a1a9acc370e32c01a2818fcb6b90573da3e6f18a340ec6e', 'SP8K2M4Q', NULL, 2, NULL, NULL,
   'A01A01', 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-USER-KID-DEMO-01', 'study_kid_demo', '演示孩子',
   'dcda6172c34162d7d105fb646f9d06de', 'SK3N7P1R', NULL, 1, NULL, NULL,
   'A01A01', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `realname` = VALUES(`realname`),
  `password` = VALUES(`password`),
  `salt` = VALUES(`salt`),
  `status` = VALUES(`status`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin',
  `update_time` = NOW();

-- ----------------------------------------------------------------- 2. 角色绑定
-- 角色 id 来自 07-seed-role-menu.sql（st_role_parent / st_role_kid）
-- 唯一键：主键 id（固定值保证重复执行不重复插）
INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`, `tenant_id`)
VALUES
  ('ST-UR-PARENT-DEMO-01', 'ST-USER-PARENT-DEMO-01', 'st_role_parent', 0),
  ('ST-UR-KID-DEMO-01', 'ST-USER-KID-DEMO-01', 'st_role_kid', 0)
ON DUPLICATE KEY UPDATE
  `user_id` = VALUES(`user_id`),
  `role_id` = VALUES(`role_id`);

-- ----------------------------------------------------------------- 3. 家庭
-- 唯一键：uk_family_code（family_code）
INSERT INTO `study_family`
  (`id`, `family_code`, `family_code_salt`, `family_name`, `status`, `del_flag`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  ('ST-FAMILY-DEMO-01', @family_code, '9b3f17c4ae25d806', '演示家庭', 1, 0,
   'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `family_name` = VALUES(`family_name`),
  `status` = VALUES(`status`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin',
  `update_time` = NOW();

-- ----------------------------------------------------------------- 4. 家庭成员
-- 唯一键：uk_family_member（family_id + user_id）
-- 创建人角色 OWNER（与 StudyFamilyServiceImpl 建家庭时一致）
INSERT INTO `study_family_member`
  (`id`, `family_id`, `user_id`, `member_role`, `relation_label`, `status`, `del_flag`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  ('ST-FAMMEMBER-DEMO-01', 'ST-FAMILY-DEMO-01', 'ST-USER-PARENT-DEMO-01', 'OWNER', '妈妈', 1, 0,
   'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `member_role` = VALUES(`member_role`),
  `relation_label` = VALUES(`relation_label`),
  `status` = VALUES(`status`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin',
  `update_time` = NOW();

-- ----------------------------------------------------------------- 5. 孩子
-- 唯一键：uk_child_code（child_code）、uk_child_user（user_id）
-- child_code 格式与运行时一致：CHD- + 6 位序号（CHILD_CODE_PREFIX = "CHD-"）
-- PIN 1234 的哈希（盐 9f2c7a41d8e35b06）：PBKDF2-HMAC-SHA256 / 10 万次 / hex 64
INSERT INTO `study_child`
  (`id`, `child_code`, `family_id`, `user_id`, `nickname`, `avatar`, `gender`, `birthday`,
   `grade_code`, `pin_hash`, `pin_salt`, `pin_fail_count`, `pin_lock_until`, `status`, `del_flag`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  ('ST-CHILD-DEMO-01', 'CHD-000001', 'ST-FAMILY-DEMO-01', 'ST-USER-KID-DEMO-01', '小星', NULL, 1,
   '2016-05-20', 'G4',
   '32712df897fb5def257150712ebaf92123e204b76c5778122605449ffd02de42', '9f2c7a41d8e35b06',
   0, NULL, 1, 0,
   'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `family_id` = VALUES(`family_id`),
  `nickname` = VALUES(`nickname`),
  `grade_code` = VALUES(`grade_code`),
  `pin_hash` = VALUES(`pin_hash`),
  `pin_salt` = VALUES(`pin_salt`),
  `pin_fail_count` = 0,
  `pin_lock_until` = NULL,
  `status` = VALUES(`status`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin',
  `update_time` = NOW();

-- ----------------------------------------------------------------- 6. 亲子关系
-- 唯一键：uk_parent_child（parent_user_id + child_id）
INSERT INTO `study_parent_child_rel`
  (`id`, `parent_user_id`, `child_id`, `relation_label`, `is_primary`, `status`, `del_flag`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  ('ST-PCREL-DEMO-01', 'ST-USER-PARENT-DEMO-01', 'ST-CHILD-DEMO-01', '妈妈', 1, 1, 0,
   'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_label` = VALUES(`relation_label`),
  `is_primary` = VALUES(`is_primary`),
  `status` = VALUES(`status`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin',
  `update_time` = NOW();

-- ----------------------------------------------------------------- 7. 学习上下文
-- 上下文决定：学科范围、背单词用的教材版本、护眼提醒间隔。
-- 教材版本取库里已有的「人教版·小学」（TV-RENJIAO-PRIMARY），与 09 号内容种子的词表对齐。
-- 唯一键：uk_context_code（context_code）
INSERT INTO `study_context`
  (`id`, `child_id`, `context_code`, `province_code`, `province_name`, `stage_code`, `grade_code`,
   `textbook_version_id`, `eye_interval_min`, `is_current`, `effective_from`, `del_flag`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  ('ST-CTX-DEMO-01', 'ST-CHILD-DEMO-01', 'CTX-000001', '32', '江苏省', 'PRIMARY', 'G4',
   '6b36a3390df28f636ebf1b9470a522c8', 15, 1, NOW(), 0,
   'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `child_id` = VALUES(`child_id`),
  `province_code` = VALUES(`province_code`),
  `province_name` = VALUES(`province_name`),
  `stage_code` = VALUES(`stage_code`),
  `grade_code` = VALUES(`grade_code`),
  `textbook_version_id` = VALUES(`textbook_version_id`),
  `eye_interval_min` = VALUES(`eye_interval_min`),
  `is_current` = VALUES(`is_current`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin',
  `update_time` = NOW();

-- ----------------------------------------------------------------- 8. 业务 ID 序列
-- 种子用的是固定编号（CHD-000001 / CTX-000001），把序列抬到种子编号之上，
-- 避免运行时分配出与种子冲突的业务 ID。
-- 唯一键：uniq_prefix（prefix）
INSERT INTO `study_id_sequence` (`id`, `prefix`, `next_value`, `step`, `del_flag`)
VALUES
  ('ST-SEQ-CHD-DEMO', 'CHD', 3, 1, 0),
  ('ST-SEQ-CTX-DEMO', 'CTX', 3, 1, 0)
ON DUPLICATE KEY UPDATE
  `next_value` = GREATEST(`next_value`, VALUES(`next_value`)),
  `step` = VALUES(`step`);

-- =============================================================================
-- 9. 第二个家庭（用于验证数据范围隔离）
--    为什么要第二个家庭：底座的 Shiro 在**权限码**层面先拦（返回 500 + 提示），
--    而产品红线要求的 **4010 是数据范围越权**（家长访问「存在但与自己无关」的孩子）。
--    只有一个家庭时根本触发不了这条路径 —— 家长能看到的就只有一个孩子。
--    所以这里再造一个不相干的家庭，用来验证：
--      家长 A 查孩子 B 的错题 → 4010（而不是 404，避免资源探测）
--
--    演示凭据：
--      家长 B  study_parent_other_demo / study@2026
--      孩子 B  study_kid2_demo / study@2026，PIN 5678
--      家庭 B  家庭码明文 OTH202
-- =============================================================================

-- 第二个家庭码哈希 = HMAC-SHA256(pepper, 'OTH202')（pepper 同上）
SET @family_code_2 = COALESCE(
  @family_code_2,
  '30b2881d5015de533ca2d6f559b1e7f3bc90e21dd8cb0736b779a73b6ffc5472'
);

-- ----------------------------------------------------------------- 9.1 家庭 B
INSERT INTO `study_family`
  (`id`, `family_code`, `family_code_salt`, `family_name`, `status`, `del_flag`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  ('ST-FAMILY-DEMO-02', @family_code_2, '4d7c19ab52e8f603', '演示家庭二', 1, 0,
   'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `family_name` = VALUES(`family_name`),
  `status` = VALUES(`status`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin',
  `update_time` = NOW();

-- ----------------------------------------------------------------- 9.2 家长 B 账号
INSERT INTO `sys_user`
  (`id`, `username`, `realname`, `password`, `salt`, `avatar`, `sex`, `email`, `phone`,
   `org_code`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  ('ST-USER-PARENT2-DEMO-01', 'study_parent_other_demo', '演示家长二',
   '7cb96cb91e323ab4398c00b7eec2b853f42ef2341308bd72', 'SP9N3K5R', NULL, 2, NULL, NULL,
   'A01A01', 1, 0, 'admin', NOW(), 'admin', NOW()),
  ('ST-USER-KID2-DEMO-01', 'study_kid2_demo', '演示孩子二',
   '9c9127dba355373d573c6cbc9334ea9a', 'SK7M9P3R', NULL, 1, NULL, NULL,
   'A01A01', 1, 0, 'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `realname` = VALUES(`realname`),
  `password` = VALUES(`password`),
  `salt` = VALUES(`salt`),
  `status` = VALUES(`status`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin',
  `update_time` = NOW();

INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`, `tenant_id`)
VALUES
  ('ST-UR-PARENT2-DEMO-01', 'ST-USER-PARENT2-DEMO-01', 'st_role_parent', 0),
  ('ST-UR-KID2-DEMO-01', 'ST-USER-KID2-DEMO-01', 'st_role_kid', 0)
ON DUPLICATE KEY UPDATE
  `user_id` = VALUES(`user_id`),
  `role_id` = VALUES(`role_id`);

-- ----------------------------------------------------------------- 9.3 家庭 B 成员
INSERT INTO `study_family_member`
  (`id`, `family_id`, `user_id`, `member_role`, `relation_label`, `status`, `del_flag`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  ('ST-FAMMEMBER-DEMO-02', 'ST-FAMILY-DEMO-02', 'ST-USER-PARENT2-DEMO-01', 'OWNER', '爸爸', 1, 0,
   'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `member_role` = VALUES(`member_role`),
  `relation_label` = VALUES(`relation_label`),
  `status` = VALUES(`status`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin',
  `update_time` = NOW();

-- ----------------------------------------------------------------- 9.4 孩子 B
-- PIN 5678 的哈希（盐 5b1d0e77c3a94f28）
INSERT INTO `study_child`
  (`id`, `child_code`, `family_id`, `user_id`, `nickname`, `avatar`, `gender`, `birthday`,
   `grade_code`, `pin_hash`, `pin_salt`, `pin_fail_count`, `pin_lock_until`, `status`, `del_flag`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  ('ST-CHILD-DEMO-02', 'CHD-000002', 'ST-FAMILY-DEMO-02', 'ST-USER-KID2-DEMO-01', '小舟', NULL, 1,
   '2014-09-08', 'G3',
   '13999fdc007d6fa86ff821a7bfdb18553f6c3f433d27da5a346e19d63487903d', '5b1d0e77c3a94f28',
   0, NULL, 1, 0,
   'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `family_id` = VALUES(`family_id`),
  `nickname` = VALUES(`nickname`),
  `grade_code` = VALUES(`grade_code`),
  `pin_hash` = VALUES(`pin_hash`),
  `pin_salt` = VALUES(`pin_salt`),
  `pin_fail_count` = 0,
  `pin_lock_until` = NULL,
  `status` = VALUES(`status`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin',
  `update_time` = NOW();

-- ----------------------------------------------------------------- 9.5 亲子关系（家长 B ↔ 孩子 B）
INSERT INTO `study_parent_child_rel`
  (`id`, `parent_user_id`, `child_id`, `relation_label`, `is_primary`, `status`, `del_flag`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  ('ST-PCREL-DEMO-02', 'ST-USER-PARENT2-DEMO-01', 'ST-CHILD-DEMO-02', '爸爸', 1, 1, 0,
   'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `relation_label` = VALUES(`relation_label`),
  `is_primary` = VALUES(`is_primary`),
  `status` = VALUES(`status`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin',
  `update_time` = NOW();

-- ----------------------------------------------------------------- 9.6 孩子 B 的学习上下文
INSERT INTO `study_context`
  (`id`, `child_id`, `context_code`, `province_code`, `province_name`, `stage_code`, `grade_code`,
   `textbook_version_id`, `eye_interval_min`, `is_current`, `effective_from`, `del_flag`,
   `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
  ('ST-CTX-DEMO-02', 'ST-CHILD-DEMO-02', 'CTX-000002', '32', '江苏省', 'PRIMARY', 'G3',
   '6b36a3390df28f636ebf1b9470a522c8', 15, 1, NOW(), 0,
   'admin', NOW(), 'admin', NOW())
ON DUPLICATE KEY UPDATE
  `child_id` = VALUES(`child_id`),
  `province_code` = VALUES(`province_code`),
  `province_name` = VALUES(`province_name`),
  `stage_code` = VALUES(`stage_code`),
  `grade_code` = VALUES(`grade_code`),
  `textbook_version_id` = VALUES(`textbook_version_id`),
  `eye_interval_min` = VALUES(`eye_interval_min`),
  `is_current` = VALUES(`is_current`),
  `del_flag` = VALUES(`del_flag`),
  `update_by` = 'admin',
  `update_time` = NOW();
