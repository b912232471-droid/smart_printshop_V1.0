# AGENTS.md — 智慧在线打印平台 项目说明

> 本文件给协作者（含 AI 代理）提供项目现状、目录结构、构建/测试命令和开发规范。
> 最终事实以代码为准；当本文与代码冲突时，**以代码为准并回头修订本文**。
> 历史规划见 `智慧打印平台-开发计划书.md`，里程碑流水见 `当前工作完成清单.md`。

## 1. 项目身份

- 名称：融合型智慧打印平台（私有版本）
- 性质：微服务架构毕业论文级项目，含核心打印 + AI 证件照 + 教务课表 + AI 客服
- 根目录非 git 仓库；`.gitignore` 已就位，便于后续 `git init`
- 主域：`https://www.guangxun.ltd`（生产目标，尚未部署）

## 2. 技术栈（实际）

| 层 | 技术 | 版本 |
|---|---|---|
| Java | Spring Boot | 3.5.6 |
| Java | Spring Cloud / Spring Cloud Alibaba | 2025.0.0 / 2025.0.0.0 |
| Java | MyBatis Spring Boot Starter | 3.0.5 |
| Java | PDFBox / Spring Security Crypto | 3.0.5 / 跟随 Boot |
| Python | FastAPI / uvicorn / pydantic / Celery / FastEmbed | 0.115.0 / 0.30.0 / 2.8.0 / 5.4.0 / 0.7.3 |
| Python | ONNX Runtime / MediaPipe / OpenCV-Pillow | 1.18.0 / 0.10.14 / 4.10.0.84 + 10.4.0 |
| Web | Vue 3 / Vite / Ant Design Vue / Pinia / vue-router | 3.5.x / 7.x / 4.2.x / 3.x / 4.x |
| 小程序 | 微信原生 | — |
| 基础设施 | MySQL 8 / Redis / Qdrant / Nacos / ClamAV / Nginx / Docker Compose | 8.0 / 7.4 / 1.15.4 / 2.5.1 / — / 1.24+ / — |
| AI | StepFun（图片生成）/ TokenHub 聚合平台（客服 LLM 与润色） | step-image-edit-2 / hy3 |
| 构建 | Maven wrapper (`mvnw`/`mvnw.cmd`) / Node 18+ / Python 3.12 | — |

> ⚠️ 与计划书的差异：计划书 4.1 写「Element Plus」，**实际是 Ant Design Vue**；计划书 4.1 写「MyBatis Plus 3.5.7（课表服务）」，**实际 schedule-service 用纯 MyBatis**。计划书需要更新。

## 3. 目录结构

