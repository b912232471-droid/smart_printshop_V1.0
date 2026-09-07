-- =====================================================================
-- 008_imagegen_schema.sql
-- AI 图片生成功能数据层：生成记录 / 运营配置+审计 / 校园模板 / RBAC 授权
-- 设计依据：backend/docs/图文生成功能更新迭代方案-2026-09-06.md 第 3 节
--
-- 设计原则：
--   1. 纯增量：不修改 000-007 任何既有表结构与数据。
--      生成图转打印订单走既有 createOrder -> file_info 主链路，
--      本脚本仅新增溯源维度（image_gen_record.order_id 回填）。
--   2. photo-service 不建表、不连库（保持无状态多实例约束），
--      全部业务数据落在 print_shop，由 print-service 读写。
--   3. 幂等：可重复执行，CREATE TABLE IF NOT EXISTS + INSERT IGNORE。
--   4. 不使用外键约束（与项目既有表风格一致，靠应用层维护）。
--   5. 配置/模板种子只在缺失时插入，重跑不会覆盖管理员已调整的值。
--
-- 执行方式一（已有数据的手动执行，本地 Docker）：
--   docker exec -i <mysql容器名> mysql -uroot -p < 008_imagegen_schema.sql
-- 执行方式二（宿主机 mysql 客户端）：
--   mysql -h127.0.0.1 -P3306 -uroot -p < 008_imagegen_schema.sql
-- 新建库时本文件位于 mysql/init/，容器首次初始化数据目录时自动执行。
-- =====================================================================

SET NAMES utf8mb4;
USE `print_shop`;

-- ---------------------------------------------------------------------
-- 1. 生成记录表 image_gen_record
--    一行 = 一次成功生成；审核拒绝也落行（moderation_status=2）便于审计。
--    token 用于 owner-scoped 下载（对齐 OCR 的 ownerId+token 模式）。
--    cost_amount 为调用时刻模型单价快照，后续调价不影响历史台账。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `image_gen_record` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
  `account_id` INT NOT NULL COMMENT '生成者(user_account.id)',
  `token` CHAR(36) NOT NULL COMMENT '下载令牌(UUID)',
  `model_id` VARCHAR(64) NOT NULL COMMENT '模型标识(IMAGE_MODEL_CATALOG内)',
  `prompt` VARCHAR(600) NOT NULL COMMENT '最终生效提示词',
  `polished` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否经过AI润色',
  `template_key` VARCHAR(64) DEFAULT NULL COMMENT '使用的模板标识(image_gen_template.template_key)',
  `file_url` VARCHAR(512) NOT NULL COMMENT '存储路径(/files/imagegen/...)',
  `width` INT NOT NULL COMMENT '图片宽(px)',
  `height` INT NOT NULL COMMENT '图片高(px)',
  `size` VARCHAR(32) NOT NULL COMMENT '模型size参数',
  `usage_tokens` INT DEFAULT NULL COMMENT '模型返回token用量',
  `cost_amount` DECIMAL(10,4) DEFAULT NULL COMMENT '成本快照(元)',
  `duration_ms` INT DEFAULT NULL COMMENT '生成耗时(毫秒)',
  `moderation_status` TINYINT NOT NULL DEFAULT 0 COMMENT '审核状态(0未审/1通过/2拒绝)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0用户已删除',
  `order_id` INT DEFAULT NULL COMMENT '转打印订单后回填(order_info.id)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY `uk_gen_token` (`token`),
  KEY `idx_gen_account` (`account_id`, `created_at`),
  KEY `idx_gen_created` (`created_at`),
  KEY `idx_gen_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI图片生成记录';

-- ---------------------------------------------------------------------
-- 2. 运营配置表 image_gen_settings（单行表，镜像 chat_settings 运营模式）
--    管理端"AI 图片生成-运营配置"页读写，变更写审计表。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `image_gen_settings` (
  `id` TINYINT PRIMARY KEY COMMENT '固定为1',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '功能总开关(0全站停用)',
  `default_model` VARCHAR(64) NOT NULL DEFAULT 'step-image-edit-2' COMMENT '默认模型',
  `daily_quota_per_user` INT NOT NULL DEFAULT 5 COMMENT '每用户每日生成张数上限',
  `max_prompt_length` INT NOT NULL DEFAULT 500 COMMENT '提示词最大长度',
  `polish_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'prompt AI润色开关',
  `moderation_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '生成图内容审核开关(生产建议恒开)',
  `templates_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '校园模板开关',
  `watermark_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '水印脚注开关(仅声明支持的模型生效)',
  `watermark_text` VARCHAR(64) DEFAULT NULL COMMENT '水印脚注文案',
  `daily_cost_cap` DECIMAL(10,2) NOT NULL DEFAULT 50.00 COMMENT '全平台单日成本上限(元),超限熔断',
  `updated_by` BIGINT DEFAULT NULL COMMENT '最后修改人(account_id)',
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI图片生成运营配置';

-- 配置审计表：变更前后 JSON 快照（镜像 chat_setting_audits）
CREATE TABLE IF NOT EXISTS `image_gen_setting_audits` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '审计ID',
  `admin_id` BIGINT NOT NULL COMMENT '操作管理员(account_id)',
  `before_json` LONGTEXT NOT NULL COMMENT '变更前配置JSON',
  `after_json` LONGTEXT NOT NULL COMMENT '变更后配置JSON',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI图片生成配置审计';

-- 默认配置行（仅缺失时插入，不覆盖已调整值）
INSERT INTO `image_gen_settings` (`id`)
  SELECT 1 FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM `image_gen_settings` WHERE `id` = 1);

-- ---------------------------------------------------------------------
-- 3. 校园模板表 image_gen_template
--    prompt_template 中 {xxx} 为占位符，由前端按 fields_json 渲染填空后拼装。
--    recommended_size 取值以方案 M0 联调确认的 size 格式为准，
--    联调后如需调整直接 UPDATE 本表，不改代码。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `image_gen_template` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '模板ID',
  `template_key` VARCHAR(64) NOT NULL COMMENT '模板标识',
  `title` VARCHAR(100) NOT NULL COMMENT '模板标题',
  `category` VARCHAR(30) NOT NULL COMMENT '分类(handout手抄报/poster海报/notice通知/photo照片)',
  `prompt_template` VARCHAR(600) NOT NULL COMMENT '提示词模板(含{xxx}占位符)',
  `recommended_model` VARCHAR(64) NOT NULL COMMENT '推荐模型',
  `recommended_size` VARCHAR(32) NOT NULL COMMENT '推荐尺寸',
  `fields_json` VARCHAR(500) NOT NULL COMMENT '前端填空字段定义JSON',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  UNIQUE KEY `uk_tpl_key` (`template_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI生成校园模板';

