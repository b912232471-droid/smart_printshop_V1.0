SET NAMES utf8mb4;

USE `print_shop`;

CREATE TABLE IF NOT EXISTS `user_account` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '统一账户ID',
  `username` VARCHAR(64) NOT NULL COMMENT '登录用户名',
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt密码哈希',
  `account_type` VARCHAR(16) NOT NULL COMMENT 'user/admin',
  `role` VARCHAR(32) NOT NULL COMMENT 'user/superadmin/admin/operator',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  `display_name` VARCHAR(64) DEFAULT NULL COMMENT '用户昵称',
  `real_name` VARCHAR(64) DEFAULT NULL COMMENT '管理员姓名',
  `phone` VARCHAR(512) DEFAULT NULL COMMENT '手机号密文',
  `email` VARCHAR(512) DEFAULT NULL COMMENT '邮箱密文',
  `email_hash` CHAR(64) DEFAULT NULL COMMENT '规范化邮箱盲索引',
  `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT '头像公开访问路径',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  UNIQUE KEY `uk_user_account_username` (`username`),
  UNIQUE KEY `uk_user_account_email_hash` (`email_hash`),
  KEY `idx_user_account_type_status` (`account_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一用户账户表';

-- 历史普通用户没有平台密码，迁移后必须由管理员重置密码才能登录。
INSERT IGNORE INTO `user_account`
  (`id`, `username`, `password_hash`, `account_type`, `role`, `status`,
   `display_name`, `phone`, `avatar_url`, `created_at`, `last_login_at`)
SELECT `id`, CONCAT('legacy_user_', `id`), '!RESET_REQUIRED!', 'user', 'user', 1,
       `username`, `phone`, `avatar_url`, `register_time`, `last_login_time`
FROM `user`;

-- 管理员并入同一账户表。普通用户ID保持不变，以兼容订单和课表数据。
INSERT IGNORE INTO `user_account`
  (`username`, `password_hash`, `account_type`, `role`, `status`, `real_name`,
   `phone`, `email`, `created_at`, `last_login_at`)
SELECT `username`, `password`, 'admin', `role`, `status`, `real_name`,
       `phone`, `email`, `create_time`, `last_login_time`
FROM `admin`;

-- 迁移完成后删除旧身份表，避免存在第二份账号或密码数据源。
DROP TABLE IF EXISTS `admin`;
DROP TABLE IF EXISTS `user`;
