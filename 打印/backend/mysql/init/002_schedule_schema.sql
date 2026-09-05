SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `schedule_db`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON `schedule_db`.* TO 'print_shop'@'%';

USE `schedule_db`;

CREATE TABLE IF NOT EXISTS `jw_accounts` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '教务账号ID',
  `user_id` BIGINT NOT NULL COMMENT '平台用户ID',
  `student_id` VARCHAR(64) NOT NULL COMMENT '学号',
  `jw_password` VARCHAR(1024) NOT NULL COMMENT 'AES-GCM密文密码',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_jw_accounts_user` (`user_id`),
  KEY `idx_jw_accounts_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教务账号绑定表';

CREATE TABLE IF NOT EXISTS `course_schedules` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '课表ID',
  `user_id` BIGINT NOT NULL COMMENT '平台用户ID',
  `student_id` VARCHAR(64) NOT NULL COMMENT '学号',
  `xnm` VARCHAR(16) NOT NULL COMMENT '学年',
  `xqm` VARCHAR(16) NOT NULL COMMENT '学期',
  `kcmc` VARCHAR(255) NOT NULL COMMENT '课程名称',
  `xqj` VARCHAR(32) DEFAULT NULL COMMENT '星期',
  `jcs` VARCHAR(64) DEFAULT NULL COMMENT '节次',
  `cdmc` VARCHAR(255) DEFAULT NULL COMMENT '地点',
  `xm` VARCHAR(128) DEFAULT NULL COMMENT '教师',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY `idx_course_user_term` (`user_id`, `xnm`, `xqm`),
  KEY `idx_course_student_term` (`student_id`, `xnm`, `xqm`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教务课表表';