```
智慧在线打印平台源码/
├── AGENTS.md                       # 本文件
├── .gitignore
├── 智慧打印平台-开发计划书.md       # V1.33 规划文档（部分历史章节仍待继续收口）
├── 当前工作完成清单.md              # 流水式进展记录（部分内容已落后于实现）
└── 打印/
    ├── backend/
    │   ├── pom.xml                 # Maven 根聚合父 pom（printshop-backend，packaging=pom）
    │   ├── mvnw / mvnw.cmd / .mvn/ # Maven wrapper（backend 根统一提供，子模块共用）
    │   ├── .env.example             # 生产环境变量模板（占位值）
    │   ├── .env.local               # 本地开发 env（被 .gitignore 忽略）
    │   ├── .env.middleware.local    # 中间件本地覆盖 env
    │   ├── docker-compose.yml       # 基础编排：mysql/nacos/clamav + 4 服务 + gateway
    │   ├── docker-compose.dev.yml   # 本地调试：发布 8080/3306/8848/8091/8092/8093
    │   ├── docker-compose.nginx.yml # 生产边缘：Nginx 80/443 → gateway
    │   ├── printshop/               # Java print-service :8081（Maven 子模块）
    │   │   ├── pom.xml / Dockerfile / README.md / .dockerignore
    │   │   └── src/main/java/com/example/printshop/
    │   │       ├── controller/      # OrderInfoController, FileInfoController, AuthController, AdminController, UserController, StoreController, ServiceItemController
    │   │       ├── service/         # 接口 + impl/
    │   │       ├── mapper/          # MyBatis 接口（XML 在 resources/mapper/）
    │   │       ├── entity/          # Account / Admin / User / OrderInfo / FileInfo / Store / ServiceItem
    │   │       ├── security/        # JwtService, AuthInterceptor, AuthContext, AdminLoginGuard, FieldCryptoService, QqMailVerificationService, LoginCaptchaService, RateLimitInterceptor, SecurityAlertService, FileScanService, ImageContentModerationService, SecurityHeadersFilter, RequestIpResolver, QqEmailAddress, AuthPrincipal
    │   │       ├── common/          # ApiResponse, ApiException, GlobalExceptionHandler
    │   │       ├── config/          # WebConfig, SecurityBeansConfig, RestTemplateConfig
    │   │       └── bootstrap/       # AdminBootstrapRunner（首引导 superadmin）, EmailIdentityMigrationRunner
    │   ├── gateway/                 # Java Spring Cloud Gateway :8080（Maven 子模块）
    │   │   ├── pom.xml / Dockerfile / README.md / .dockerignore
    │   │   └── src/main/java/com/example/printshop/gateway/
    │   │       ├── security/        # GatewaySecurityFilter, GatewaySecurityProperties
    │   │       └── fallback/       # FallbackController（Resilience4J 降级）
    │   ├── schedule-service/        # Java 课表服务 :8092（Maven 子模块）
    │   │   ├── pom.xml / Dockerfile / README.md / .dockerignore
    │   │   └── src/main/java/com/example/scheduleservice/
    │   │       ├── controller/      # ScheduleController
    │   │       ├── service/         # ScheduleService + sync/（ScheduleSyncAdapter + HttpJsonScheduleSyncAdapter）
    │   │       ├── repository/      # JwAccountRepository, CourseScheduleRepository
    │   │       ├── security/        # JwtService, JwtAuthFilter, FieldCryptoService, AuthContext, AuthPrincipal, ScheduleSecurityProperties
    │   │       ├── dto/ model/ common/
    │   ├── photo-service/           # Python FastAPI :8091（多实例负载均衡）
    │   │   ├── README.md
    │   │   ├── app/main.py          # FastAPI 入口
    │   │   ├── app/api/v1/          # generate_id.py, change_bg.py, media_parser/parser.py
    │   │   ├── app/services/        # id_photo_service, matting_service, face_service, image_service, media_parser/
    │   │   ├── app/core/            # config, security, nacos, metrics
    │   │   ├── pretrained/modnet.onnx
    │   │   ├── requirements.txt / Dockerfile / start.sh / .dockerignore
    │   ├── chat-service/            # Python FastAPI :8093（站内 Agent + RAG）
    │   │   ├── README.md
    │   │   ├── app/main.py          # FastAPI 入口
    │   │   ├── app/{agent,agent_store,tools,store,security,retrieval,deepseek,metrics,nacos,importer,schemas,config}.py
    │   │   ├── app/{markdown_knowledge,ingestion,vector_store,celery_app,tasks}.py
    │   │   └── requirements.txt / Dockerfile / start.sh / .dockerignore
    │   ├── mysql/init/              # 000-007 SQL（含 user_account 统一账户表、RBAC 权限表）
    │   ├── nginx/templates/         # 生产 Nginx 模板 default.conf.template
    │   ├── scripts/                  # 运维 Python 脚本（见第 6 节）
    │   ├── docs/                     # 4 篇运维文档
    │   ├── pressure-results/         # 11 份压测/联调报告
    │   └── data/files/               # 上传文件持久化卷
    ├── printshop-web/               # Vue3 统一 Web（admin + client）
    │   ├── package.json / vite.config.js
    │   └── src/
    │       ├── views/UnifiedLogin.vue
    │       ├── admin/{views,api,stores,utils,lang,assets}/
    │       └── client/{views,api,stores,utils}/
    ├── miniprogram-1/                # 微信小程序
    │   ├── app.js / app.json / app.wxss
    │   ├── pages/                   # index, login, booking, location, service-list, profile, orders, order-detail, schedule, photo, chat, logs
    │   ├── utils/                   # api.js, config.js, formatter.js
    │   ├── components/ custom-tab-bar/ i18n/ images/ docs/
    │   └── project.config.json
    ├── skills/ui-refactor-skill.md   # UI 重构规范 prompt
    └── .claude/settings.local.json   # Claude Code 权限白名单
```

## 4. 服务端口与边界

| 服务 | 端口 | 公网暴露 | 说明 |
|---|---|---|---|
| Nginx | 80/443 | 是 | 生产唯一公网入口，SSL 终止 + 静态托管 + 反代 `/api/` |
| Gateway | 8080 | 否（仅 Docker 内网） | Spring Cloud Gateway，统一路由 + JWT + 限流 + 熔断 |
| print-service | 8081 | 否 | 智慧打印核心 |
| photo-service | 8091 | 否 | AI 证件照（生产 `--scale photo-service=3`） |
| schedule-service | 8092 | 否 | 课表 |
| chat-service | 8093 | 否 | AI 客服 |
| MySQL | 3306 | 否 | print_shop / schedule_db / chatbot_db 三库 |
| Redis | 6379 | 否 | 登录验证码哈希 + 限流计数 |
| Nacos | 8848 | 否 | 服务发现 |
| ClamAV | 3310 | 否 | 上传病毒扫描 |
| Qdrant | 6333 | 否 | AI 客服 Markdown 知识块向量索引 |

## 5. 构建与运行命令

### 5.1 Java（print-service / gateway / schedule-service）

> backend 根有聚合父 pom（`printshop-backend`，packaging=pom），三个 Java 服务是其 `<modules>` 子模块，统一继承 Java 17 / Spring Cloud / Spring Cloud Alibaba 版本与 BOM 管理；Maven wrapper 在 backend 根。
> 2026-09-05：应用侧测试类已全部移除（历史测试留存于 `打印/backend-tests-backup-20260905.zip`），pom 中 test 依赖同步删除，构建以 compile/package 为准。

```powershell
# 全 reactor 编译打包（printshop + gateway + schedule-service 一次构建）
cd 打印\backend
.\mvnw.cmd clean package -DskipTests

# 按模块构建
.\mvnw.cmd package -pl printshop
.\mvnw.cmd package -pl gateway -pl schedule-service

# 打包 Docker 所需 jar（避免每个服务都拉 Maven 镜像），一次 reactor 构建出 3 个 jar
cd 打印\backend
python scripts\package_java_services.py
```

