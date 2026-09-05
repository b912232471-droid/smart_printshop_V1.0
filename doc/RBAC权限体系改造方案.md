# 校园一站式打印平台 RBAC 权限体系改造方案

| 项 | 内容 |
|---|---|
| 版本 | V1.0（2026-09-05） |
| 对标模型 | 若依（RuoYi）/ 芋道（ruoyi-vue-pro、yu-dao-cloud）RBAC 权限架构 |
| 配套 SQL | `打印/backend/mysql/init/007_rbac_schema.sql`（已随本方案交付，可直接执行） |
| 改造范围 | 仅管理端（admin 链路）；用户端（client / 小程序）不引入 RBAC，保持现状 |

---

## 1. 改造背景与目标

### 1.1 现状问题

当前平台权限体系是「扁平角色 + 硬编码三档检查」，具体：

1. `user_account` 表上只有一个 `role` 字符串字段（`user / superadmin / admin / operator`），**没有角色表、菜单权限表、关联表**，新增角色必须改代码重新发版。
2. 后端鉴权只有 `AuthContext.requireUser() / requireAdmin() / requireSuperAdmin()` 三档硬编码（全项目约 30 处调用点），**没有权限标识（perms）级别的检查**。
3. **`operator`（操作员）是一个"假角色"**：数据库允许创建、前端能分配、界面显示"操作员"标签，但后端所有管理接口都只做 `requireAdmin()`，operator 与 admin 实际权限完全相同。
4. 前端路由表（`printshop-web/src/router/index.js`）纯静态，管理端所有页面只挂 `adminAuth`（有无 token）守卫，**菜单全量渲染**；唯一的按钮级控制在 `AdminList.vue` 中读 localStorage 硬判断，可被绕过。
5. 没有数据权限：任何管理员可以看到所有门店、所有订单。

### 1.2 目标形态（对标若依/芋道）

| 能力 | 说明 |
|---|---|
| 角色可运营 | 角色存 `sys_role` 表，新增/停用角色零代码改动 |
| 权限点化 | 每个菜单页面、每个按钮/接口都有权限标识（如 `print:order:update`），存 `sys_menu` |
| 授权关系持久化 | `sys_user_role`（账户↔角色）、`sys_role_menu`（角色↔权限点），运营期可随时调整 |
| 接口级鉴权 | 后端 `requirePermission("print:order:update")` 替换三档硬编码 |
| 动态菜单 | 登录后端下发菜单树，前端 `router.addRoute` 动态注册，菜单按角色过滤 |
| 按钮级控制 | 自定义 `v-permission` 指令，无权限按钮直接不渲染 |
| 数据权限 | `sys_role.data_scope`（ALL 全部 / STORE 本门店 / SELF 仅本人），一期落地"操作员只看本门店订单" |

### 1.3 范围声明

- **管理端专属**：RBAC 只服务管理侧（`account_type = admin` 的账户）。普通用户端（client web / 小程序）业务上不需要角色体系，其接口继续走 `requireUser()` 类型检查，不在本次改造范围。
- **身份不动**：账户身份源仍是 `user_account`，`username` 就是注册时的 **QQ 邮箱**（`uk_user_account_username` 唯一索引）。本次改造**不改动 `user_account` 的任何字段和数据**，RBAC 表通过 `account_id` 关联，邮箱永不复制进权限表——即使用户后续换绑邮箱（`/api/auth/email/bind`），授权关系也不受影响。

---

## 2. 当前数据库连接信息

> 依据：`打印/backend/.env.local`（本地开发环境变量）、`docker-compose.yml` + `docker-compose.dev.yml`。
> 按 AGENTS.md 协作约定（文档不落真实密钥），本文密码做脱敏显示，**完整值请按行号查 `.env.local`**。

### 2.1 部署形态

```
宿主机 127.0.0.1:3306（docker-compose.dev.yml 发布端口）
        │
   MySQL 8.0 容器（compose 服务名 mysql，容器内 3306）
        ├── print_shop    ← print-service（Java :8081）
        ├── schedule_db   ← schedule-service（Java :8092）
        └── chatbot_db    ← chat-service（Python :8093）
```

