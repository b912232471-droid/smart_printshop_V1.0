-- ---------------------------------------------------------------------
-- 009_ocr_schema.sql
-- OCR 图片转文档运营管理（镜像 008 imagegen 模式）：
--   1. ocr_settings        功能开关 + 每用户每日识别次数（单行表）
--   2. ocr_setting_audits  配置变更审计
--   3. ocr_record          转换使用记录（成功/失败、字数、耗时）
--   4. RBAC 菜单 88/89（F 权限点，挂菜单 82 之下）：print:ocr:manage / print:ocr:query
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `ocr_settings` (
  `id` TINYINT PRIMARY KEY COMMENT '固定为1',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '功能总开关(0全站停用)',
  `daily_quota_per_user` INT NOT NULL DEFAULT 20 COMMENT '每用户每日识别次数上限',
  `updated_by` BIGINT DEFAULT NULL COMMENT '最后修改人(account_id)',
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCR 图片转文档运营配置';

CREATE TABLE IF NOT EXISTS `ocr_setting_audits` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '审计ID',
  `admin_id` BIGINT NOT NULL COMMENT '操作管理员(account_id)',
  `before_json` LONGTEXT NOT NULL COMMENT '变更前配置JSON',
  `after_json` LONGTEXT NOT NULL COMMENT '变更后配置JSON',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCR配置审计';

CREATE TABLE IF NOT EXISTS `ocr_record` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
  `account_id` BIGINT NOT NULL COMMENT '用户ID(account_id)',
  `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名(去扩展名)',
  `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '原图大小(字节)',
  `char_count` INT NOT NULL DEFAULT 0 COMMENT '识别字符数',
  `duration_ms` INT NOT NULL DEFAULT 0 COMMENT '识别耗时(毫秒)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1成功 2失败',
  `failure_reason` VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY `idx_account_created` (`account_id`, `created_at`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCR转换使用记录';

-- 默认配置行（仅缺失时插入，不覆盖已调整值）
INSERT INTO `ocr_settings` (`id`)
  SELECT 1 FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM `ocr_settings` WHERE `id` = 1);

-- ---------------------------------------------------------------------
-- RBAC 权限点（接续 008 的 id 段，87 之后）
--   88 print:ocr:manage  OCR 运营配置管理
--   89 print:ocr:query   OCR 使用记录查看
--   superadmin 代码级直通；admin 角色授权，与 imagegen 菜单 85-87 对齐
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `perms`, `path`, `component`, `icon`, `sort_order`) VALUES
  (88, 82, 'OCR 运营配置管理', 'F', 'print:ocr:manage', NULL, NULL, NULL, 1),
  (89, 82, 'OCR 使用记录查看', 'F', 'print:ocr:query',  NULL, NULL, NULL, 2);

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
  SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (88, 89);

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
  SELECT 2, `id` FROM `sys_menu` WHERE `id` IN (88, 89);

-- ---------------------------------------------------------------------
-- 执行后验证（手动抽查）
--   SHOW TABLES LIKE 'ocr_%';                       -- 预期 3 张
--   SELECT id, enabled, daily_quota_per_user FROM ocr_settings;  -- 预期 1 行默认值
--   SELECT id, perms FROM sys_menu WHERE id IN (88, 89);         -- 预期 2 行
-- ---------------------------------------------------------------------
