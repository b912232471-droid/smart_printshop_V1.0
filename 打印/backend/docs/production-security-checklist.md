# 生产安全检查清单

生产执行顺序见 `production-deployment-runbook.md`；本文件保留上线安全要求和验收点。

## 必须完成

- 使用 `打印/backend/.env.example` 生成 `.env`，所有数据库密码、JWT 密钥和字段加密密钥都必须替换为生产值。可先执行 `python 打印/backend/scripts/generate_production_env.py --domain <domain>` 生成被 `.gitignore` 忽略的 `打印/backend/.env.production.local` 草稿。
- `PRINTSHOP_JWT_SECRET` 至少 32 位随机字符；未配置时服务会拒绝启动。
- 平台 JWT 必须由后端签发，且保留完整 claim：`type/id/sub/role/username/iat/exp/jti`，其中 `id` 对应 `user_account.id`，`jti` 为 UUID；不要手工构造不完整 token。
- `PRINTSHOP_FIELD_ENCRYPTION_KEY` 至少 32 位随机字符；用户手机号、管理员手机号/邮箱会使用该密钥加密存储，且该密钥必须备份，丢失后历史密文不可恢复。
- `SCHEDULE_FIELD_ENCRYPTION_KEY` 至少 32 位随机字符；教务账号密码会使用该密钥加密存储，且该密钥必须备份。
- Docker/生产保持 `SCHEDULE_AUTH_ENABLED=true`，且 `SCHEDULE_JWT_SECRET` 必须与平台 JWT 密钥一致；不要直连 schedule-service 绕过 Gateway。
- 管理员登录失败锁定保持启用，生产可通过 `PRINTSHOP_LOGIN_MAX_FAILURES`、`PRINTSHOP_LOGIN_LOCK_SECONDS`、`PRINTSHOP_LOGIN_WINDOW_SECONDS` 调整阈值。
- 后端接口基础限流保持启用，生产可通过 `PRINTSHOP_RATE_LIMIT_ENABLED`、`PRINTSHOP_RATE_LIMIT_REQUESTS`、`PRINTSHOP_RATE_LIMIT_WINDOW_SECONDS` 调整阈值。
- `PRINTSHOP_TRUST_FORWARDED_HEADERS` 默认保持 `false`；只有在 Nginx/Gateway 明确覆盖 `X-Forwarded-For` / `X-Real-IP` / `X-Forwarded-Proto`，且 print-service 不被公网直连时才可开启。
- 生产日志采集必须包含 `AUDIT` 和 `SECURITY_ALERT` logger，至少保留管理员登录、安全异常、限流、订单状态变更和异常登录锁定告警记录。
- 异常登录告警保持启用；如有外部告警系统，通过 `PRINTSHOP_SECURITY_ALERT_WEBHOOK_URL` 配置 webhook。
- 执行 `打印/backend/mysql/init/001_security_migration.sql`，确保 `admin.password` 可容纳 BCrypt 哈希，敏感字段可容纳密文，且订单/文件/用户查询索引存在。
- 第一个 `superadmin` 优先通过一次性引导创建：仅在全新库且 `admin` 表为空时临时设置 `PRINTSHOP_BOOTSTRAP_ADMIN_ENABLED=true`、`PRINTSHOP_BOOTSTRAP_ADMIN_USERNAME` 和 `PRINTSHOP_BOOTSTRAP_ADMIN_PASSWORD`，由 print-service 复用管理员注册逻辑写入 BCrypt 密码和加密手机号/邮箱。首次登录并确认管理员账号后，必须关闭该开关并清空引导用户名、密码、姓名、手机号和邮箱。不要直接改库写入管理员，也不要恢复 `admin/123456` 这类默认账号。
- 管理员账号只使用 `superadmin`、`admin`、`operator` 三类角色和 `0/1` 状态；不要直接改库写入其他角色或异常状态。
- 生产公网入口只暴露 Nginx `80/443`；Gateway `8080`、print-service `8081`、photo-service `8091`、schedule-service `8092`、chat-service `8093`、MySQL、Redis、Nacos、ClamAV 都只在 Docker 内网暴露。本地调试才使用 `docker-compose.dev.yml` 发布调试端口。
- 登录验证码答案只存 Redis，保持一次性读取删除和短 TTL；生产必须配置强 `REDIS_PASSWORD`，不得将 Redis 端口发布到公网。
- Gateway 默认只暴露 Actuator `health,info`；不要在公网暴露 `gateway` actuator 端点。
- Docker/生产保持 `GATEWAY_AUTH_ENABLED=true`，且 `GATEWAY_JWT_SECRET` 必须与 print-service 的 `PRINTSHOP_JWT_SECRET` 一致；本地 standalone 网关可关闭该开关。
- `GATEWAY_PRINT_COMPAT_PATHS` 默认保持 `/api/**` 用于旧客户端过渡；新管理端和小程序发布并确认无旧路径访问后，改为 `/api/public-files/**` 等白名单或 `/__disabled-print-compat/**` 这类不可命中的哨兵路径。
- Gateway 入口限流和请求日志保持启用，生产可通过 `GATEWAY_RATE_LIMIT_ENABLED`、`GATEWAY_RATE_LIMIT_REQUESTS`、`GATEWAY_RATE_LIMIT_WINDOW_SECONDS`、`GATEWAY_REQUEST_LOG_ENABLED` 调整。
- `GATEWAY_TRUST_FORWARDED_HEADERS` 默认保持 `false`；只有在 Nginx 明确覆盖 `X-Forwarded-For`，且 Gateway 不被公网直连时才可开启。
- Gateway 到 print-service、photo-service、schedule-service、chat-service 的熔断降级保持启用，生产可通过通用 `GATEWAY_CB_*` 调整默认阈值，也可通过 `GATEWAY_PRINT_CB_*`、`GATEWAY_PHOTO_CB_*`、`GATEWAY_SCHEDULE_CB_*`、`GATEWAY_CHAT_CB_*` 为不同服务设置独立滑动窗口、最小调用数、失败率阈值、打开等待时间、半开探测数和超时时间。
- `PRINTSHOP_REQUIRE_HTTPS=true`，Nginx 配置 HSTS，并限制 `PRINTSHOP_ALLOWED_ORIGINS`、`GATEWAY_ALLOWED_ORIGINS` 为真实域名；不要在直连后端的场景开启可信转发头，否则协议/IP 判断会依赖代理头。
- 使用 `docker-compose.nginx.yml` 启动生产边缘入口，Nginx 模板位于 `打印/backend/nginx/templates/default.conf.template`。
- `docker-compose.nginx.yml` 会为 Gateway 和 print-service 开启可信转发头；必须保证 Gateway/print-service 不对公网发布端口，且 Nginx 模板覆盖写入 `X-Forwarded-For` 和 `X-Forwarded-Proto`。
- 本地 HTTP 调试使用 `docker-compose.dev.yml`，该覆盖文件会关闭 print-service 的 HTTPS 强制校验；不要把 dev 覆盖文件用于生产。
- 生产启动 Nginx 前必须先构建管理端 `dist`，并将真实证书挂载到 `NGINX_CERT_DIR`，默认期望文件为 `fullchain.pem` 和 `privkey.pem`。
- 如启用管理端门店地图选点，构建管理端前必须通过 `VITE_TENCENT_MAP_KEY` 注入腾讯地图浏览器 Key，并在腾讯位置服务后台限制生产管理端域名白名单；不要把 Key 写入 `index.html` 或源码。
- `NGINX_SERVER_NAME`、`NGINX_CLIENT_MAX_BODY_SIZE`、`NGINX_CERTIFICATE`、`NGINX_CERTIFICATE_KEY` 必须与真实域名和证书路径一致。
- 上传文件目录使用独立持久化卷，定期备份，禁止映射到 Web 静态目录。
- 订单文件只允许通过数据库中的 `/files/<单个文件名>` 映射到上传目录；不要手工写入带路径分隔符、绝对路径、盘符或 `..` 的 `file_url`。
- 客户端响应中的订单文件 `fileUrl` 后端只能暴露 `/api/file/download/{fileId}` 鉴权下载地址，不应返回内部 `/files/<uuid>` 存储路径；管理端和小程序对外访问应经 Gateway `/api/print/file/download/{fileId}`。
- 上传展示文件名必须由服务端清洗路径、控制字符和危险字符；不能直接信任客户端传入的 `originalFileName`。
- 文件删除会同步删除磁盘文件；生产备份和恢复时必须同时处理 `file_info` 表和上传目录，避免元数据与磁盘文件不一致。
- 文件上传会校验扩展名、文件头、PDF 结构/页数/加密状态/渲染复杂度、图片尺寸/像素数、图片内容合规、OOXML ZIP 结构/复杂度和 ClamAV 病毒扫描；生产应保持 `PRINTSHOP_VIRUS_SCAN_ENABLED=true`、`PRINTSHOP_VIRUS_SCAN_FAIL_CLOSED=true`。
- PDF 页数限制、加密文件策略和渲染复杂度限制可通过 `PRINTSHOP_PDF_MAX_PAGES`、`PRINTSHOP_PDF_REJECT_ENCRYPTED`、`PRINTSHOP_PDF_MAX_OBJECTS`、`PRINTSHOP_PDF_MAX_PAGE_CONTENT_STREAMS`、`PRINTSHOP_PDF_MAX_PAGE_CONTENT_BYTES`、`PRINTSHOP_PDF_MAX_TOTAL_CONTENT_BYTES` 调整；生产不要无限放大页数、对象数或内容流大小限制。
- 图片宽高和总像素限制可通过 `PRINTSHOP_IMAGE_MAX_WIDTH`、`PRINTSHOP_IMAGE_MAX_HEIGHT`、`PRINTSHOP_IMAGE_MAX_PIXELS` 调整；生产不要无限放大。
- 图片内容合规检测通过 `PRINTSHOP_IMAGE_MODERATION_*` 接入外部审核服务；生产启用时设置 `PRINTSHOP_IMAGE_MODERATION_ENABLED=true`、`PRINTSHOP_IMAGE_MODERATION_ENDPOINT_URL` 和 `PRINTSHOP_IMAGE_MODERATION_API_KEY`，并建议保持 `PRINTSHOP_IMAGE_MODERATION_FAIL_CLOSED=true`。
- OOXML 会拒绝路径穿越、宏工程、ActiveX、OLE/嵌入对象、宏表和外部关系；复杂度限制可通过 `PRINTSHOP_OPENXML_MAX_ENTRIES`、`PRINTSHOP_OPENXML_MAX_ENTRY_UNCOMPRESSED_BYTES`、`PRINTSHOP_OPENXML_MAX_TOTAL_UNCOMPRESSED_BYTES` 调整，生产不要无限放大。
- ClamAV 只在 Docker 内网暴露给 print-service；首次启动可能需要等待病毒库更新完成后再验证上传。
- 微信小程序通过 Gateway `POST /api/print/auth/register` 注册，通过 `POST /api/print/auth/login` 使用账号密码换取平台 JWT。
- 普通用户和管理员凭据只能保存在 `user_account` 表，密码必须使用 BCrypt 哈希；资料、订单和课表鉴权统一使用 `user_account.id`。
- 匿名门店接口只开放 Gateway `GET /api/print/store/active`，且响应不得包含 `deviceCode`、创建时间、更新时间等内部管理字段；完整门店列表和详情必须使用管理员 JWT。
- 收紧 Gateway `/api/**` 到 print-service 的旧兼容路由前，必须先确认已发布的管理端和小程序不再包含 `/api/order`、`/api/service`、`/api/store`、`/api/file`、`/api/admin`、`/api/wx`、`/api/user` 旧客户端路径；再用 `python 打印/backend/scripts/legacy_print_compat_audit.py <gateway-or-nginx-log>` 审计 Gateway/Nginx 访问日志。仍有旧动态接口访问时不要收紧；只剩 `/api/public-files/**` 时可收紧为该白名单；完全无旧路径时可收紧为 `/__disabled-print-compat/**`。收紧通过 `GATEWAY_PRINT_COMPAT_PATHS` 完成，不需要重新构建镜像。
- 服务项目和门店配置即使只允许管理员维护，也必须经过服务层校验；价格、状态、经纬度、公开图片地址等字段不能直接信任管理端表单。
- 当前支付能力是模拟支付，仅用于演示订单流转；上线文案、后台统计和操作入口不能表述为真实微信收款。
- 即使支付能力仅为模拟支付，订单创建也必须由后端校验服务项目、营业门店和模拟订单金额；不要信任小程序或管理端提交的服务、门店、金额字段。
- schedule-service 只通过 Gateway 暴露 `/api/schedule/**`，不要直接发布 `8092` 到公网；教务账号绑定响应和课表查询响应不得返回 `jw_password`。
- 生产保持 `SCHEDULE_SYNC_MOCK_ENABLED=false`；真实教务同步适配器接入前，同步接口返回 503 是预期行为，不要用 mock 数据冒充真实课表。
- 如启用 schedule-service HTTP JSON 教务适配器，必须设置 `SCHEDULE_SYNC_HTTP_ENABLED=true` 和内网可信的 `SCHEDULE_SYNC_HTTP_ENDPOINT_URL`；该地址会接收解密后的教务密码，必须使用 HTTPS 或受控内网链路，并通过 `SCHEDULE_SYNC_HTTP_BEARER_TOKEN` 或等效网关策略限制访问。
- 教务适配器响应必须只返回课程字段，不得回传教务密码、Cookie、验证码票据或第三方系统会话信息；上线前需用真实测试账号验证同步结果属于当前绑定用户。
- photo-service 只通过 Gateway 暴露 `/api/photo/**`，不要直接发布 `8091` 到公网；图片上传限制通过 `PHOTO_MAX_UPLOAD_BYTES`、`PHOTO_MAX_IMAGE_WIDTH`、`PHOTO_MAX_IMAGE_HEIGHT`、`PHOTO_MAX_IMAGE_PIXELS` 控制，生产不要无限放大。
- photo-service 生产镜像必须包含 `pretrained/modnet.onnx`，且 `PHOTO_MODEL_PATH` / `MODEL_PATH` 指向该模型；启动后需验证换底色和生成证件照接口可以完成一次真实推理。本地 Docker 3 容器换底色推理压测和 Gateway/Nacos 健康检查链路压测报告已留存在 `打印/backend/pressure-results/`，生产仍需使用正式入口和真实人脸样本复测生成证件照链路。
- photo-service 多实例生产启动使用 `--scale photo-service=3`，不要给 photo-service 添加固定 `container_name`；通过 Nacos 注册的多个 `photo-service` 实例由 Gateway `lb://photo-service` 负载均衡。
- photo-service 上线后验证 `/api/photo/metrics` 和内部 `/metrics` 指标；公网仍只暴露 Gateway/Nginx，不直接暴露实例级 `8091`。
- chat-service 只通过 Gateway 暴露 `/api/chat/**`，不要直接发布 `8093` 到公网；生产保持 `CHAT_AUTH_ENABLED=true`，且 `CHAT_JWT_SECRET` 必须与平台 JWT 密钥一致。
- chat-service 策略、Markdown 文档和 FAQ 维护接口只允许管理员 JWT 访问；用户只访问问答、自己的会话和反馈。FAQ 批量导入仅接受 CSV/XLSX、Markdown 仅接受 UTF-8 `.md/.markdown`。生产保持 `CHAT_VECTOR_ENABLED=true`、`CHAT_VECTOR_REQUIRED=true`、`CHAT_INGESTION_INLINE=false`，Qdrant 仅在 Docker 内网暴露，文档摄取由 `chat-worker` 执行。
- Agent 工具只允许管理端白名单中的只读工具；当前用户 ID 必须由 JWT Principal 注入，禁止模型传入用户 ID、URL 或 SQL。JWT、教务密码、手机号等敏感信息不得进入模型提示词；每次工具调用写入 `agent_tool_calls` 审计。
- 如配置 `DEEPSEEK_API_KEY`，密钥只能通过生产环境变量注入，不得写入源码、小程序或管理端包。Gateway 客服超时应覆盖至少两轮模型调用，当前默认 `GATEWAY_CHAT_CB_TIMEOUT=90s`。
- chat-service 上线后验证 `/api/chat/metrics` 和内部 `/metrics` 指标；公网仍只暴露 Gateway/Nginx，不直接暴露实例级 `8093`。
- 使用 `打印/backend/scripts/chat_pressure_test.py` 对 `/api/chat/ask` 做问答压测；通过 Gateway 压测时必须使用与平台一致的 JWT 密钥或传入真实用户 token，可用 `--output <report.json>` 留存报告。本地 Nacos + chat-service + Gateway 的问答与健康检查压测报告已留存在 `打印/backend/pressure-results/`。

