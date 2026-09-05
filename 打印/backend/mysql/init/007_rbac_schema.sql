-- =====================================================================
-- 007_rbac_schema.sql
-- RBAC 权限体系数据层：角色 / 菜单权限 / 账户-角色 / 角色-菜单
-- 参考 若依(RuoYi) / 芋道(ruoyi-vue-pro) RBAC 模型裁剪适配本平台。
--
-- 设计原则：
--   1. 账户身份源仍是 user_account（username = 注册 QQ 邮箱，唯一）；
--      本脚本只新增"授权"维度，不改动 user_account 的任何字段与数据。
--   2. 现有登录、JWT、订单业务完全不受影响（应用尚未接入新表前）。
--   3. 幂等：可重复执行，重复行被 INSERT IGNORE 跳过。
--   4. 不使用外键约束（与项目既有表风格一致，靠应用层维护）。
--
-- 执行方式一（已有数据的手动执行，本地 Docker）：
--   docker exec -i <mysql容器名> mysql -uroot -p < 007_rbac_schema.sql
-- 执行方式二（宿主机 mysql 客户端）：
--   mysql -h127.0.0.1 -P3306 -uroot -p < 007_rbac_schema.sql
-- 新建库时本文件位于 mysql/init/，容器首次初始化数据目录时自动执行。
-- =====================================================================

SET NAMES utf8mb4;
USE `print_shop`;

-- ---------------------------------------------------------------------
-- 1. 角色表 sys_role
--    data_scope 为数据权限预留：ALL 全部 / STORE 本门店 / SELF 仅本人
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
  `role_key` VARCHAR(32) NOT NULL COMMENT '角色标识(user/superadmin/admin/operator)',
  `role_name` VARCHAR(32) NOT NULL COMMENT '角色名称',
  `role_sort` INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `data_scope` VARCHAR(16) NOT NULL DEFAULT 'SELF' COMMENT '数据范围(ALL全部/STORE本门店/SELF仅本人)',
  `builtin` TINYINT NOT NULL DEFAULT 0 COMMENT '1内置角色,禁止删除',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_sys_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC角色表';

