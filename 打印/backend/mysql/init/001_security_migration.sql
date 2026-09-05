SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS ensure_column_definition;
DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;

DELIMITER //

CREATE PROCEDURE ensure_column_definition(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN alter_statement TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @ddl = alter_statement;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

CREATE PROCEDURE add_column_if_missing(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN alter_statement TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @ddl = alter_statement;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

CREATE PROCEDURE add_index_if_missing(
    IN table_name_value VARCHAR(64),
    IN index_name_value VARCHAR(64),
    IN ddl_statement TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND index_name = index_name_value
    ) THEN
        SET @ddl = ddl_statement;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DELIMITER ;

CALL add_column_if_missing('user', 'avatar_url', 'ALTER TABLE `user` ADD COLUMN `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT ''头像公开访问路径'' AFTER `phone`');
CALL add_column_if_missing('service_item', 'category', 'ALTER TABLE `service_item` ADD COLUMN `category` VARCHAR(32) DEFAULT NULL COMMENT ''服务分类'' AFTER `name`');
CALL add_column_if_missing('order_info', 'store_id', 'ALTER TABLE `order_info` ADD COLUMN `store_id` INT DEFAULT NULL COMMENT ''门店ID'' AFTER `service_id`');
CALL add_column_if_missing('order_info', 'copies', 'ALTER TABLE `order_info` ADD COLUMN `copies` INT NOT NULL DEFAULT 1 COMMENT ''打印份数'' AFTER `total_price`');
CALL add_column_if_missing('order_info', 'page_count', 'ALTER TABLE `order_info` ADD COLUMN `page_count` INT NOT NULL DEFAULT 1 COMMENT ''页数'' AFTER `copies`');
CALL add_column_if_missing('order_info', 'duplex', 'ALTER TABLE `order_info` ADD COLUMN `duplex` TINYINT NOT NULL DEFAULT 0 COMMENT ''0单面 1双面'' AFTER `page_count`');
CALL add_column_if_missing('order_info', 'color_mode', 'ALTER TABLE `order_info` ADD COLUMN `color_mode` VARCHAR(20) NOT NULL DEFAULT ''BLACK_WHITE'' COMMENT ''BLACK_WHITE/COLOR'' AFTER `duplex`');
CALL add_column_if_missing('order_info', 'paper_size', 'ALTER TABLE `order_info` ADD COLUMN `paper_size` VARCHAR(20) NOT NULL DEFAULT ''A4'' COMMENT ''A4/A3/PHOTO_6IN/ID_PHOTO'' AFTER `color_mode`');

CALL ensure_column_definition('admin', 'password', 'ALTER TABLE `admin` MODIFY COLUMN `password` VARCHAR(100) NOT NULL COMMENT ''BCrypt密码哈希''');
CALL ensure_column_definition('user', 'avatar_url', 'ALTER TABLE `user` MODIFY COLUMN `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT ''头像公开访问路径''');
CALL ensure_column_definition('user', 'phone', 'ALTER TABLE `user` MODIFY COLUMN `phone` VARCHAR(512) DEFAULT NULL COMMENT ''手机号密文''');
CALL ensure_column_definition('admin', 'phone', 'ALTER TABLE `admin` MODIFY COLUMN `phone` VARCHAR(512) DEFAULT NULL COMMENT ''手机号密文''');
CALL ensure_column_definition('admin', 'email', 'ALTER TABLE `admin` MODIFY COLUMN `email` VARCHAR(512) DEFAULT NULL COMMENT ''邮箱密文''');
CALL ensure_column_definition('file_info', 'file_url', 'ALTER TABLE `file_info` MODIFY COLUMN `file_url` VARCHAR(512) NOT NULL COMMENT ''服务器存储路径''');
CALL ensure_column_definition('order_info', 'copies', 'ALTER TABLE `order_info` MODIFY COLUMN `copies` INT NOT NULL DEFAULT 1 COMMENT ''打印份数''');
CALL ensure_column_definition('order_info', 'page_count', 'ALTER TABLE `order_info` MODIFY COLUMN `page_count` INT NOT NULL DEFAULT 1 COMMENT ''页数''');
CALL ensure_column_definition('order_info', 'duplex', 'ALTER TABLE `order_info` MODIFY COLUMN `duplex` TINYINT NOT NULL DEFAULT 0 COMMENT ''0单面 1双面''');
CALL ensure_column_definition('order_info', 'color_mode', 'ALTER TABLE `order_info` MODIFY COLUMN `color_mode` VARCHAR(20) NOT NULL DEFAULT ''BLACK_WHITE'' COMMENT ''BLACK_WHITE/COLOR''');
CALL ensure_column_definition('order_info', 'paper_size', 'ALTER TABLE `order_info` MODIFY COLUMN `paper_size` VARCHAR(20) NOT NULL DEFAULT ''A4'' COMMENT ''A4/A3/PHOTO_6IN/ID_PHOTO''');

CALL add_index_if_missing('user', 'uk_openid', 'ALTER TABLE `user` ADD UNIQUE KEY `uk_openid` (`openid`)');
CALL add_index_if_missing('order_info', 'idx_order_user_status_time', 'ALTER TABLE `order_info` ADD KEY `idx_order_user_status_time` (`user_id`, `order_status`, `create_time`)');
CALL add_index_if_missing('order_info', 'idx_order_status_time', 'ALTER TABLE `order_info` ADD KEY `idx_order_status_time` (`order_status`, `create_time`)');
CALL add_index_if_missing('file_info', 'idx_file_order', 'ALTER TABLE `file_info` ADD KEY `idx_file_order` (`order_id`)');
CALL add_index_if_missing('store', 'idx_store_status_sort', 'ALTER TABLE `store` ADD KEY `idx_store_status_sort` (`status`, `sort_order`)');

DROP PROCEDURE ensure_column_definition;
DROP PROCEDURE add_column_if_missing;
DROP PROCEDURE add_index_if_missing;