## 上线前验证

- `python 打印/backend/scripts/generate_production_env.py --domain <domain>`，用于生成生产 `.env` 草稿；如需直接输出到标准输出，可追加 `--output -`，摘要会写入 stderr，不污染 env 内容。
- `python 打印/backend/scripts/production_readiness_check.py --env .env --backend-dir 打印/backend`，用于检查真实生产 `.env` 是否仍有占位值、JWT/加密密钥是否满足长度且跨服务一致、首个管理员引导配置是否缺失或弱口令、Nginx 证书文件和管理端 `dist` 是否存在、photo-service 模型是否可用、DeepSeek/课表适配器/图片审核/旧打印兼容路由是否仍处于未接入或过渡状态；存在 `FAIL` 时不要上线。
- `python 打印/backend/scripts/production_smoke_test.py --base-url https://<domain> --env 打印/backend/.env --output 打印/backend/pressure-results/production-smoke.json`，用于在生产容器启动后复测正式 Nginx/Gateway 入口：公开打印服务项、公开营业门店、受保护接口匿名 401、带平台 JWT 的 photo/chat/schedule 路由和客服问答均应通过；完成首个管理员创建后追加 `--admin-username <admin>`、`--admin-password '<password>'` 和 `--expect-admin-role superadmin` 验证管理端登录与管理员受保护接口；真实证件照推理追加 `--photo-image <sample.jpg>`，脚本会校验图片响应 payload，不只判断 HTTP 2xx；真实课表同步就绪后追加 `--schedule-student-id`、`--schedule-jw-username`、`--schedule-jw-password` 和 `--schedule-sync expect-ready`，脚本会先验证绑定响应不回显 `jwPassword` 再触发同步。
- `python 打印/backend/scripts/legacy_print_compat_audit.py <gateway-or-nginx-log>`，用于新管理端和小程序发布后判断是否可以收紧 `GATEWAY_PRINT_COMPAT_PATHS`；可通过 `docker compose logs gateway | python 打印/backend/scripts/legacy_print_compat_audit.py -` 直接读取 Gateway 日志。
- `cd 打印/backend && mvnw.cmd clean package -DskipTests`（根 pom 聚合 reactor，一次性构建 printshop + gateway + schedule-service；应用侧测试类已移除，历史测试留存于 `打印/backend-tests-backup-20260905.zip`）
- `cd 打印/backend && docker compose --env-file .env.example config`，用于无 `.env` 时验证配置结构；生产应替换为真实 `.env` 后再执行 `docker compose config`。
- `python 打印/backend/scripts/photo_pressure_test.py --base-url http://<gateway-or-photo-service> --endpoint /api/photo/health --requests 100 --concurrency 10`，用于压测健康检查和网关转发稳定性；真实图片推理压测需追加 `--image <sample.jpg>` 并改用 `/api/photo/change-background` 或 `/api/photo/generate-id-photo`。多实例直连验证可传入逗号分隔的多个 `--base-url` 并用 `--output <report.json>` 留存报告。
- 启用 `SCHEDULE_SYNC_HTTP_ENABLED=true` 后，用测试教务账号调用 `POST /api/schedule/sync`，确认返回导入数量、`GET /api/schedule` 可查询到真实课程，且接口响应和日志均不包含 `jw_password` 或明文密码。
- `python 打印/backend/scripts/chat_pressure_test.py --base-url http://<gateway-or-chat-service> --requests 100 --concurrency 10 --jwt-secret <same-as-platform-jwt-secret> --output <report.json>`，用于压测客服问答接口和网关转发稳定性；健康检查压测可追加 `--endpoint /api/chat/health`。
- `cd 打印/printshop-web && npm run build`
- `rg -n "/api/(order|service|store|file|admin|wx|user)|/api/public-files|/api/file|/api/store|/api/service|/api/order" 打印/printshop-web/apps 打印/miniprogram-1`，移除 Gateway 旧兼容路由前应无打印核心旧客户端路径残留。
- 预编译 jar 镜像构建前确认 `printshop/.dockerignore` 和 `gateway/.dockerignore` 保留 `target/*.jar`，然后执行 `cd 打印/backend && docker compose --env-file .env.example build gateway` 验证 Gateway 镜像可构建；生产构建 print-service 前同样应先完成 `printshop` jar 打包。
- 本地临时容器已完成 Gateway + print-service + MySQL + Nacos 核心联调，报告为 `打印/backend/pressure-results/core-gateway-print-mysql.json`；生产上线仍需用正式 `.env` 和 Nginx/Gateway 入口复测核心公开接口和受保护接口。
- `cd 打印/backend && docker compose --env-file .env.example config`，用于无 `.env` 时验证配置结构；生产应替换为真实 `.env` 后再执行 `docker compose config`。
- `cd 打印/backend && docker compose --env-file .env.example -f docker-compose.yml -f docker-compose.dev.yml config`，仅用于确认本地调试端口覆盖文件可解析，不用于生产启动。
- `cd 打印/backend && docker compose --env-file .env.example -f docker-compose.yml -f docker-compose.nginx.yml config`，用于验证生产 Nginx 入口覆盖文件可解析。
- 生产真实 `.env` 就绪后执行 `docker compose -f docker-compose.yml -f docker-compose.nginx.yml config`，确认最终配置只向公网发布 Nginx `80/443`。
- Java 服务应保持 Spring Cloud Alibaba `2025.0.x` 与 Spring Cloud `2025.0.x` / Spring Boot 3.5.x 对齐；上线前用 `NACOS_ENABLED=true` 启动 Gateway，确认不会出现 Nacos Discovery 二进制兼容异常。
- 用普通用户账号验证：不能访问其他用户订单、文件、头像以外的上传资源。
- 首次空库部署时验证：只在 `PRINTSHOP_BOOTSTRAP_ADMIN_ENABLED=true` 且 `admin` 表为空时创建一个 `superadmin`；创建后关闭引导开关并清空相关凭证，再重启 print-service，确认不会再次创建管理员。
- 用管理员账号验证：没有 `superadmin` 角色时不能注册、修改、删除管理员。
- 验证用户注册、账号密码登录、退出后重新登录、用户详情和管理员列表；任何响应均不得返回密码哈希。
- 验证门店接口：匿名访问完整门店列表或门店详情应返回 401，匿名访问营业门店列表不应返回设备号。
- 验证配置写入：服务项目负数/三位小数价格应被拒绝；营业门店缺少合法坐标、异常状态或外部/脚本/路径穿越图片地址应被拒绝。
- 验证订单创建：无效服务、关闭门店、非正数金额、超过两位小数金额、低于服务单价的模拟金额都应被拒绝。
- 验证文件元数据：文件列表、文件详情和订单文件响应中的后端 `fileUrl` 应为 `/api/file/download/{fileId}`，不应出现内部 `/files/` 存储路径；客户端展示/下载入口应归一化到 `/api/print/file/download/{fileId}`。
- 验证上传文件名：传入路径穿越、CR/LF/TAB/NUL 或危险字符的 `originalFileName` 时，展示名和下载响应头不应被污染；清洗为空时应回退 Multipart 文件名。

## 仍需后续实现

- 后续新增的其他敏感字段加密存储。
- AI证件照服务本地 Docker 3 容器换底色推理压测和 Gateway/Nacos 健康检查链路压测已完成并留存结果；生产上线前仍需使用正式 `.env`、Nginx/Gateway 入口和真实人脸样本补做生成证件照链路验证。客服服务本地 Nacos/Gateway 问答与健康检查压测已完成并留存结果；生产上线前仍需做真实 DeepSeek Key 联调和正式 Nginx/Gateway 入口复测。课表服务还需按目标学校教务协议配置/实现 HTTP 适配器后做真实账号联调。