- 三库的账户授权在初始化 SQL 中完成：`mysql/init/002_schedule_schema.sql:7`、`mysql/init/003_chat_schema.sql:3`（`GRANT ALL ON 库.* TO 'print_shop'@'%'`）。
- **全平台共用一个 MySQL 账户 `print_shop`@`%`**（初始化 SQL 中无 `CREATE USER`，账户由 mysql 镜像 entrypoint 依 `MYSQL_USER`/`MYSQL_PASSWORD` 在**数据目录首次初始化时**创建）。

### 2.2 连接参数一览

| 使用方 | 地址 | 库 | 账户 | 密码来源（.env.local 行号） |
|---|---|---|---|---|
| MySQL root（运维用） | 容器内 / 宿主 127.0.0.1:3306 | 全部 | `root` | `MYSQL_ROOT_PASSWORD`（第 11 行，`lhg2***288`） |
| print-service | `jdbc:mysql://127.0.0.1:3306/print_shop`（IDE 直跑时）；容器内为 `mysql:3306` | print_shop | `print_shop` | `PRINTSHOP_DATASOURCE_PASSWORD`（第 18 行，`oCxU****CJBI`） |
| schedule-service | `jdbc:mysql://127.0.0.1:3306/schedule_db` | schedule_db | `print_shop` | `SCHEDULE_DATASOURCE_PASSWORD`（第 96 行，`lhg2***288`） |
| chat-service | `mysql+pymysql://…@mysql:3306/chatbot_db`（容器网络） | chatbot_db | `print_shop` | `CHAT_DATABASE_URL` 内嵌（第 84 行，`oCxU****CJBI`） |
| MySQL 容器初始化 | — | — | `MYSQL_USER=print_shop`（第 13 行） | `MYSQL_PASSWORD`（第 14 行，`lhg2***288`，仅首次建目录时生效） |

### 2.3 ⚠️ 调查中发现的配置冲突（建议尽快处理）

同一个 MySQL 账户 `print_shop`@`%` 在 `.env.local` 中出现了**两个不同的密码**：

- 第 18 行 `PRINTSHOP_DATASOURCE_PASSWORD` = `oCxU****CJBI`（print-service、chat-service 在用）
- 第 14 / 96 行 `MYSQL_PASSWORD`、`SCHEDULE_DATASOURCE_PASSWORD` = `lhg2***288`（schedule-service 在用）

MySQL 单账户只有一个密码，**两者至多一个真实生效**（取决于 `mysql/data` 目录首次初始化时使用的值；数据目录已存在时，改 `.env.local` 的 `MYSQL_PASSWORD` 不会重新生效——这正是 AGENTS.md 5.4 节警告过的坑）。当前 Docker 未运行，无法代替你实测，请用以下命令验证：

```bash
docker ps                                                    # 找到 mysql 容器名
docker exec -it <mysql容器名> mysql -uprint_shop -p'<密码A>' -e "SELECT 1;"   # 两个密码各试一次
docker exec -it <mysql容器名> mysql -uroot -p -e "SELECT user,host FROM mysql.user;"
```

统一方式（二选一）：

```sql
-- 以实际生效的为准，把另一个改齐：
ALTER USER 'print_shop'@'%' IDENTIFIED BY '<统一后的密码>';
FLUSH PRIVILEGES;
```

然后同步 `.env.local` 的第 14、18、84、96 行四处引用。

### 2.4 本次 RBAC 改造涉及哪个库

**只涉及 `print_shop`**。四张新表 + 两个视图全部建在 `print_shop` 中（见第 5 节），`schedule_db`、`chatbot_db` 不动。

---

## 3. 改造前基线：现有权限模型盘点

### 3.1 数据层

`user_account`（`mysql/init/004_unified_user_account.sql`）：`account_type`（user/admin）+ `role`（user/superadmin/admin/operator）两个扁平字符串字段，一人一角色，角色值由代码白名单约束（`AdminServiceImpl.java:17` 的 `ALLOWED_ROLES`）。

### 3.2 后端鉴权链路

| 层 | 文件 | 行为 |
|---|---|---|
| Gateway | `gateway/.../security/GatewaySecurityFilter.java` | 只验 JWT 签名 + claims 结构（type/id/sub/jti/role 非空），**不做角色路由控制**，角色差异全部下沉到各服务 |
| print-service | `security/AuthInterceptor.java` | 解析 JWT 后**每次请求查库**比对 type/role/username（防"改角色后旧 token 仍可用"），再写入 ThreadLocal |
| print-service | `security/AuthContext.java` | `requireUser()` / `requireAdmin()` / `requireSuperAdmin()` 三档，Controller 内嵌调用约 30 处 |
| chat-service | `app/security.py` | `require_admin` 仅判 `type == admin`，知识库/设置等 10+ 接口的管理员门禁 |
| schedule-service | `security/JwtAuthFilter.java` | 与 print-service 同一套 JWT 规范，类型级检查 |

