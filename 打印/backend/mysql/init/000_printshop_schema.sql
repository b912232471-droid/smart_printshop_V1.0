SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `user` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '用户主键',
  `openid` VARCHAR(64) NOT NULL COMMENT '微信OpenID',
  `username` VARCHAR(64) DEFAULT NULL COMMENT '用户昵称',
  `phone` VARCHAR(512) DEFAULT NULL COMMENT '手机号密文',
  `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT '头像公开访问路径',
  `register_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `last_login_time` DATETIME DEFAULT NULL COMMENT '最近登录时间',
  UNIQUE KEY `uk_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `admin` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '管理员ID',
  `username` VARCHAR(64) NOT NULL COMMENT '登录用户名',
  `password` VARCHAR(100) NOT NULL COMMENT 'BCrypt密码哈希',
  `real_name` VARCHAR(64) DEFAULT NULL COMMENT '真实姓名',
  `phone` VARCHAR(512) DEFAULT NULL COMMENT '手机号密文',
  `email` VARCHAR(512) DEFAULT NULL COMMENT '邮箱密文',
  `role` VARCHAR(32) NOT NULL DEFAULT 'admin' COMMENT 'superadmin/admin/operator',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  UNIQUE KEY `uk_admin_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

CREATE TABLE IF NOT EXISTS `service_item` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '服务ID',
  `name` VARCHAR(64) NOT NULL COMMENT '服务名称',
  `category` VARCHAR(32) DEFAULT NULL COMMENT '服务分类',
  `price` DECIMAL(10,2) NOT NULL COMMENT '单价',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '服务描述',
  KEY `idx_service_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务项目表';

CREATE TABLE IF NOT EXISTS `store` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '门店ID',
  `name` VARCHAR(128) NOT NULL COMMENT '门店名称',
  `short_name` VARCHAR(64) DEFAULT NULL COMMENT '短名称',
  `device_code` VARCHAR(64) DEFAULT NULL COMMENT '设备号',
  `address` VARCHAR(255) DEFAULT NULL COMMENT '门店地址',
  `image_url` VARCHAR(512) DEFAULT NULL COMMENT '门店图片公开访问路径',
  `latitude` DECIMAL(10,7) DEFAULT NULL COMMENT '纬度',
  `longitude` DECIMAL(10,7) DEFAULT NULL COMMENT '经度',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `hours` VARCHAR(128) DEFAULT NULL COMMENT '营业时间',
  `services` VARCHAR(255) DEFAULT NULL COMMENT '服务列表',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1营业中 0已关闭',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_store_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店表';

CREATE TABLE IF NOT EXISTS `order_info` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '订单ID',
  `user_id` INT NOT NULL COMMENT '用户ID',
  `service_id` INT NOT NULL COMMENT '服务ID',
  `store_id` INT DEFAULT NULL COMMENT '门店ID',
  `file_id` INT DEFAULT NULL COMMENT '主文件ID',
  `appoint_time` DATETIME NOT NULL COMMENT '预约时间',
  `queue_number` VARCHAR(20) DEFAULT NULL COMMENT '排队号码',
  `total_price` DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
  `copies` INT NOT NULL DEFAULT 1 COMMENT '打印份数',
  `page_count` INT NOT NULL DEFAULT 1 COMMENT '页数',
  `duplex` TINYINT NOT NULL DEFAULT 0 COMMENT '0单面 1双面',
  `color_mode` VARCHAR(20) NOT NULL DEFAULT 'BLACK_WHITE' COMMENT 'BLACK_WHITE/COLOR',
  `paper_size` VARCHAR(20) NOT NULL DEFAULT 'A4' COMMENT 'A4/A3/PHOTO_6IN/ID_PHOTO',
  `order_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1打印中 2待取件 3已完成 4已取消',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `fetch_code` VARCHAR(16) DEFAULT NULL COMMENT '取件码',
  KEY `idx_order_user_status_time` (`user_id`, `order_status`, `create_time`),
  KEY `idx_order_status_time` (`order_status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

CREATE TABLE IF NOT EXISTS `file_info` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '文件ID',
  `order_id` INT NOT NULL COMMENT '关联订单ID',
  `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `file_url` VARCHAR(512) NOT NULL COMMENT '服务器存储路径',
  `upload_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  KEY `idx_file_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件表';

INSERT INTO `service_item` (`name`, `category`, `price`, `description`)
SELECT * FROM (
  SELECT 'A4黑白打印', '文档', 0.20, '标准A4纸张黑白打印' UNION ALL
  SELECT 'A4彩色打印', '文档', 1.50, 'A4彩色打印' UNION ALL
  SELECT 'A3黑白打印', '文档', 0.50, 'A3大幅面黑白打印' UNION ALL
  SELECT 'A3彩色打印', '文档', 3.00, 'A3大幅面彩色打印' UNION ALL
  SELECT '照片打印6寸', '照片', 2.00, '6寸照片打印' UNION ALL
  SELECT '证件照打印', '证件照', 10.00, '一寸/二寸证件照打印' UNION ALL
  SELECT '文件装订', '文档', 5.00, '文件装订服务' UNION ALL
  SELECT '扫描服务', '文档', 1.00, '文件扫描成电子版'
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM `service_item` LIMIT 1);