> 历史问题：Maven Central 拉取 Spring Cloud Alibaba BOM 偶发 403。`package_java_services.py` 使用临时 Aliyun Maven settings 规避，结束清理。

### 5.2 Python（photo-service / chat-service）

```powershell
# 应用侧测试类已移除；语法级自检可用
cd 打印\backend\photo-service
python -m compileall app

cd 打印\backend\chat-service
python -m compileall app
```

### 5.3 Web（printshop-web）

```powershell
cd 打印\printshop-web
npm install
npm run dev      # http://127.0.0.1:3000/，/api 代理到 http://localhost:8080
npm run build    # 产物 dist/，由 Nginx 托管
```

构建前需通过 `VITE_TENCENT_MAP_KEY` 注入腾讯地图浏览器 Key（管理端门店选点用）。

### 5.4 本地微服务栈（Docker）

```powershell
cd 打印\backend
python scripts\generate_local_dev_env.py --force    # 生成 .env.local（同源 JWT/加密密钥、首个 superadmin 引导）
python scripts\package_java_services.py            # 打包 3 个 Java jar
docker compose --env-file .env.local -f docker-compose.yml -f docker-compose.dev.yml up -d --build
```

print-service 的宽松安全姿态（关闭 HTTPS 强制校验、接口限流、病毒扫描、安全告警）由 `application-dev.yml` profile 提供，dev 覆盖文件只负责注入 `SPRING_PROFILES_ACTIVE=dev`；生产 compose 默认激活 `prod` profile（HTTPS 强制、限流、ClamAV、告警全部开启，默认值对齐 `.env.example`）。课表默认启用 mock 同步；chat-service 的 LLM 变量已统一为 `CHAT_LLM_*`（旧 `DEEPSEEK_*` 仍兼容读取）。

> 注意：若 `mysql/data` 已存在，不要直接重生成 `.env.local` 复用旧数据目录——MySQL 数据目录中的密码仍是旧 env 初始化值。

### 5.5 生产部署

```powershell
cd 打印\backend
python scripts\generate_production_env.py --domain <domain> --wechat-app-id <appid> --wechat-app-secret <secret>
python scripts\production_readiness_check.py --env .env --backend-dir .
docker compose -f docker-compose.yml -f docker-compose.nginx.yml --env-file .env up -d --build
# 多实例证件照：
docker compose -f docker-compose.yml -f docker-compose.nginx.yml --env-file .env up -d --build --scale photo-service=3
python scripts\production_smoke_test.py --base-url https://<domain> --env .env
python scripts\legacy_print_compat_audit.py <gateway-or-nginx-log>   # 收紧旧兼容路由前审计
```

完整顺序见 `打印\backend\docs\production-deployment-runbook.md`。

## 6. 运维脚本（打印\backend\scripts\）

| 脚本 | 用途 |
|---|---|
| `generate_local_dev_env.py` | 生成本地 `.env.local`（同源 JWT、字段加密密钥、首个 superadmin） |
| `generate_production_env.py` | 生成生产 `.env.production.local` 草稿，自动生成 MySQL 密码、JWT、加密密钥、域名 CORS/Nginx 配置 |
| `package_java_services.py` | 用 Maven wrapper + 临时 Aliyun settings 打包 3 个 Java jar |
| `production_readiness_check.py` | 静态就绪检查（env 占位值、密钥长度一致性、HTTPS/CORS、首引导、证书、dist、模型、CHAT_LLM_API_KEY、课表适配器、旧兼容路由状态） |
| `production_smoke_test.py` | 生产 Nginx/Gateway 入口 smoke（公开接口、401、带 JWT 的 photo/chat/schedule、可选真实证件照推理和课表同步） |
| `legacy_print_compat_audit.py` | 审计 Gateway/Nginx 日志，判断是否可收紧 `GATEWAY_PRINT_COMPAT_PATHS` |
| `photo_pressure_test.py` | photo-service 健康检查 / 真实推理压测，支持多 `--base-url` 轮询 |
| `chat_pressure_test.py` | chat-service `/api/chat/ask` / 健康检查 / Gateway 转发压测 |

所有压测脚本支持 `--output <report.json>` 留存报告到 `pressure-results/`。

## 7. 开发规范

### 7.1 Java 包结构

- `com.example.printshop.*`（print-service）
- `com.example.printshop.gateway.*`（gateway）
- `com.example.scheduleservice.*`（schedule-service）

分层：`controller / service（+ impl）/ mapper / entity / security / common / config / bootstrap`。

### 7.2 配置与密钥

- **所有密钥、密码、第三方凭证一律通过环境变量注入**，无默认值。
- `PRINTSHOP_JWT_SECRET`、`PRINTSHOP_FIELD_ENCRYPTION_KEY`、`SCHEDULE_FIELD_ENCRYPTION_KEY`、`GATEWAY_JWT_SECRET` 至少 32 位随机字符；未配置或包含 `change-me/dev-only/replace-with` 时服务**拒绝启动**。
- 跨服务密钥一致：`GATEWAY_JWT_SECRET = PRINTSHOP_JWT_SECRET`；`SCHEDULE_JWT_SECRET / CHAT_JWT_SECRET` 也复用平台 JWT。
- print-service 配置为 YAML 三文件结构：`application.yml`（主配置，公共项 + 唯一 MySQL 数据源，默认激活 dev）+ `application-dev.yml`（日常开发，宽松安全姿态）+ `application-prod.yml`（生产部署，HTTPS/限流/ClamAV/告警全开，域名 `https://www.guangxun.ltd`）。所有值通过 `${ENV_VAR:default}` 占位；可选 `spring.config.import` 载入 `.env.local[.properties]`、`.env.middleware.local[.properties]`。gateway / schedule-service 仍是单 `application.yml`。