### 3.3 值得保留的优点（改造不推翻）

1. **JWT claim 规范完整**（type/id/sub/role/jti…），Gateway 与四个服务共享密钥做深度防御——权限集合可以直接挂在现有 claim 体系上扩展。
2. **`AuthInterceptor` 每请求查库**本是性能负担，但对 RBAC 是天然优势：**角色/权限变更即时生效**（若依把权限缓存在 token/Redis 中，改角色要等过期或踢人）。改造时把权限装载合并进这一次查询即可，不新增请求开销。
3. 统一账户（QQ 邮箱即账户名）让"账户"在管理端与用户端语义一致，RBAC 表挂 `account_id` 即可覆盖未来任何账户类型。

---

## 4. 目标 RBAC 模型设计

### 4.1 模型总览

```
┌──────────────────┐        ┌──────────────────┐
│   user_account   │        │     sys_role     │
│  (身份源,不动)    │        │ 角色/数据范围      │
│  username=QQ邮箱  │        │ role_key         │
│  role(降级为冗余) │        │ data_scope       │
└────────┬─────────┘        └────────┬─────────┘
         │ 1                       * │
         └────► sys_user_role ◄──────┘
                 (账户↔角色)

┌──────────────────┐        ┌──────────────────┐
│     sys_menu     │        │  sys_role_menu   │
│ 菜单/权限点       │        │  (角色↔权限点)    │
│ M目录/C菜单/F按钮 │        └────────┬─────────┘
│ perms 权限标识    │                 │ *
└────────┬─────────┘                 │
         │ * ────────────────────────┘
   查询视图：v_account_roles / v_account_perms
        │
        ▼
  AuthInterceptor 装载 → AuthPrincipal(perms集合)
        → AuthContext.requirePermission("print:order:update")
```

### 4.2 身份与授权分离原则

| 概念 | 载体 | 说明 |
|---|---|---|
| **身份**（你是谁） | `user_account`（不动） | `username` = 注册 QQ 邮箱，唯一；BCrypt 密码；AES-GCM 敏感字段 |
| **授权**（你能干什么） | `sys_user_role` + `sys_role` + `sys_role_menu` + `sys_menu` | 本次新增，全部可运营 |
| 冗余缓存 | `user_account.role` | 一期保留：JWT 颁发仍读它（claim 结构不变），应用层改角色时**同时写 `user_account.role` 和 `sys_user_role`**，保证两处一致；二期可考虑彻底废弃该字段 |

一期维持"一人一角色"语义（与现有 `user_account.role` 对齐），但表结构按多对多设计（若依同款），未来扩展多角色无需再动表。

### 4.3 新增表定义（4 表 + 2 视图）

| 表/视图 | 作用 | 关键字段 |
|---|---|---|
| `sys_role` | 角色定义 | `role_key`（唯一标识）、`role_name`、`data_scope`（ALL/STORE/SELF）、`builtin`（内置角色禁删）、`status` |
| `sys_menu` | 菜单与权限点 | `parent_id`（树形，M/C/F 三层）、`menu_type`（M目录/C菜单/F按钮）、`perms`（权限标识）、`path/component`（前端动态路由用） |
| `sys_user_role` | 账户↔角色 | 联合主键 `(account_id, role_id)`，`account_id` 即 `user_account.id` |
| `sys_role_menu` | 角色↔权限点 | 联合主键 `(role_id, menu_id)` |
| `v_account_roles` 视图 | 账户 → 角色标识集合 | `AuthInterceptor` 装载角色用 |
| `v_account_perms` 视图 | 账户 → 权限标识集合 | `requirePermission` 校验用 |

与若依的差异（裁剪项）：不做部门表 `sys_dept`（平台无组织架构，数据权限改用门店维度，见 4.6）；不做 `sys_role_dept`；不做多租户。

### 4.4 权限标识规范

格式：`模块:资源:动作`，全小写模块前缀，动作与后端接口一一对应。

| 模块前缀 | 覆盖服务 | 示例 |
|---|---|---|
| `print:` | print-service | `print:order:update`、`print:admin:add` |
| `chat:` | chat-service | `chat:knowledge:manage` |

