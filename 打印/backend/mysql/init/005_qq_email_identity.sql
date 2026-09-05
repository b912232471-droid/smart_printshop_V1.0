SET NAMES utf8mb4;
USE `print_shop`;

SET @email_column_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'user_account'
    AND column_name = 'email_hash'
);
SET @email_column_sql = IF(
  @email_column_exists = 0,
  'ALTER TABLE user_account ADD COLUMN email_hash CHAR(64) DEFAULT NULL COMMENT ''规范化邮箱盲索引'' AFTER email',
  'SELECT 1'
);
PREPARE email_column_stmt FROM @email_column_sql;
EXECUTE email_column_stmt;
DEALLOCATE PREPARE email_column_stmt;

SET @email_index_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'user_account'
    AND index_name = 'uk_user_account_email_hash'
);
SET @email_index_sql = IF(
  @email_index_exists = 0,
  'CREATE UNIQUE INDEX uk_user_account_email_hash ON user_account(email_hash)',
  'SELECT 1'
);
PREPARE email_index_stmt FROM @email_index_sql;
EXECUTE email_index_stmt;
DEALLOCATE PREPARE email_index_stmt;