### 7.3 API 路径

- Gateway 对外统一：`/api/<service>/**`
  - `/api/print/**` → print-service `/api/**`（含 RewritePath）
  - `/api/photo/**` → photo-service `/api/photo/**`
  - `/api/schedule/**` → schedule-service `/api/schedule/**`
  - `/api/chat/**` → chat-service `/api/chat/**`
  - `/api/**` 兼容旧打印客户端路径（`GATEWAY_PRINT_COMPAT_PATHS` 可收紧）
- Web/小程序请求封装：`/print/<path>` → Gateway `/api/print/<path>`。

### 7.4 统一响应格式（计划目标）

```json
{ "code": 0, "message": "success", "data": {} }
```

- `code = 0` 表示成功，非 0 表示错误。
- 现状：`AuthController` 已用 `ApiResponse<Map>`；`AdminController.login` 用 `ApiResponse`，但 `register/update/delete/change-password` 用裸 `Map` 返回 `code:200/500/404/403`；`FileInfoController.uploadFile/uploadAvatar/uploadStoreImage` 用裸 `Map` 返回 `code:200`；`OrderInfoController` 全部返回裸 `Integer/List/OrderInfo`，无 `ApiResponse` 包装。**这是已知规范不统一处，新代码请用 `ApiResponse`，旧代码逐步收口。**

### 7.5 鉴权（HS256 JWT）

- JWT claim 完整集：`type / id / sub / role / username / iat / exp / jti`
  - `type` ∈ {`admin`, `user`}
  - `sub` 必须等于 `type:id`
  - `jti` 必须是 UUID
  - `iat` 不允许来自未来（60s 时钟偏移），`exp` ≥ `iat`
  - 用户 token 必须有非空 `role`，管理员 token 同样
  - 拒绝 `alg=none` 或声明缺失
- Gateway 在转发前校验 JWT；print-service/schedule-service/chat-service 也独立校验同一套密钥（深度防御）。
- 公开路径白名单（拦截器和 Gateway 一致）：
  - `OPTIONS` 预检
  - `POST /api/admin/login`
  - `GET /api/auth/captcha`
  - `POST /api/auth/login`、`/api/auth/register`、`/api/auth/email/send`、`/api/auth/password/reset`
  - `GET /api/service/**`、`/api/store/active`、`/api/public-files/**`
- 权限分级：`requireUser()` / `requireAdmin()` / `requireSuperAdmin()`（见 `AuthContext`）。
- RBAC 权限点（管理端专属）：`AuthContext.requirePermission("print:order:update")` 按权限标识校验；权限集合随 `AuthInterceptor` 既有的一次查库装载（`v_account_perms`），`role_key=superadmin` 代码级直通；管理端角色变更双写 `sys_user_role` + `user_account.role`。

### 7.6 安全要求

- 密码 BCrypt 哈希，强度约束：8-72 位、字母+数字+符号至少两类、不含空白。
- 敏感字段（手机号、邮箱、教务密码）AES-GCM 加密存储；查询走 HMAC 盲索引 `email_hash`。
- 登录失败锁定：默认 15 分钟内 5 次失败锁 15 分钟；触发锁定写 `SECURITY_ALERT` 日志，可选 webhook。
- 图形验证码 `LoginCaptchaService`（Redis 存答案、一次性删除、短 TTL）。
- 邮箱验证码 `QqMailVerificationService`：仅 `@qq.com`，Redis 存哈希、原子 Lua 校验删除、5min 有效、60s 冷却、单邮箱 20/天。
- 接口限流：print-service 默认 240/min/IP+路由组；Gateway 默认 600/min/IP+路由组；默认不信任 `X-Forwarded-For`。
- 文件上传多层防护：扩展名 + magic bytes + PDF 结构（页数、加密、对象数、内容流复杂度）+ 图片尺寸/像素 + OOXML ZIP 防炸弹/宏/ActiveX/OLE + ClamAV + 可选图片内容审核（fail-closed）。
- 订单金额后端重算（`service_price × page_count × copies`），不信任客户端 `totalPrice`；状态机：`0待处理→1打印中→2待取件→3已完成`，`4已取消`，禁止回退/越级/终态变更。
- AI 图片生成：每用户日配额（Redis 计数）+ 全平台日成本熔断（超限 503 + SECURITY_ALERT 告警）+ 提示词黑名单 + 生成图内容审核（fail-closed，复用 ImageContentModerationService）；审核拒绝落记录可查。
- 审计日志：管理员登录、订单状态变更、401/403/429 安全异常。
- 上传文件 UUID 文件名 + 路径归一化；下载强制 `/files/<单文件名>`；响应只返回 `/api/file/download/{fileId}` 鉴权地址，不暴露内部存储路径。

### 7.7 Python 服务规范