INSERT IGNORE INTO `image_gen_template`
  (`template_key`, `title`, `category`, `prompt_template`, `recommended_model`, `recommended_size`, `fields_json`, `sort_order`) VALUES
  ('handout_festival', '节日手抄报', 'handout',
   '小学生手抄报版面设计，主题：{topic}，顶部大标题"{title}"，分为3个图文板块，包含{elements}相关插图，中文文字清晰工整，色彩明快，A4竖版，留出可书写的空白区域',
   'step-image-edit-2', '896x1184',
   '[{"key":"topic","label":"主题","placeholder":"如：中秋节"},{"key":"title","label":"标题"},{"key":"elements","label":"插图元素","placeholder":"如：月亮、兔子、桂花"}]', 1),
  ('poster_club', '社团招新海报', 'poster',
   '大学社团招新海报，社团名称"{title}"，主题风格{style}，突出文案"{slogan}"，底部预留报名方式区域，构图醒目有青春感',
   'step-image-edit-2', '896x1184',
   '[{"key":"title","label":"社团名称"},{"key":"slogan","label":"招新口号"},{"key":"style","label":"风格","placeholder":"如：国潮/简约/像素风"}]', 2),
  ('notice_board', '活动通知海报', 'notice',
   '校园活动通知海报，活动名称"{title}"，时间"{time}"，地点"{place}"，信息层级清晰易读，正式但活泼',
   'step-image-edit-2', '896x1184',
   '[{"key":"title","label":"活动名称"},{"key":"time","label":"时间"},{"key":"place","label":"地点"}]', 3);

-- ---------------------------------------------------------------------
-- 4. RBAC 菜单与授权种子（接续 007 的 id 段，84 之后）
--    权限点前缀 photo: 对应 photo-service 能域，共新增 2 个权限点，
--    全平台权限点 25 -> 27，仍远低于 GROUP_CONCAT 1024 字节上限。
--    菜单 85 为 C 类型页面（无 perms），86/87 为 F 类型权限点。
--    superadmin/admin 授权；operator 不授权，需要时管理端手工勾选。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `perms`, `path`, `component`, `icon`, `sort_order`) VALUES
  (85, 0,  'AI 图片生成',  'C', NULL,                    '/imagegen', 'admin/views/ImageGenOps.vue', NULL, 13),
  (86, 85, '生成配置管理', 'F', 'photo:imagegen:manage', NULL, NULL, NULL, 1),
  (87, 85, '生成记录查看', 'F', 'photo:imagegen:query',  NULL, NULL, NULL, 2);

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
  SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (85, 86, 87);

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
  SELECT 2, `id` FROM `sys_menu` WHERE `id` IN (85, 86, 87);

-- ---------------------------------------------------------------------
-- 5. 执行后验证（手动抽查）
--   验证1：新表齐全
--     SHOW TABLES LIKE 'image_gen%';        -- 预期 4 张
--   验证2：默认配置行存在
--     SELECT id, enabled, default_model, daily_quota_per_user, daily_cost_cap
--       FROM image_gen_settings;            -- 预期 1 行默认值
--   验证3：模板种子
--     SELECT template_key, title, category FROM image_gen_template;  -- 预期 3 行
--   验证4：菜单与授权
--     SELECT id, menu_name, perms FROM sys_menu WHERE id IN (85,86,87);
--     SELECT role_id, COUNT(*) FROM sys_role_menu
--       WHERE menu_id IN (85,86,87) GROUP BY role_id;  -- 预期 role 1、2 各 3 行
--   验证5：superadmin 权限点数量（登录后）
--     SELECT COUNT(*) FROM v_account_perms
--       WHERE account_id = <superadmin账户id>;         -- 预期 27（25+2）
-- =====================================================================
