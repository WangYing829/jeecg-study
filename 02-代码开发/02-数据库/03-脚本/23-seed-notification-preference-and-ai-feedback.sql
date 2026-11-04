-- ============================================================
-- jeecg-study · 增量脚本 23 —— 家长提醒偏好 + 解析申诉（V1 落地）
-- 目标库：jeecg-study（MySQL 8）· 表前缀 study_
-- 幂等：建表用 IF NOT EXISTS；权限行用 ON DUPLICATE KEY UPDATE
-- 可重复执行：任意次执行结果一致
--
-- 背景（13 个「文档写了但没实现」的端点里，有两个需要新表）：
--   1) GET/PUT /parent/notification-preference  → 需要「提醒推送偏好」落库
--      （《接口设计说明书》§3.5 #74/#75，需求 F-PAR-04）
--   2) GET /practice/{contentId}/explanation/feedback → 需要「解析申诉」落库
--      （《接口设计说明书》§3.2 #21，需求 R-02 / F-FAM-08）
--
-- 表名口径：
--   study_notification_preference —— 新表（提醒偏好此前无表）
--   study_ai_feedback —— 《数据库设计说明书》§预留表清单里已登记的
--     「AI 讲解申诉记录（「我不同意」）」，本脚本把它落成实体表
-- ============================================================

USE `jeecg-study`;

-- ----------------------------------------------------------------- 1. 家长提醒推送偏好
-- 维度：家长账号 + 孩子（child_code 为空串 = 该家长的全部孩子）
-- 隔离：parent_user_id 来自会话，不接受前端传值；child_code 传了就必须过关系表校验
CREATE TABLE IF NOT EXISTS `study_notification_preference` (
  `id`                    varchar(32)  NOT NULL COMMENT '主键',
  `parent_user_id`        varchar(32)  NOT NULL COMMENT '家长账号 sys_user.id',
  `child_code`            varchar(32)  NOT NULL DEFAULT '' COMMENT '孩子业务ID(空串=全部孩子)',
  `review_remind_enabled` tinyint      NOT NULL DEFAULT 1 COMMENT '复习提醒开关 1开 0关',
  `review_remind_time`    time         NOT NULL DEFAULT '19:00:00' COMMENT '复习提醒推送时间',
  `eye_remind_enabled`    tinyint      NOT NULL DEFAULT 1 COMMENT '护眼提醒开关 1开 0关',
  `eye_remind_time`       time         NOT NULL DEFAULT '19:30:00' COMMENT '护眼提醒推送时间',
  `push_channel`          varchar(16)  NOT NULL DEFAULT 'APP' COMMENT '推送渠道 APP/WECHAT/SMS',
  `del_flag`              tinyint      NOT NULL DEFAULT 0 COMMENT '删除标记 0正常 1删除',
  `create_by`             varchar(50)  DEFAULT NULL COMMENT '创建人',
  `create_time`           datetime     DEFAULT NULL COMMENT '创建时间',
  `update_by`             varchar(50)  DEFAULT NULL COMMENT '更新人',
  `update_time`           datetime     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pref_parent_child` (`parent_user_id`, `child_code`),
  KEY `idx_pref_parent` (`parent_user_id`, `del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长提醒推送偏好(F-PAR-04)';

-- ----------------------------------------------------------------- 2. 解析申诉（我不同意）
-- 隔离：child_id 必填（行为表按孩子隔离）；submitter_user_id 记录谁提的
--       家长之间互不可见：读的时候只读本人提交的那条
CREATE TABLE IF NOT EXISTS `study_ai_feedback` (
  `id`                varchar(32)  NOT NULL COMMENT '主键',
  `child_id`          varchar(32)  NOT NULL COMMENT '所属孩子 study_child.id(多用户隔离)',
  `content_id`        varchar(32)  NOT NULL COMMENT '内容业务ID',
  `content_type`      varchar(24)  NOT NULL DEFAULT 'QUESTION' COMMENT '内容类型',
  `disagree`          tinyint      NOT NULL DEFAULT 1 COMMENT '是否表示不同意 1是 0否',
  `reason`            varchar(512) DEFAULT NULL COMMENT '申诉理由',
  `status`            varchar(16)  NOT NULL DEFAULT 'PENDING' COMMENT '处理状态 PENDING/ACCEPTED/REJECTED',
  `submitter_user_id` varchar(32)  NOT NULL COMMENT '提交人 sys_user.id',
  `del_flag`          tinyint      NOT NULL DEFAULT 0 COMMENT '删除标记 0正常 1删除',
  `create_by`         varchar(50)  DEFAULT NULL COMMENT '创建人',
  `create_time`       datetime     DEFAULT NULL COMMENT '创建时间',
  `update_by`         varchar(50)  DEFAULT NULL COMMENT '更新人',
  `update_time`       datetime     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_feedback_child_content` (`child_id`, `content_id`),
  KEY `idx_ai_feedback_child` (`child_id`, `del_flag`),
  KEY `idx_ai_feedback_submitter` (`submitter_user_id`, `del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='讲解申诉记录(我不同意)(R-02/F-FAM-08)';

-- ----------------------------------------------------------------- 3. 权限码：study:parent:pref
-- 《接口设计说明书》§13 权限码设计里，家长端写接口的口径是 study:parent:pref；
-- 提醒偏好是家长端资源域里唯一的写入口，单独给一个码，便于审计。
-- 挂在「家长-学习报告页」下（没有独立页面，语义最接近）。
INSERT INTO `sys_permission`
  (`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `menu_type`, `perms`, `perms_type`,
   `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `create_by`, `create_time`, `del_flag`, `status`)
VALUES
  ('st_btn_parent_pref', 'st_menu_parent_report', '提醒偏好', NULL, NULL, 0, 2, 'study:parent:pref', '1',
   17.00, 0, NULL, 1, 0, 1, 'admin', NOW(), 0, '1')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `perms` = VALUES(`perms`), `sort_no` = VALUES(`sort_no`);

-- 角色授权：家长持有（管理员天然全量）
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `operate_date`, `operate_ip`)
VALUES
  ('st_rp_parent_b_pref', 'st_role_parent', 'st_btn_parent_pref', NOW(), '127.0.0.1')
ON DUPLICATE KEY UPDATE `operate_date` = VALUES(`operate_date`);

-- 管理员也要拿到这个按钮。
-- 基座**没有 admin 权限兜底**（SysPermissionServiceImpl 里没有 isAdmin 分支），
-- admin 的权限只来自 sys_role_permission 行；而 19 号脚本（admin 全量授权）
-- 在迁移序列里排在 23 号**之前** ⇒ 新按钮首轮拿不到授权 ⇒ 真 403。
-- 这里按 19 / 22 号同口径补授（id 生成口径：st_rpa_ + MD5('admin|' + 权限id)），
-- 与后续脚本的兜底授权用 NOT EXISTS 判重，不会产生重复行。
SET @admin_role_id = (SELECT `id` FROM `sys_role` WHERE `role_code` = 'admin' LIMIT 1);
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `operate_date`, `operate_ip`)
SELECT
  CONCAT('st_rpa_', LEFT(MD5(CONCAT('admin|', p.`id`)), 16)),
  @admin_role_id,
  p.`id`,
  NOW(),
  '127.0.0.1'
FROM `sys_permission` p
WHERE p.`id` = 'st_btn_parent_pref'
  AND @admin_role_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM (SELECT `role_id`, `permission_id` FROM `sys_role_permission`) rp
    WHERE rp.`role_id` = @admin_role_id
      AND rp.`permission_id` = p.`id`
  );
