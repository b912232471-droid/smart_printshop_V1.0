-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: print_shop
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `print_shop`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `print_shop` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `print_shop`;

--
-- Table structure for table `admin`
--

DROP TABLE IF EXISTS `admin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '管理员ID',
  `username` varchar(64) NOT NULL COMMENT '登录用户名',
  `password` varchar(100) NOT NULL COMMENT 'BCrypt密码哈希',
  `real_name` varchar(64) DEFAULT NULL COMMENT '真实姓名',
  `phone` varchar(512) DEFAULT NULL COMMENT '手机号密文',
  `email` varchar(512) DEFAULT NULL COMMENT '邮箱密文',
  `role` varchar(32) NOT NULL DEFAULT 'admin' COMMENT 'superadmin/admin/operator',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1启用 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `admin`
--

LOCK TABLES `admin` WRITE;
/*!40000 ALTER TABLE `admin` DISABLE KEYS */;
/*!40000 ALTER TABLE `admin` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `file_info`
--

DROP TABLE IF EXISTS `file_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_info` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `order_id` int NOT NULL COMMENT '关联订单ID',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_url` varchar(512) NOT NULL COMMENT '服务器存储路径',
  `upload_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_file_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `file_info`
--

LOCK TABLES `file_info` WRITE;
/*!40000 ALTER TABLE `file_info` DISABLE KEYS */;
/*!40000 ALTER TABLE `file_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `order_info`
--