一期共 **25 个权限点**（30 条菜单种子中，5 条为纯工具页面/个人中心不设权限点，任何管理端角色可见），完整清单见 SQL 第 6 节种子，摘要：

- 订单：`print:order:list / query / update / cancel / delete`（对应 `OrderInfoController` 的查询、状态推进、取消、删除）
- 服务：`print:service:list / add / update / delete`
- 门店：`print:store:list / add / update / delete`
- 用户：`print:user:list / update / delete`
- 管理员：`print:admin:list / add / update / delete / resetPwd`
- 知识库：`chat:knowledge:manage / import / delete`
- 工作台：`print:dashboard:view`

### 4.5 内置角色与默认授权矩阵

| 权限组 | superadmin（超级管理员） | admin（管理员） | operator（操作员） |
|---|---|---|---|
| 工作台 | ✅ | ✅ | ✅ |
| 订单管理 | ✅ | ✅ | ✅ 仅查看 + 状态推进（21/22） |
| 服务/门店/用户管理 | ✅ | ✅ | ❌ |
| 管理员账号管理 | ✅ 含新增/编辑/删除/重置密码 | ✅ 仅查看列表（对齐现状：`AdminController` 查询是 `requireAdmin`，写操作是 `requireSuperAdmin`） | ❌ |
| 客服知识库 | ✅ | ✅ | ❌ |
| 工具页（证件照/课表/OCR/客服） | ✅ | ✅ | ✅ |
| 数据范围 | ALL 全部 | ALL 全部 | **STORE 本门店** |

> superadmin 双保险：种子数据全量授权之外，代码层做 `role_key=superadmin` 直通（若依同款 `ALL_PERMISSION` 惯例），即使未来新增权限点忘记勾选也不会锁死超管。
> 矩阵是**默认种子**，上线后可随时在角色管理界面调整，无需改代码。

### 4.6 数据权限设计（门店维度，替代若依的部门维度）

若依的五档数据权限（全部/自定义/本部门/本部门及以下/仅本人）依赖 `sys_dept` 部门树；本平台没有组织架构，但有天然的隔离维度——**门店**（`order_info.store_id` 字段已存在，`000_printshop_schema.sql:60`）。因此：

| data_scope | 语义 | SQL 效果（订单列表为例） |
|---|---|---|
| `ALL` | 全部数据 | 不加过滤 |
| `STORE` | 本门店数据 | `AND o.store_id IN (当前账户关联的门店)` |
| `SELF` | 仅本人 | `AND o.user_id = 当前账户ID` |

一期只落地 `STORE` 在**订单列表/查询**上的过滤；"操作员↔门店"的绑定关系二期加 `sys_user_store` 关联表（或在 `user_account` 加 `store_id` 字段，二选一，届时定夺）。`SELF` 一期不启用（普通用户走用户端，不进管理端），字段先占位。

### 4.7 各服务改造边界

| 服务 | 改造内容 | 工作量 |
|---|---|---|
| print-service | **主战场**：AuthInterceptor 装载权限集合 + `requirePermission` + 替换约 30 处检查点 | 大 |
| chat-service | 知识库/设置 10+ 个 `require_admin` 接口换成 `require_permission("chat:knowledge:*")`（Python 侧，从 JWT claim 或查库取权限集合） | 中 |
| photo-service | 无鉴权（依赖 Gateway + 无状态），**不动** | 无 |
| schedule-service | 课表数据本身就按账户隔离（SELF 语义天然成立），**一期不动** | 无 |
| Gateway | 可选增强：按路径前缀做粗粒度权限拦截（如 `/api/print/admin/**` 要求 `print:admin:*`）。一期不加，避免两处维护 | 可选 |

---

## 5. SQL（可直接执行）

**正式文件：`打印/backend/mysql/init/007_rbac_schema.sql`**（与下文完全一致，以文件为准）。

### 5.1 执行方式

```bash
# 方式一：Docker 容器内执行（推荐，本地开发）
docker ps                                                  # 找到 mysql 容器名
docker exec -i <mysql容器名> mysql -uroot -p < 007_rbac_schema.sql

# 方式二：宿主机 mysql 客户端
mysql -h127.0.0.1 -P3306 -uroot -p < 打印/backend/mysql/init/007_rbac_schema.sql
```

注意：