-- ---------------------------------------------------------------------
-- 2. 菜单权限表 sys_menu
--    menu_type: M=目录(预留) C=菜单页面 F=按钮/接口权限点
--    perms 为权限标识，规范：模块:资源:动作，如 print:order:update
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '菜单/权限ID',
  `parent_id` INT NOT NULL DEFAULT 0 COMMENT '父菜单ID(0为根)',
  `menu_name` VARCHAR(64) NOT NULL COMMENT '菜单/权限名称',
  `menu_type` CHAR(1) NOT NULL COMMENT '类型(M目录 C菜单 F按钮)',
  `perms` VARCHAR(128) DEFAULT NULL COMMENT '权限标识(如print:order:update,目录/页面可为空)',
  `path` VARCHAR(255) DEFAULT NULL COMMENT '前端路由路径(C类型)',
  `component` VARCHAR(255) DEFAULT NULL COMMENT '前端组件路径(C类型)',
  `icon` VARCHAR(64) DEFAULT NULL COMMENT '菜单图标(预留)',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `visible` TINYINT NOT NULL DEFAULT 1 COMMENT '1显示 0隐藏',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY `idx_sys_menu_parent` (`parent_id`),
  KEY `idx_sys_menu_perms` (`perms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC菜单权限表';

-- ---------------------------------------------------------------------
-- 3. 账户-角色关联表 sys_user_role（多对多，一期按一人一角色使用）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `account_id` INT NOT NULL COMMENT '账户ID(user_account.id)',
  `role_id` INT NOT NULL COMMENT '角色ID(sys_role.id)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  PRIMARY KEY (`account_id`, `role_id`),
  KEY `idx_sys_user_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户-角色关联表';

-- ---------------------------------------------------------------------
-- 4. 角色-菜单关联表 sys_role_menu
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `role_id` INT NOT NULL COMMENT '角色ID(sys_role.id)',
  `menu_id` INT NOT NULL COMMENT '菜单/权限ID(sys_menu.id)',
  PRIMARY KEY (`role_id`, `menu_id`),
  KEY `idx_sys_role_menu_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-菜单关联表';

-- ---------------------------------------------------------------------
-- 5. 内置角色种子（id 固定，便于 role_menu 种子引用）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_role` (`id`, `role_key`, `role_name`, `role_sort`, `data_scope`, `builtin`, `remark`) VALUES
  (1, 'superadmin', '超级管理员', 1, 'ALL',   1, '内置：平台最高权限，代码级直通所有权限点'),
  (2, 'admin',      '管理员',     2, 'ALL',   1, '内置：全部业务管理，不含管理员账号管理'),
  (3, 'operator',   '操作员',     3, 'STORE', 1, '内置：门店订单处理，数据范围限本门店'),
  (4, 'user',       '普通用户',   4, 'SELF',  1, '内置：平台普通用户，不进入管理端');

-- ---------------------------------------------------------------------
-- 6. 菜单与权限点种子（覆盖当前管理端全部页面与后端写操作）
--    页面(C)与当前 router/index.js 管理端路由一一对应；
--    按钮(F)与各 Controller 的写接口一一对应。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `perms`, `path`, `component`, `icon`, `sort_order`) VALUES
  (10, 0,  '工作台',        'C', 'print:dashboard:view',  '/dashboard', 'admin/views/Dashboard.vue',  NULL, 1),
  (20, 0,  '订单管理',      'C', 'print:order:list',      '/orders',    'admin/views/OrderList.vue',  NULL, 2),
  (21, 20, '订单详情',      'F', 'print:order:query',     NULL, NULL, NULL, 1),
  (22, 20, '订单状态推进',  'F', 'print:order:update',    NULL, NULL, NULL, 2),
  (23, 20, '订单取消',      'F', 'print:order:cancel',    NULL, NULL, NULL, 3),
  (24, 20, '订单删除',      'F', 'print:order:delete',    NULL, NULL, NULL, 4),
  (30, 0,  '服务管理',      'C', 'print:service:list',    '/services',  'admin/views/ServiceList.vue', NULL, 3),
  (31, 30, '服务新增',      'F', 'print:service:add',     NULL, NULL, NULL, 1),
  (32, 30, '服务编辑',      'F', 'print:service:update',  NULL, NULL, NULL, 2),
  (33, 30, '服务删除',      'F', 'print:service:delete',  NULL, NULL, NULL, 3),
  (40, 0,  '门店管理',      'C', 'print:store:list',      '/stores',    'admin/views/StoreList.vue',  NULL, 4),
  (41, 40, '门店新增',      'F', 'print:store:add',       NULL, NULL, NULL, 1),
  (42, 40, '门店编辑',      'F', 'print:store:update',    NULL, NULL, NULL, 2),
  (43, 40, '门店删除',      'F', 'print:store:delete',    NULL, NULL, NULL, 3),
  (50, 0,  '用户管理',      'C', 'print:user:list',       '/users',     'admin/views/UserList.vue',   NULL, 5),
  (51, 50, '用户编辑',      'F', 'print:user:update',     NULL, NULL, NULL, 1),
  (52, 50, '用户删除',      'F', 'print:user:delete',     NULL, NULL, NULL, 2),
  (60, 0,  '管理员管理',    'C', 'print:admin:list',      '/admins',    'admin/views/AdminList.vue',  NULL, 6),
  (61, 60, '管理员新增',    'F', 'print:admin:add',       NULL, NULL, NULL, 1),
  (62, 60, '管理员编辑',    'F', 'print:admin:update',    NULL, NULL, NULL, 2),
  (63, 60, '管理员删除',    'F', 'print:admin:delete',    NULL, NULL, NULL, 3),
  (64, 60, '管理员重置密码','F', 'print:admin:resetPwd',  NULL, NULL, NULL, 4),
  (70, 0,  '客服知识库',    'C', 'chat:knowledge:manage', '/knowledge', 'admin/views/KnowledgeBase.vue', NULL, 7),
  (71, 70, '知识导入',      'F', 'chat:knowledge:import', NULL, NULL, NULL, 1),
  (72, 70, '知识删除',      'F', 'chat:knowledge:delete', NULL, NULL, NULL, 2),
  (80, 0,  'AI 证件照',     'C', NULL, '/photo',    'admin/views/Photo.vue',    NULL, 8),
  (81, 0,  '课表查询',      'C', NULL, '/schedule', 'admin/views/Schedule.vue', NULL, 9),
  (82, 0,  'OCR 转文档',    'C', NULL, '/ocr',      'admin/views/Ocr.vue',      NULL, 10),
  (83, 0,  '智能客服',      'C', NULL, '/chat',     'admin/views/Chat.vue',     NULL, 11),
  (84, 0,  '个人中心',      'C', NULL, '/profile',  'admin/views/Profile.vue',  NULL, 12);