DROP TABLE IF EXISTS `order_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_info` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `user_id` int NOT NULL COMMENT '用户ID',
  `service_id` int NOT NULL COMMENT '服务ID',
  `store_id` int DEFAULT NULL COMMENT '门店ID',
  `file_id` int DEFAULT NULL COMMENT '主文件ID',
  `appoint_time` datetime NOT NULL COMMENT '预约时间',
  `queue_number` varchar(20) DEFAULT NULL COMMENT '排队号码',
  `total_price` decimal(10,2) NOT NULL COMMENT '订单总金额',
  `copies` int NOT NULL DEFAULT '1' COMMENT '打印份数',
  `page_count` int NOT NULL DEFAULT '1' COMMENT '页数',
  `duplex` tinyint NOT NULL DEFAULT '0' COMMENT '0单面 1双面',
  `color_mode` varchar(20) NOT NULL DEFAULT 'BLACK_WHITE' COMMENT 'BLACK_WHITE/COLOR',
  `paper_size` varchar(20) NOT NULL DEFAULT 'A4' COMMENT 'A4/A3/PHOTO_6IN/ID_PHOTO',
  `order_status` tinyint NOT NULL DEFAULT '0' COMMENT '0待处理 1打印中 2待取件 3已完成 4已取消',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `fetch_code` varchar(16) DEFAULT NULL COMMENT '取件码',
  PRIMARY KEY (`id`),
  KEY `idx_order_user_status_time` (`user_id`,`order_status`,`create_time`),
  KEY `idx_order_status_time` (`order_status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `order_info`
--

LOCK TABLES `order_info` WRITE;
/*!40000 ALTER TABLE `order_info` DISABLE KEYS */;
/*!40000 ALTER TABLE `order_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `service_item`
--

DROP TABLE IF EXISTS `service_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `service_item` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '服务ID',
  `name` varchar(64) NOT NULL COMMENT '服务名称',
  `category` varchar(32) DEFAULT NULL COMMENT '服务分类',
  `price` decimal(10,2) NOT NULL COMMENT '单价',
  `description` varchar(255) DEFAULT NULL COMMENT '服务描述',
  PRIMARY KEY (`id`),
  KEY `idx_service_category` (`category`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务项目表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `service_item`
--

LOCK TABLES `service_item` WRITE;
/*!40000 ALTER TABLE `service_item` DISABLE KEYS */;
INSERT INTO `service_item` VALUES (1,'A4黑白打印','文档',0.20,'标准A4纸张黑白打印'),(2,'A4彩色打印','文档',1.50,'A4彩色打印'),(3,'A3黑白打印','文档',0.50,'A3大幅面黑白打印'),(4,'A3彩色打印','文档',3.00,'A3大幅面彩色打印'),(5,'照片打印6寸','照片',2.00,'6寸照片打印'),(6,'证件照打印','证件照',10.00,'一寸/二寸证件照打印'),(7,'文件装订','文档',5.00,'文件装订服务'),(8,'扫描服务','文档',1.00,'文件扫描成电子版');
/*!40000 ALTER TABLE `service_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `store`
--

DROP TABLE IF EXISTS `store`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `store` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '门店ID',
  `name` varchar(128) NOT NULL COMMENT '门店名称',
  `short_name` varchar(64) DEFAULT NULL COMMENT '短名称',
  `device_code` varchar(64) DEFAULT NULL COMMENT '设备号',
  `address` varchar(255) DEFAULT NULL COMMENT '门店地址',
  `image_url` varchar(512) DEFAULT NULL COMMENT '门店图片公开访问路径',
  `latitude` decimal(10,7) DEFAULT NULL COMMENT '纬度',
  `longitude` decimal(10,7) DEFAULT NULL COMMENT '经度',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `hours` varchar(128) DEFAULT NULL COMMENT '营业时间',
  `services` varchar(255) DEFAULT NULL COMMENT '服务列表',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1营业中 0已关闭',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_store_status_sort` (`status`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `store`
--

LOCK TABLES `store` WRITE;
/*!40000 ALTER TABLE `store` DISABLE KEYS */;
/*!40000 ALTER TABLE `store` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '用户主键',
  `openid` varchar(64) NOT NULL COMMENT '微信OpenID',
  `username` varchar(64) DEFAULT NULL COMMENT '用户昵称',
  `phone` varchar(512) DEFAULT NULL COMMENT '手机号密文',
  `avatar_url` varchar(512) DEFAULT NULL COMMENT '头像公开访问路径',
  `register_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最近登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'print_shop'
--

--
-- Dumping routines for database 'print_shop'
--

--
-- Current Database: `schedule_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `schedule_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `schedule_db`;

--
-- Table structure for table `course_schedules`
--

DROP TABLE IF EXISTS `course_schedules`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_schedules` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '课表ID',
  `user_id` bigint NOT NULL COMMENT '平台用户ID',
  `student_id` varchar(64) NOT NULL COMMENT '学号',
  `xnm` varchar(16) NOT NULL COMMENT '学年',
  `xqm` varchar(16) NOT NULL COMMENT '学期',
  `kcmc` varchar(255) NOT NULL COMMENT '课程名称',
  `xqj` varchar(32) DEFAULT NULL COMMENT '星期',
  `jcs` varchar(64) DEFAULT NULL COMMENT '节次',
  `cdmc` varchar(255) DEFAULT NULL COMMENT '地点',
  `xm` varchar(128) DEFAULT NULL COMMENT '教师',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_course_user_term` (`user_id`,`xnm`,`xqm`),
  KEY `idx_course_student_term` (`student_id`,`xnm`,`xqm`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教务课表表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_schedules`
--

LOCK TABLES `course_schedules` WRITE;
/*!40000 ALTER TABLE `course_schedules` DISABLE KEYS */;
/*!40000 ALTER TABLE `course_schedules` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jw_accounts`
--

DROP TABLE IF EXISTS `jw_accounts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `jw_accounts` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '教务账号ID',
  `user_id` bigint NOT NULL COMMENT '平台用户ID',
  `student_id` varchar(64) NOT NULL COMMENT '学号',
  `jw_username` varchar(128) NOT NULL COMMENT '教务系统用户名',
  `jw_password` varchar(1024) NOT NULL COMMENT 'AES-GCM密文密码',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jw_accounts_user` (`user_id`),
  KEY `idx_jw_accounts_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教务账号绑定表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jw_accounts`
--

LOCK TABLES `jw_accounts` WRITE;
/*!40000 ALTER TABLE `jw_accounts` DISABLE KEYS */;
/*!40000 ALTER TABLE `jw_accounts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'schedule_db'
--

--
-- Dumping routines for database 'schedule_db'
--

--
-- Current Database: `chatbot_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `chatbot_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `chatbot_db`;

--
-- Table structure for table `chat_logs`
--

DROP TABLE IF EXISTS `chat_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `session_id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `question` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `answer` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `source` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_ids` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chat_logs_user` (`user_type`,`user_id`),
  KEY `idx_chat_logs_session` (`session_id`),
  KEY `idx_chat_logs_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='å®¢æœå¯¹è¯æ—¥å¿—';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `chat_logs`
--

LOCK TABLES `chat_logs` WRITE;
/*!40000 ALTER TABLE `chat_logs` DISABLE KEYS */;
/*!40000 ALTER TABLE `chat_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `knowledge_categories`
--

DROP TABLE IF EXISTS `knowledge_categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='çŸ¥è¯†åº“åˆ†ç±»';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `knowledge_categories`
--

LOCK TABLES `knowledge_categories` WRITE;
/*!40000 ALTER TABLE `knowledge_categories` DISABLE KEYS */;
INSERT INTO `knowledge_categories` VALUES (1,'ä¸‹å•æ‰“å°','æ–‡ä»¶ä¸Šä¼ ã€æ‰“å°å‚æ•°ã€è®¢å•åˆ›å»ºä¸Žæ¨¡æ‹Ÿæ”¯ä»˜','2026-07-11 08:35:20'),(2,'è¯ä»¶ç…§','è¯ä»¶ç…§ç”Ÿæˆã€æ¢åº•è‰²ã€ä¿å­˜ç›¸å†Œ','2026-07-11 08:35:20'),(3,'è¯¾è¡¨','æ•™åŠ¡è´¦å·ç»‘å®šã€è¯¾è¡¨æŸ¥è¯¢å’ŒåŒæ­¥','2026-07-11 08:35:20'),(4,'å®¢æœ','äººå·¥å®¢æœä¸Žå¸¸è§å”®åŽé—®é¢˜','2026-07-11 08:35:20');
/*!40000 ALTER TABLE `knowledge_categories` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `knowledge_items`
--

DROP TABLE IF EXISTS `knowledge_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `answer` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'é€šç”¨',
  `tags` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_category` (`category`),
  KEY `idx_knowledge_enabled` (`enabled`),
  FULLTEXT KEY `idx_knowledge_fulltext` (`question`,`answer`,`category`,`tags`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='çŸ¥è¯†åº“æ¡ç›®';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `knowledge_items`
--

LOCK TABLES `knowledge_items` WRITE;
/*!40000 ALTER TABLE `knowledge_items` DISABLE KEYS */;
INSERT INTO `knowledge_items` VALUES (1,'å¦‚ä½•ä¸Šä¼ æ–‡ä»¶å¹¶ä¸‹å•æ‰“å°ï¼Ÿ','åœ¨é¦–é¡µé€‰æ‹©æ‰“å°æœåŠ¡ï¼Œä¸Šä¼  PDFã€Wordã€Excel æˆ–å›¾ç‰‡æ–‡ä»¶ï¼Œé€‰æ‹©é—¨åº—å’Œæ‰“å°å‚æ•°åŽæäº¤è®¢å•ã€‚å½“å‰æ”¯ä»˜ä¸ºæ¨¡æ‹Ÿæ”¯ä»˜ï¼Œç”¨äºŽæ¼”ç¤ºè®¢å•çŠ¶æ€æµè½¬ã€‚','ä¸‹å•æ‰“å°','[\"ä¸Šä¼ \",\"ä¸‹å•\",\"æ‰“å°\",\"æ¨¡æ‹Ÿæ”¯ä»˜\"]',1,'2026-07-11 08:35:20','2026-07-11 08:35:20'),(2,'è¯ä»¶ç…§å¯ä»¥åšå“ªäº›å¤„ç†ï¼Ÿ','è¯ä»¶ç…§åŠŸèƒ½æ”¯æŒä¸Šä¼ ç…§ç‰‡åŽç”Ÿæˆå¸¸ç”¨å°ºå¯¸è¯ä»¶ç…§ï¼Œä¹Ÿæ”¯æŒç™½åº•ã€è“åº•ã€çº¢åº•ç­‰èƒŒæ™¯æ›¿æ¢ã€‚å¤„ç†å®ŒæˆåŽå¯ä»¥é¢„è§ˆå¹¶ä¿å­˜åˆ°ç›¸å†Œã€‚','è¯ä»¶ç…§','[\"è¯ä»¶ç…§\",\"æ¢åº•è‰²\",\"ä¿å­˜\"]',1,'2026-07-11 08:35:20','2026-07-11 08:35:20'),(3,'è¯¾è¡¨åŒæ­¥å¤±è´¥æ€Žä¹ˆåŠžï¼Ÿ','è¯·å…ˆç¡®è®¤å·²ç»‘å®šæ­£ç¡®çš„æ•™åŠ¡è´¦å·å’Œå¯†ç ã€‚è‹¥æç¤ºæ•™åŠ¡åŒæ­¥é€‚é…å™¨æœªé…ç½®ï¼Œè¯´æ˜Žå½“å‰çŽ¯å¢ƒè¿˜æ²¡æœ‰æŽ¥å…¥çœŸå®žå­¦æ ¡æ•™åŠ¡ç³»ç»Ÿï¼Œå¯å…ˆæŸ¥çœ‹å·²ç¼“å­˜è¯¾è¡¨æˆ–ç¨åŽå†è¯•ã€‚','è¯¾è¡¨','[\"è¯¾è¡¨\",\"æ•™åŠ¡\",\"åŒæ­¥\"]',1,'2026-07-11 08:35:20','2026-07-11 08:35:20'),(4,'å¦‚ä½•è”ç³»äººå·¥å®¢æœï¼Ÿ','å¦‚ AI å®¢æœæ— æ³•è§£å†³é—®é¢˜ï¼Œè¯·æ‹¨æ‰“å®¢æœç”µè¯ 400-778-1811ï¼ŒæœåŠ¡æ—¶é—´ä¸º 9:00-22:00ã€‚','å®¢æœ','[\"äººå·¥å®¢æœ\",\"ç”µè¯\",\"çƒ­çº¿\"]',1,'2026-07-11 08:35:20','2026-07-11 08:35:20');
/*!40000 ALTER TABLE `knowledge_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'chatbot_db'
--

--
-- Dumping routines for database 'chatbot_db'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-11 19:38:59