- FastAPI + lifespan，启动时注册 Nacos，退出时注销。
- 暴露 `GET /api/<svc>/health`、`GET /api/<svc>/metrics`（JSON）、`GET /metrics`（Prometheus 文本）。
- 监控指标：实例 ID、运行时长、请求总数、状态码分布、路径分布、请求耗时（chat 还含问答来源和知识库操作计数）。
- chat-service 知识库 CRUD/批量导入要求管理员 JWT，问答接口允许用户 JWT；未配置 `CHAT_LLM_API_KEY`（通用 OpenAI 兼容接入，当前 TokenHub 聚合平台混元 hy3）时降级为关键词检索兜底。
- chat-service 已升级为站内 Agent：管理员可持久化启停、配置模式/模型/工具白名单；Markdown 由 Celery Worker 分块并用本地 BGE 生成向量写入 Qdrant；FAQ 与 Markdown 做混合检索。
- 会话、消息、摘要、Agent 运行、工具调用和反馈均持久化。模型只能调用白名单内只读工具，用户 ID 由 JWT Principal 注入，JWT 和敏感字段不进入模型上下文。
- 生产保持 `CHAT_VECTOR_ENABLED=true`、`CHAT_VECTOR_REQUIRED=true`、`CHAT_INGESTION_INLINE=false`；Gateway 客服超时默认 90s。
- schedule-service 教务密码 AES-GCM 加密存储；同步适配器按优先级路由（`RoutingScheduleSyncAdapter`）：正方教务直连适配器（`ZfsoftScheduleSyncAdapter`，`SCHEDULE_SYNC_ZFSOFT_ENABLED` + `SCHEDULE_SYNC_ZFSOFT_BASE_URL`，默认 gnmkdm=N253508；登录走 `/xtgl/login_slogin.html` + RSA 公钥加密密码，课表走 `/kbcx/xskbcx_cxXsgrkb.html`，xnm 取学年起始年、xqm 映射 1/2/3→3/12/16）→ HTTP JSON 适配器（`SCHEDULE_SYNC_HTTP_ENDPOINT_URL`）→ mock。HTTP JSON 同步适配器解密后调用 `SCHEDULE_SYNC_HTTP_ENDPOINT_URL`；生产默认 mock 关闭、HTTP 未启用时同步返回 503，**不伪造成功**。

### 7.8 前端规范（printshop-web）

- 统一 `UnifiedLogin.vue`，`portal` 切换 `client`/`admin`，同端口同 Router。
- 别名：`@`、`@admin` → `src/admin`；`@client` → `src/client`；`@web` → `src`。
- Axios 请求自动携带 `Authorization: Bearer <token>`；401/403 清理本地登录态并跳转登录页。
- 文件下载走 axios blob，确保携带 JWT。
- `console.*` 已从生产构建中清理，避免在浏览器控制台暴露敏感信息。
- UI 重构遵循 `skills/ui-refactor-skill.md`：业务零修改、绑定不变、仅改样式与动画、玻璃质感 + 紫色渐变 + 柔光阴影、圆角 12-20px、入场淡入上滑、按钮按压缩放 0.95。

### 7.9 小程序规范

- API 基址：本地 `http://localhost:8080/api`，生产 `https://www.guangxun.ltd/api`（见 `utils/config.js`）。
- 请求封装自动携带用户 JWT；公开接口可匿名；token 过期前置校验，避免过期 token 继续传输文件。
- 历史路径归一化：`/api/public-files/**` → `/api/print/public-files/**`；`/api/file/download/**` → `/api/print/file/download/**`。
- 不再保存、读取或提交 openid；登录态只保留本地用户 ID、公开用户资料和平台 JWT。
- 已清理 `console.*` 调试输出。

### 7.10 注释与代码风格

- **不主动加注释**，除非用户明确要求；UI/动画关键块可加简短中文注释。
- 不写破坏性命令；不动 API 路径；保持 ASCII；不使用 emoji（除非用户要求）。
- Java 命名：大驼峰类名，小驼峰方法/变量；包名全小写。
- 配置占位符 `${ENV:default}` 风格统一。

## 8. 数据库

### 8.1 三库划分

| 库 | 服务 | 来源 |
|---|---|---|
| `print_shop` | print-service | 原有 + 安全迁移 + 统一账户 |
| `schedule_db` | schedule-service | `请了吗` 项目 `attendance_system.sql` |
| `chatbot_db` | chat-service | FAQ、Markdown 文档/知识块、策略、会话、消息、Agent 审计、反馈 |

`photo-service` 无状态，不占库。

### 8.2 print_shop 核心表