1. 放入 `mysql/init/` 后，**仅新建数据目录（首次建库）时自动执行**；现有库必须手动执行一次。
2. 脚本**幂等**（`CREATE TABLE IF NOT EXISTS` + `INSERT IGNORE`），重复执行无副作用。
3. 不改动 `user_account` 任何字段与数据，执行前后现有登录/订单业务零影响（应用尚未接入新表前，新表只是"闲置"）。

### 5.2 完整 SQL

```sql
SET NAMES utf8mb4;
USE `print_shop`;

-- 1. 角色表 ----------------------------------------------------------
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

-- 2. 菜单权限表 ------------------------------------------------------
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

-- 3. 账户-角色关联表 -------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `account_id` INT NOT NULL COMMENT '账户ID(user_account.id)',
  `role_id` INT NOT NULL COMMENT '角色ID(sys_role.id)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  PRIMARY KEY (`account_id`, `role_id`),
  KEY `idx_sys_user_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户-角色关联表';

-- 4. 角色-菜单关联表 -------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `role_id` INT NOT NULL COMMENT '角色ID(sys_role.id)',
  `menu_id` INT NOT NULL COMMENT '菜单/权限ID(sys_menu.id)',
  PRIMARY KEY (`role_id`, `menu_id`),
  KEY `idx_sys_role_menu_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-菜单关联表';

-- 5. 内置角色种子 ----------------------------------------------------
INSERT IGNORE INTO `sys_role` (`id`, `role_key`, `role_name`, `role_sort`, `data_scope`, `builtin`, `remark`) VALUES
  (1, 'superadmin', '超级管理员', 1, 'ALL',   1, '内置：平台最高权限，代码级直通所有权限点'),
  (2, 'admin',      '管理员',     2, 'ALL',   1, '内置：全部业务管理，不含管理员账号管理'),
  (3, 'operator',   '操作员',     3, 'STORE', 1, '内置：门店订单处理，数据范围限本门店'),
  (4, 'user',       '普通用户',   4, 'SELF',  1, '内置：平台普通用户，不进入管理端');

-- 6. 菜单与权限点种子 ------------------------------------------------
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

-- 7. 角色-菜单默认授权种子 ------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
  SELECT 1, `id` FROM `sys_menu`;

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
  SELECT 2, `id` FROM `sys_menu` WHERE `id` NOT IN (61, 62, 63, 64);

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
  (3, 10), (3, 20), (3, 21), (3, 22),
  (3, 80), (3, 81), (3, 82), (3, 83), (3, 84);

-- 8. 存量账户角色回填：user_account.role -> sys_user_role -----------
INSERT IGNORE INTO `sys_user_role` (`account_id`, `role_id`)
  SELECT a.`id`, r.`id`
  FROM `user_account` a
  JOIN `sys_role` r ON r.`role_key` = a.`role`;

-- 9. 应用层查询视图 --------------------------------------------------
CREATE OR REPLACE VIEW `v_account_roles` AS
  SELECT ur.`account_id`, r.`role_key`
  FROM `sys_user_role` ur
  JOIN `sys_role` r ON r.`id` = ur.`role_id` AND r.`status` = 1;

CREATE OR REPLACE VIEW `v_account_perms` AS
  SELECT ur.`account_id`, m.`perms` AS `permission`
  FROM `sys_user_role` ur
  JOIN `sys_role` r       ON r.`id` = ur.`role_id` AND r.`status` = 1
  JOIN `sys_role_menu` rm ON rm.`role_id` = ur.`role_id`
  JOIN `sys_menu` m       ON m.`id` = rm.`menu_id` AND m.`status` = 1
  WHERE m.`perms` IS NOT NULL AND m.`perms` <> '';
```

### 5.3 执行后验证

```sql
SHOW TABLES LIKE 'sys_%';
SHOW FULL TABLES WHERE Table_type = 'VIEW';

-- 所有账户都完成角色回填（预期返回空集）
SELECT a.`role`, COUNT(*) FROM `user_account` a
  LEFT JOIN `sys_user_role` ur ON ur.`account_id` = a.`id`
  WHERE ur.`role_id` IS NULL GROUP BY a.`role`;

-- 抽查 superadmin 权限点数量（预期 25）
SELECT COUNT(*) FROM `v_account_perms` WHERE `account_id` =
  (SELECT `id` FROM `user_account` WHERE `role` = 'superadmin' LIMIT 1);
```

---

## 6. 管理员双登录入口调查结论