-- ---------------------------------------------------------------------
-- 7. 角色-菜单默认授权种子
--    superadmin：全量（此后新增菜单自动全量）
--    admin     ：全量，但不含管理员账号管理的写按钮(61-64)
--    operator  ：工作台 + 订单(查看/状态推进) + 工具页 + 个人中心
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
  SELECT 1, `id` FROM `sys_menu`;

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
  SELECT 2, `id` FROM `sys_menu` WHERE `id` NOT IN (61, 62, 63, 64);

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
  (3, 10), (3, 20), (3, 21), (3, 22),
  (3, 80), (3, 81), (3, 82), (3, 83), (3, 84);

-- ---------------------------------------------------------------------
-- 8. 存量账户角色回填：user_account.role -> sys_user_role
--    一人一角色；role 值不在四内置角色内的账户会被跳过（见验证2）。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_user_role` (`account_id`, `role_id`)
  SELECT a.`id`, r.`id`
  FROM `user_account` a
  JOIN `sys_role` r ON r.`role_key` = a.`role`;

-- ---------------------------------------------------------------------
-- 9. 应用层查询视图
--    v_account_roles ：账户 -> 角色标识集合（AuthInterceptor 装载用）
--    v_account_perms ：账户 -> 权限标识集合（requirePermission 校验用）
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW `v_account_roles` AS
  SELECT ur.`account_id`, r.`role_key`
  FROM `sys_user_role` ur
  JOIN `sys_role` r ON r.`id` = ur.`role_id` AND r.`status` = 1;

CREATE OR REPLACE VIEW `v_account_perms` AS
  SELECT ur.`account_id`, m.`perms` AS `permission`
  FROM `sys_user_role` ur
  JOIN `sys_role` r     ON r.`id` = ur.`role_id` AND r.`status` = 1
  JOIN `sys_role_menu` rm ON rm.`role_id` = ur.`role_id`
  JOIN `sys_menu` m     ON m.`id` = rm.`menu_id` AND m.`status` = 1
  WHERE m.`perms` IS NOT NULL AND m.`perms` <> '';

-- =====================================================================
-- 执行后验证（手工执行，预期结果见注释）
-- =====================================================================
-- 验证1：四张表 + 视图已建立
--   SHOW TABLES LIKE 'sys_%';  SHOW FULL TABLES WHERE Table_type = 'VIEW';
-- 验证2：所有账户都完成了角色回填（预期返回空集）
--   SELECT a.`role`, COUNT(*) FROM `user_account` a
--    LEFT JOIN `sys_user_role` ur ON ur.`account_id` = a.`id`
--    WHERE ur.`role_id` IS NULL GROUP BY a.`role`;
-- 验证3：抽查 superadmin 的权限点数量（预期 25 个：30 条菜单种子中 25 条带 perms）
--   SELECT COUNT(*) FROM `v_account_perms` WHERE `account_id` =
--     (SELECT `id` FROM `user_account` WHERE `role` = 'superadmin' LIMIT 1);
-- =====================================================================