| 表 | 说明 |
|---|---|
| `user_account` | **统一账户表**（替代旧 `user` + `admin`）：id, username(QQ邮箱), password_hash(BCrypt), account_type(user/admin), role(user/superadmin/admin/operator), status, display_name, real_name, phone(密文), email(密文), email_hash(HMAC盲索引), avatar_url, created_at, last_login_at |
| `file_info` | 文件信息 |
| `order_info` | 订单：含结构化打印参数 `copies / page_count / duplex / color_mode / paper_size` 和模拟 `total_price` |
| `store` | 门店 |
| `service_item` | 服务项目 |
| `image_gen_record` | AI 图片生成记录（token 下载、成本快照、订单回填溯源） |
| `image_gen_settings` / `image_gen_setting_audits` | 图片生成运营配置与变更审计（镜像 chat_settings 模式） |
| `image_gen_template` | AI 生成校园模板（手抄报/海报/通知，占位符 prompt），管理端可 CRUD 调配 |
| `sys_role` | RBAC 角色表：role_key/data_scope(ALL/STORE/SELF)/builtin/status |
| `sys_menu` | RBAC 菜单权限表：M/C/F 三层、perms 权限标识、path/component |
| `sys_user_role` / `sys_role_menu` | 账户↔角色、角色↔权限点关联 |
| `account_quota` | 账户级功能额度覆盖（imagegen/OCR 每日额度，NULL 跟随全局、0 禁用） |
| `v_account_roles` / `v_account_perms`（视图） | 账户→角色标识 / 账户→权限标识集合 |
| `jw_accounts`（schedule_db） | 教务账号：`jw_password` AES-GCM 密文 |
| `course_schedules`（schedule_db） | 课表 |
| `knowledge_categories / knowledge_items / chat_logs`（chatbot_db） | 客服知识库 + 对话日志 |
| `chat_settings / chat_setting_audits`（chatbot_db） | AI 客服启停、模式、模型、工具白名单和配置审计 |
| `knowledge_documents / knowledge_chunks / knowledge_ingestion_jobs`（chatbot_db） | Markdown 版本、知识块和摄取任务 |
| `chat_conversations / chat_messages / agent_runs / agent_tool_calls / chat_feedback`（chatbot_db） | 多轮会话、Agent 运行与反馈 |

### 8.3 初始化脚本顺序

```
mysql/init/
├── 000_printshop_schema.sql     # print_shop 基础表
├── 001_security_migration.sql   # BCrypt 密码长度、敏感字段密文长度、索引
├── 002_schedule_schema.sql      # schedule_db
├── 003_chat_schema.sql          # chatbot_db（含 print_shop 业务用户授权）
├── 004_unified_user_account.sql # 创建 user_account，迁移旧 user/admin 数据，DROP 旧表
├── 005_qq_email_identity.sql   # 补齐 email_hash 字段和唯一索引
├── 006_drop_jw_username.sql    # schedule_db 移除 jw_accounts.jw_username 列
├── 007_rbac_schema.sql         # RBAC 四表两视图 + 内置角色/菜单/授权种子 + 存量账户角色回填
├── 008_imagegen_schema.sql     # AI 图片生成：记录/运营配置+审计/校园模板 + RBAC 菜单 85-87
├── 009_ocr_schema.sql          # OCR 运营管理：ocr_settings/audits/record + RBAC 菜单 88-89
└── 010_account_admin_schema.sql # 管理端账户运营：account_quota 账户级额度表 + 权限点菜单 90-95
```

> 004 会 DROP 旧 `user` 和 `admin` 表。Java 侧 `UserMapper` / `AdminMapper` / `AccountMapper` 都映射到 `user_account`，按 `account_type` 区分。

### 8.4 Bootstrap

- `AdminBootstrapRunner`：仅当 `PRINTSHOP_BOOTSTRAP_ADMIN_ENABLED=true` 且 `user_account` 中没有带 QQ 邮箱的管理员时，创建一个 `superadmin`；首次登录后必须关闭开关并清空引导凭证。
- `EmailIdentityMigrationRunner`：启动时为缺失 `email_hash` 的存量账号补齐盲索引（密文可解出 QQ 邮箱时）。

## 9. 当前进展

### 9.1 已完成

- **RBAC 权限体系（Phase 1-3 已落地，2026-09-06）**：print-service 权限内核（AuthInterceptor 装载 perms + `requirePermission` 替换三档硬编码，管理员/订单/门店/服务/用户共 22 处检查点权限点化）、`GET /api/auth/permissions` 下发 roles/perms/menus、管理员注册/更新/删除与用户注册双写 RBAC、chat-service 17 个管理端接口换成 `chat:knowledge:*` 权限点（跨库查 `print_shop.v_account_perms`，库不可用时 fail-closed 503）、前端 permission store + 路由守卫按 `meta.permission` 拦截 + Layout 菜单按权限渲染 + `v-permission` 按钮级指令 + AdminList localStorage 硬判断收口。方案见 `doc/RBAC权限体系改造方案.md`。

- **管理端账户/角色/额度运营（2026-09-06 补齐）**：print-service 新增 `account` 包（`/api/account/*` 账户管理 + `/api/role/*` 角色管理）。账户管理：全账户分页检索（关键词/类型/角色/状态）、角色分配（事务双写 `user_account.role` + `sys_user_role`，禁改自身角色）、启禁用（禁自禁、superadmin 目标级防护）、重置密码（`print:user:resetPwd`）、账户级额度（`account_quota` 表覆盖 imagegen/OCR 每日额度，NULL 跟随全局、0 禁用，`AccountQuotaService` 已接入两条配额检查链路）。角色管理：角色 CRUD + 菜单授权树（`sys_role_menu` 整体替换，内置角色可改授权不可删、superadmin 内置不可停用，仍有账户的角色禁删）。前端：`/users` 改造为账户管理页（搜索/角色/状态/改密/额度弹窗）、新增 `/roles` 角色管理页（授权树半选父节点一并提交）、Layout 系统管理组新增角色管理并补上此前遗漏的 `/imagegen` 菜单项。顺带修复 RBAC 审查报告 F-03（管理员更新空 role 清空角色锁死）、F-11（角色同步静默半失败改抛异常回滚）、F-13（注册/更新 superadmin 目标级防护）、F-10（删除用户事务内清理 sys_user_role）、F-06（前端 403 分流为提示+权限刷新，不再登出）。权限点增至 35 个（菜单 90-95），admin 内置角色增授 90/91。