> 问题：`/api/admin/login` 与 `/api/auth/login` 为什么并存？现在实际在调用哪个？没用的能不能注释掉？

### 6.1 实际调用关系（代码级证据）

| 调用方 | 代码位置 | 实际请求 | 后端入口 |
|---|---|---|---|
| **管理端 web**（UnifiedLogin 管理员模式） | `printshop-web/src/admin/api/index.js:128` → `UnifiedLogin.vue:164` | `POST /api/print/admin/login` | `AdminController.login` |
| **用户端 web** | `printshop-web/src/client/api/index.js:8` | `POST /api/print/auth/login` | `AuthController.login` |
| **小程序** | `miniprogram-1/utils/api.js:154` | `POST /api/print/auth/login` | `AuthController.login` |

### 6.2 关键代码证据：两个接口各自锁死账户类型

- `AuthController.login`（`AuthController.java:103`）调用 `accountService.authenticate(email, password, "user")`，而 `AccountServiceImpl.authenticate`（`AccountServiceImpl.java:51-57`）对 `requiredType` 做硬校验——**管理员账户在这里会被直接拒绝**。
- `AdminController.login` 走 `adminService.login()`（`AdminServiceImpl.java:99`），只查 `account_type = admin` 的账户——**普通用户在这里也登不进来**。

### 6.3 结论：不可注释，缘由如下

**这两个接口不是"同一功能的两个重复入口"，而是两种账户类型各自唯一的登录入口**：

```
/api/auth/login   = 普通用户登录（client web + 小程序在用，管理员登不进）
/api/admin/login  = 管理员登录（管理端 web 在用，普通用户登不进）
```

注释掉任何一个，都会**当场打断对应前端的登录功能**（注释 `/api/admin/login` → 管理端无法登录；注释 `/api/auth/login` → 用户端和小程序无法登录）。所以**两个都保留，不做注释**。

真正冗余的不是接口，而是**两套并行的登录实现代码**（各自带验证码校验 + 登录失败锁定 `AdminLoginGuard` + BCrypt 比对 + HMAC 盲索引查询 + JWT 颁发，逻辑约 90% 重复）。收口动作依赖代码改造而非简单注释，路线见第 7 节 Phase 4：

1. `AuthController.login` 扩展为双类型登录（按账户查到什么类型发什么 token，登录响应按类型返回 user/admin 资料）；
2. 管理端 web 切换到 `/api/print/auth/login`；
3. 验证三个前端全部正常后，**此时才注释 `AdminController.login` 及其公开路径白名单**（`AuthInterceptor.java:59`、`GatewaySecurityFilter.java:147` 两处要同步删白名单）；
4. 顺手把 `AdminController` 其余裸 `Map` 响应收口为 `ApiResponse`（AGENTS.md 9.4 已记的技术债）。

---

## 7. 分阶段实施路线

### Phase 1：数据层就位（本方案已交付）

- 内容：执行 `007_rbac_schema.sql`（第 5 节）。
- 验收：4 表 + 2 视图建立、4 内置角色 + 30 条菜单种子 + 授权矩阵就位、存量账户全部回填（验证 SQL 通过）、现有业务零感知。

### Phase 2：print-service 权限内核

- `AccountMapper` 新增一次性查询（合并现有 `requireActive` 查库，**不增加请求数**）：
  ```sql
  SELECT a.*, GROUP_CONCAT(DISTINCT p.permission) AS perms
  FROM user_account a
  LEFT JOIN v_account_perms p ON p.account_id = a.id
  WHERE a.id = #{id} GROUP BY a.id;
  ```
- `AuthPrincipal` 增加 `Set<String> perms`；`AuthContext` 增加：
  ```java
  public static AuthPrincipal requirePermission(String permission) {
      AuthPrincipal p = requireAdmin();
      if (p.isSuperAdmin()) return p;              // superadmin 代码级直通
      if (!p.getPerms().contains(permission)) throw ApiException.forbidden(...);
      return p;
  }
  ```
- 按第 4.4 节清单把约 30 处 `requireAdmin()/requireSuperAdmin()` 替换为对应权限点（如 `AdminController.register` → `requirePermission("print:admin:add")`）。
- 管理员管理页改角色时，同一事务内**双写** `sys_user_role` + `user_account.role`。
- 可选：权限集合 Redis 缓存（TTL 5min，改角色时主动失效）——本地规模下可先不做。
- 验收：operator 登录后调 `/api/print/admin/register` 返回 403；admin 正常；全链路回归订单/文件/门店流程。

