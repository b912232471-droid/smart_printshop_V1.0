SET NAMES utf8mb4;

USE `schedule_db`;

-- 删除 jw_accounts.jw_username 列
-- 课表绑定不再需要单独的教务系统用户名字段，仅保留学号与教务系统密码
ALTER TABLE `jw_accounts`
  DROP COLUMN `jw_username`;