- **百度 OCR 图片转文档**：print-service 提供 `/api/ocr/convert`、受保护下载、`/api/ocr/status` 与运营管理接口（`/api/ocr/config`、`/records`、`/usage`，2026-09-06 补齐）；用户端 `/client/ocr`；管理端 `/ocr` 含转换工具 + 运营管理 tab（功能开关、每用户每日配额、使用统计与记录查询，权限点 `print:ocr:manage/query`，菜单 88-89，转换落 `ocr_record`）。当前仅支持单张 JPG/PNG/BMP，使用 `BAIDU_OCR_API_KEY` / `BAIDU_OCR_SECRET_KEY`，未配置密钥或功能停用时接口返回 503。详见 `打印\backend\docs\baidu-ocr-image-to-document.md`。

- **AI 图片生成（图文闭环，2026-09-06）**：photo-service 新增 `/api/photo/image-generate` 模型适配与 `/api/photo/image-models` 目录接口（供应商 2026-09-06 由 TokenHub 切换为阶跃星辰 StepFun `step-image-edit-2`，0.02 元/张；StepFun size 参数为 高x宽，适配层按目录 `size_axis=HxW` 自动换轴，平台侧仍为 宽x高；`IMAGE_API_KEY` 未配返回 503）；print-service 新增 `imagegen` 包编排（配额 Redis 日计数、全平台日成本熔断、生成图审核 fail-closed、`data/files/imagegen` 落盘、`image_gen_record` 记录、提示词 LLM 润色（PromptPolishService，回落共用客服 CHAT_LLM_*）、校园模板、`/api/imagegen/*` 共 11 个接口，上游额度/计费类错误透传可操作提示）；用户端 `/client/imagegen` 模板填空生成 + 一键转打印订单（Booking 预填 + 订单回填）；管理端 `/imagegen` 运营页（配置+**模板管理 CRUD**（2026-09-06 补齐，含启停/新增/编辑/删除）+记录+成本台账，RBAC `photo:imagegen:manage/query`，菜单 85-87）。方案见 `打印\backend\docs\图文生成功能更新迭代方案-2026-09-06.md`，数据库见 `008_imagegen_schema.sql`。
- **微服务架构**：Gateway（路由 + Resilience4J 熔断 + JWT + 限流 + 请求日志 + SecureHeaders）、print-service（订单/文件/门店/服务/管理员完整业务）、photo-service（多实例换底色压测通过）、schedule-service（HTTP JSON 适配器骨架）、chat-service（可运营站内 Agent + Markdown RAG + 受控只读工具；含系统提示词版本/人工转接语、正式 `search_knowledge` 工具、文档替换、检索测试、摄取进度和 SSE `delta`）。
- **统一账户体系**：QQ 邮箱 + 密码 + 图形验证码 + 邮箱验证码登录注册/找回/绑定；BCrypt + AES-GCM + HMAC 盲索引；管理员也走 QQ 邮箱。
- **安全加固**：见 7.6。print-service 曾有 135+ 单元测试全部通过（2026-09-05 应要求移除全部应用侧测试类，历史代码留存于 `打印/backend-tests-backup-20260905.zip`）。
- **运维链路**：env 生成 → Java 打包 → Docker compose → 就绪检查 → smoke → 压测 → 旧路由审计 → Runbook。
- **统一 Web**：printshop-web admin+client 共用 UnifiedLogin，Ant Design Vue 4.2。
- **小程序**：登录改为 `/api/print/auth/login`，已弃 wx.login；新增 schedule/photo/chat 页面。
- **Nginx 生产模板**：HTTP→HTTPS 跳转、HSTS、反代 `/api/` 到 Gateway、托管 `/printshop/` 静态资源。

### 9.2 重大架构变更（与历史文档不同步）

> ⚠️ 开发计划书已更新到 V1.33；当前工作完成清单已追加 Agent 里程碑，但早期流水内容仍保留历史架构描述。

1. **身份认证**：从「微信小程序 `wx.login` 换 openid」迁移到「QQ 邮箱 + 密码 + 验证码统一账户」。
   - 新增 `user_account` 表（004/005 SQL），DROP 旧 `user` 和 `admin` 表。
   - 新增 `Account` 实体 + `AccountService` + `AccountMapper` + `AuthController`（`/api/auth/*`）。
   - printshop 代码中**已无 `wx/login`、`openid`、`AppId`、`Wechat` 引用**。
   - 小程序 `utils/api.js` 已改调 `/auth/login` 和 `/auth/register`；`wx.removeStorageSync('openid')` 仅作旧数据清理残留。
   - 管理员登录有两个并存入口：`POST /api/admin/login`（`AdminController`，admin 前端用）和 `POST /api/auth/login`（`AuthController`，统一 web 用）—— 两者都走 BCrypt + 邮箱盲索引；后续可考虑收口。

2. **管理端 + 用户端合并为 printshop-web**：同端口、同 Router、同 UnifiedLogin；用户端入口 `/client`，管理端 `/dashboard`。计划书仍把「管理后台」和「小程序」作为两套独立前端描述。