### Phase 3：前端动态化（printshop-web）

- 新增 `GET /api/auth/permissions`（管理端 JWT）：返回 `{ roles, perms, menus }`（menus 来自 `sys_menu` 中当前角色可见的 C/F 节点）。
- Pinia 增加 `permission` store；`router/index.js` 管理端路由改 `router.addRoute()` 动态注册（静态表保留兜底重定向）；`Layout.vue` 菜单按 `menus` 渲染。
- 自定义 `v-permission` 指令：无权限按钮移除 DOM；`AdminList.vue:48-53` 的 localStorage 硬判断全部替换。
- 验收：operator 登录看不到 服务/门店/用户/管理员/知识库 菜单；直接输 URL 访问被重定向；按钮不渲染。

### Phase 4：登录入口收口（见第 6.3 节路线）

- 验收：三个前端登录全部正常；`/api/admin/login` 已注释且 Gateway/Nginx 层面 404；`AdminController` 裸 `Map` 响应清零。

### Phase 5：数据权限落地

- `requirePermission` 返回的 principal 携带 `dataScope` 与门店绑定；订单列表/详情查询按 4.6 节拼 store 过滤（MyBatis XML 手写 where 片段）。
- 增加"操作员↔门店"绑定（`sys_user_store` 或 `user_account.store_id`，实施时定夺）。
- 验收：operator 只能看到本门店订单；admin/superadmin 全量。

---

## 8. 风险与回滚

| 风险 | 缓解 |
|---|---|
| 执行 SQL 影响现有业务 | 脚本不动 `user_account`，新表独立；回滚 = `DROP` 四表两视图即可，无需回滚业务代码 |
| JWT 兼容 | claim 结构不变（role 保留），Gateway/schedule/chat 无需同步升级，可分批发版 |
| 每请求查库性能回退 | 权限装载合并进既有的一次查询；如仍有压力再上 Redis 缓存 |
| chat-service 改造节奏 | 知识库权限点放 Phase 2 收尾或 Phase 3 之后独立小批量做，不影响 print-service 主线 |
| `.env.local` 双密码冲突 | 与本改造无关，建议**立即**按 2.3 节验证并统一，避免下次重建容器后 schedule-service 起不来 |
| 漏替换某处 `requireAdmin` | superadmin 直通保底；排查手段：全局 grep `requireAdmin\|requireSuperAdmin` 清零即为替换完成 |

---

## 9. 附录：本次调查涉及的代码位置索引

| 位置 | 结论 |
|---|---|
| `backend/printshop/.../security/AuthContext.java` | 三档硬编码检查（改造对象） |
| `backend/printshop/.../security/AuthInterceptor.java:37-42` | 每请求查库比对（权限装载挂载点） |
| `backend/printshop/.../controller/AdminController.java:93-124` | `/api/admin/login` 实现（管理员唯一登录入口） |
| `backend/printshop/.../controller/AuthController.java:93-110,103` | `/api/auth/login` 实现，`authenticate(..., "user")` 锁死用户类型 |
| `backend/printshop/.../service/impl/AccountServiceImpl.java:51-57` | `requiredType` 硬校验证据 |
| `backend/printshop/.../service/impl/AdminServiceImpl.java:17,99` | `ALLOWED_ROLES` 白名单；admin 专用登录查询 |
| `backend/gateway/.../security/GatewaySecurityFilter.java:142-167` | Gateway 公开路径白名单（Phase 4 需同步删 `/api/admin/login`） |
| `printshop-web/src/router/index.js:28-45` | 静态管理端路由（Phase 3 改造对象） |
| `printshop-web/src/admin/api/index.js:128`、`src/client/api/index.js:8` | 双登录入口的实际调用方证据 |
| `printshop-web/src/admin/views/AdminList.vue:43-53` | localStorage 角色硬判断（Phase 3 替换对象） |
| `miniprogram-1/utils/api.js:154` | 小程序走 `/auth/login` 证据 |
| `backend/mysql/init/004_unified_user_account.sql` | 现有账户表结构（QQ 邮箱即 username） |
| `backend/mysql/init/000_printshop_schema.sql:60` | `order_info.store_id`（门店数据权限依据） |
| `backend/.env.local:11-18,84,96` | 数据库连接配置与双密码冲突位置 |