3. **UI 库**：实际 Ant Design Vue 4.2，计划书 4.1 写 Element Plus。

4. **响应格式不统一**：见 7.4，待收口。

### 9.3 待完成（生产前阻塞项）

- 真实生产服务器 + 域名 + Nginx HTTPS 证书部署
- 正式生产 `CHAT_LLM_API_KEY` 和 Qdrant/Celery 容器链路联调（本地 API Key、BGE 向量与 Agent 功能补丁已单独验证；仍缺 Docker 全链路运行级联调）
- 真实教务系统协议适配：`jw.unn.edu.cn`（正方教务）直连适配器已完成并用测试账号联调通过（`ZfsoftLiveSyncIT`，需 `ZFSOFT_LIVE_TEST=true` 门控运行）；其他学校教务或教务变更时仍需适配
- 真实人脸样本生成证件照链路复测（本地只压了换底色）
- 旧 `/api/**` 兼容路由收紧（先跑 `legacy_print_compat_audit.py` 审计日志）
- 小程序正式发布（已弃 wx.login，发布流程需重新走 QQ 邮箱注册）
- AI 图片生成供应商已切换为 StepFun `step-image-edit-2`（2026-09-06 真实 Key 联调通过，size=高x宽、仅 5 个固定尺寸、b64_json 返回、无 usage 字段；模板种子与线上库已同步为 `896x1184`）；⚠️ 该模型官方公告 **2026-10-10 下线**，届时更换模型只需改 photo-service `IMAGE_MODEL_CATALOG` + 模板推荐项 + `.env` 的 `IMAGE_API_BASE_URL/IMAGE_API_KEY`；P1 扩展：图生图（`/v1/images/edits`，step-image-edit-2 原生支持）、客服 Agent 生成图片工具
- RBAC Phase 4：登录入口收口（`/api/auth/login` 扩展双类型登录 → 管理端切 `/api/print/auth/login` → 三端联调通过后注释 `/api/admin/login` 并删 Gateway/AuthInterceptor 白名单，见方案第 6.3/7 节）
- RBAC Phase 5：数据权限落地（operator↔门店绑定 + 订单列表/查询按 `sys_role.data_scope=STORE` 过滤）
- RBAC 注意：若未来权限点大幅增长（当前 35 个，GROUP_CONCAT 默认上限 1024 字节，约 45 个时静默截断），需调大 `group_concat_max_len`
- 同步更新 `智慧打印平台-开发计划书.md` 到 V1.33+ 和 `当前工作完成清单.md`

### 9.4 已知技术债

- `AdminController.register/update/delete/change-password` 仍返回裸 `Map`，未走 `ApiResponse`。
- `FileInfoController.uploadFile/uploadAvatar/uploadStoreImage` 仍返回裸 `Map`，`code:200` 与 `ApiResponse.code=0` 语义不一致。
- `OrderInfoController` 所有方法返回裸类型，无 `ApiResponse` 包装。
- 管理员登录两套入口并存（`/api/admin/login` 与 `/api/auth/login`），建议收口到一套。
- `AuthInterceptor` 仍把 `/api/admin/login` 当公开路径，`/api/auth/login` 也是；要确认是否冗余。
- RBAC 后保留 `requireAdmin` 的接口（属预期，无对应权限点）：文件上传/删除/门店图片（FileInfoController）、`/api/ocr/status`（OcrController）、管理员自助改密（AdminController.changePassword）。
- `当前工作完成清单.md` 篇幅已超 440 行，建议按里程碑分档归档，主文档只保留最新一轮。

## 10. 关键文档索引

| 文档 | 路径 |
|---|---|
| 开发计划书（V1.32，部分落后） | `智慧打印平台-开发计划书.md` |
| 流水式完成清单（部分落后） | `当前工作完成清单.md` |
| 本说明 | `AGENTS.md` |
| 本地开发 Runbook | `打印\backend\docs\local-development-runbook.md` |
| 生产部署 Runbook | `打印\backend\docs\production-deployment-runbook.md` |
| 生产安全清单 | `打印\backend\docs\production-security-checklist.md` |
| QQ 邮箱验证码配置 | `打印\backend\docs\qq-mail-verification.md` |
| 站内客服 Agent 运维 | `打印\backend\docs\chat-agent-operations.md` |
| UI 重构规范 | `打印\skills\ui-refactor-skill.md` |
| 生产 env 模板 | `打印\backend\.env.example` |

## 11. 协作约定

- 改代码前先读邻近文件，确认框架/库/命名约定；不要假设某库可用。
- Java 改动跑 `mvnw.cmd clean package -DskipTests`（测试类已移除）；Python 改动可用 `python -m compileall app` 自检；Web 改动跑 `npm run build`。
- 不提交 `.env`、`.env.local`、`*.local`、`target/`、`node_modules/`、`dist/`、`mysql/data/`、`nginx/certs/`、上传文件。
- 不在文档/源码/小程序包中写真实 AppSecret / JWT 密钥 / 加密密钥 / SMTP 授权码 / LLM API Key。
- 新增功能同步更新本文件 + 计划书版本号；修改安全相关字段必须同步更新 `production-security-checklist.md`。
- 不主动 commit，除非用户明确要求；commit 前检查 `git status` / `git diff`，只 stage 预期文件。
