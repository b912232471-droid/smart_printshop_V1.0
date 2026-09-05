# 生产部署 Runbook

本文按实际执行顺序串联生产部署步骤。安全要求以 `production-security-checklist.md` 为准；本文只负责把命令和验收闸口排成可执行流程。

## 0. 前置条件

- 已取得正式域名，并完成 DNS 指向生产服务器。
- 已准备 TLS 证书，默认文件名为 `fullchain.pem` 和 `privkey.pem`。
- 已确定普通用户与管理员的初始账号发放和密码重置流程。
- 如启用 AI 客服大模型、课表同步或图片审核，已取得对应生产凭证和 endpoint。
- 服务器只对公网开放 `80/443`，不要发布 Gateway、MySQL、Nacos、print-service、photo-service、schedule-service、chat-service 或 ClamAV 端口。

## 1. 生成生产 env 草稿

在项目根目录执行：

```bash
python 打印/backend/scripts/generate_production_env.py \
  --domain <domain> \
  --compat-mode full \
  --output .env.production.local
```

说明：

- 默认 `--compat-mode full` 会保留 `GATEWAY_PRINT_COMPAT_PATHS=/api/**`，用于新客户端发布前的过渡。
- 新管理端和小程序发布后，再用 `legacy_print_compat_audit.py` 判断是否改为 `/api/public-files/**` 或 `/__disabled-print-compat/**`。
- 生成器只处理本地可自动生成的密码和密钥；真实证书、DeepSeek、课表适配器、图片审核等外部值仍需按生产实际补齐。
- 首次部署全新数据库时，如 `admin` 表为空，可在 `.env` 中临时设置 `PRINTSHOP_BOOTSTRAP_ADMIN_ENABLED=true`、`PRINTSHOP_BOOTSTRAP_ADMIN_USERNAME` 和 `PRINTSHOP_BOOTSTRAP_ADMIN_PASSWORD`。密码必须满足管理员密码强度规则；生成器不会自动写入首个管理员密码。

## 2. 放置证书和管理端配置

生产默认从 `打印/backend/nginx/certs` 挂载证书：

```text
打印/backend/nginx/certs/fullchain.pem
打印/backend/nginx/certs/privkey.pem
```

如果使用其他证书目录，需在 `.env` 中同步调整：

```env
NGINX_CERT_DIR=/absolute/cert/dir
NGINX_CERTIFICATE=/etc/nginx/certs/fullchain.pem
NGINX_CERTIFICATE_KEY=/etc/nginx/certs/privkey.pem
```

Web 工作区构建前安装依赖；管理端如启用门店地图选点，同时设置腾讯地图 Key：

```bash
cd 打印/printshop-web
set VITE_TENCENT_MAP_KEY=<tencent-map-browser-key>
npm ci
npm run build
```

Linux shell 使用：

```bash
cd 打印/printshop-web
VITE_TENCENT_MAP_KEY=<tencent-map-browser-key> npm ci
VITE_TENCENT_MAP_KEY=<tencent-map-browser-key> npm run build
```

## 3. 本地测试和打包

Java 服务（应用侧测试类已移除，以打包为准；backend 根 pom 聚合三个模块，wrapper 在 backend 根）：

```bash
cd 打印/backend
./mvnw clean package -DskipTests   # 全 reactor，一次产出 3 个 jar
# 等价于：
python scripts/package_java_services.py
```

Windows PowerShell 可将 `./mvnw` 替换为 `mvnw.cmd`。

Python 服务无测试套件；改动后可在具备依赖的环境做语法级自检：`python -m compileall app`。

## 4. 上线前静态闸口

在 `打印/backend` 使用真实 `.env` 执行：

```bash
python scripts/production_readiness_check.py --env .env --backend-dir .
docker compose --env-file .env -f docker-compose.yml -f docker-compose.nginx.yml config
```

要求：

- `production_readiness_check.py` 不得出现 `FAIL`。
- `docker compose config` 中对公网发布端口只应有 Nginx `80/443`。
- 不要使用 `docker-compose.dev.yml` 启动生产。
- 如启用 `PRINTSHOP_BOOTSTRAP_ADMIN_ENABLED=true`，就绪检查只允许出现“需首次登录后关闭”的 `WARN`，不得出现用户名或密码相关 `FAIL`。

## 5. 启动生产容器

先启动统一 `print_shop` 项目下的中间件：

```bash
cd 打印/backend
docker compose --env-file .env -f docker-compose.middleware.yml up -d
```

确认 `print_db`、`print_nacos`、`print_redis`、`print_clamav` 正常后，再启动业务服务。`print_redis` 保存登录验证码，必须配置强 `REDIS_PASSWORD`，且 `redis-cli` 健康检查返回 `PONG`。两个编排文件共享 `print_shop` 项目名和 Docker 网络；不要使用 `--remove-orphans`。

单实例 photo-service：

```bash
cd 打印/backend
docker compose --env-file .env -f docker-compose.yml -f docker-compose.nginx.yml up -d --build
```

3 实例 photo-service：

```bash
cd 打印/backend
docker compose --env-file .env -f docker-compose.yml -f docker-compose.nginx.yml up -d --build --scale photo-service=3
```

基础状态检查：

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.nginx.yml ps
docker compose --env-file .env -f docker-compose.yml -f docker-compose.nginx.yml logs --tail=120 gateway
docker compose --env-file .env -f docker-compose.yml -f docker-compose.nginx.yml logs --tail=120 nginx
```

如果本次是全新库首次创建管理员，确认 print-service 日志出现 `bootstrap superadmin account '<username>' created` 后，立即用该账号登录管理端，新增或确认后续管理员账号，然后修改 `.env`：

```env
PRINTSHOP_BOOTSTRAP_ADMIN_ENABLED=false
PRINTSHOP_BOOTSTRAP_ADMIN_USERNAME=
PRINTSHOP_BOOTSTRAP_ADMIN_PASSWORD=
PRINTSHOP_BOOTSTRAP_ADMIN_REAL_NAME=
PRINTSHOP_BOOTSTRAP_ADMIN_PHONE=
PRINTSHOP_BOOTSTRAP_ADMIN_EMAIL=
```

清理后重启 print-service 并复查：

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.nginx.yml up -d print-service
python scripts/production_readiness_check.py --env .env --backend-dir .
```

## 6. 正式入口 smoke test

使用真实域名执行：

```bash
python scripts/production_smoke_test.py \
  --base-url https://<domain> \
  --env .env \
  --output pressure-results/production-smoke.json
```

如已完成首个管理员创建，追加管理端登录验证：

```bash
python scripts/production_smoke_test.py \
  --base-url https://<domain> \
  --env .env \
  --admin-username <admin-username> \
  --admin-password '<admin-password>' \
  --expect-admin-role superadmin \
  --output pressure-results/production-smoke-admin.json
```

该检查会通过 Gateway 调用 `/api/print/admin/login`，确认返回管理员 JWT，再用该 JWT 访问 `/api/print/admin/`；脚本不会打印密码或 token。

如果有真实人脸样本，追加证件照推理验证：

```bash
python scripts/production_smoke_test.py \
  --base-url https://<domain> \
  --env .env \
  --photo-image <sample.jpg> \
  --photo-endpoint /api/photo/generate-id-photo \
  --output pressure-results/production-smoke-photo.json
```

该检查不只判断 HTTP 2xx，还会校验响应 `code=0`、`data.image` 可 base64 解码、图片魔数为 PNG/JPEG、`width/height` 为正整数，避免空响应或错误 JSON 被误判为证件照链路可用。

课表真实适配器启用后执行：

```bash
python scripts/production_smoke_test.py \
  --base-url https://<domain> \
  --env .env \
  --schedule-student-id <student-id> \
  --schedule-jw-username <jw-username> \
  --schedule-jw-password '<jw-password>' \
  --schedule-sync expect-ready \
  --schedule-xnm <school-year> \
  --schedule-xqm <term> \
  --output pressure-results/production-smoke-schedule.json
```

该检查会先通过 `/api/schedule/bind` 绑定真实测试账号并确认响应不回显 `jwPassword`，再调用 `/api/schedule/sync`。如果已提前为 smoke 用户绑定账号，可不传绑定参数，直接保留 `--schedule-sync expect-ready`。

DeepSeek 已配置时，客服问答可要求来源为 `deepseek`：

```bash
python scripts/production_smoke_test.py \
  --base-url https://<domain> \
  --env .env \
  --expect-chat-source deepseek \
  --output pressure-results/production-smoke-deepseek.json
```

## 7. 压测和观测

photo-service Gateway 健康检查压测：

```bash
python scripts/photo_pressure_test.py \
  --base-url https://<domain> \
  --endpoint /api/photo/health \
  --requests 100 \
  --concurrency 10 \
  --token <user-jwt> \
  --output pressure-results/production-photo-health.json
```

chat-service Gateway 问答压测：

```bash
python scripts/chat_pressure_test.py \
  --base-url https://<domain> \
  --jwt-secret <same-as-PRINTSHOP_JWT_SECRET> \
  --requests 100 \
  --concurrency 10 \
  --output pressure-results/production-chat-ask.json
```

重点观察：

- Gateway 日志是否有大量 `gateway_unauthorized`、`gateway_rate_limited` 或 503 fallback。
- Nginx 是否只对外暴露 `80/443`。
- Nacos 中是否出现预期数量的 `photo-service` 实例。
- ClamAV 首次启动后病毒库是否更新完成。

## 8. 旧兼容路由收紧

新管理端和小程序发布后，先审计日志：

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.nginx.yml logs gateway \
  | python scripts/legacy_print_compat_audit.py -
```

决策：

- 仍有 `/api/order`、`/api/service`、`/api/store`、`/api/file`、`/api/admin`、`/api/wx`、`/api/user` 等旧动态路径访问：继续保持 `GATEWAY_PRINT_COMPAT_PATHS=/api/**`。
- 只剩 `/api/public-files/**`：改为 `GATEWAY_PRINT_COMPAT_PATHS=/api/public-files/**`。
- 没有旧路径访问：改为 `GATEWAY_PRINT_COMPAT_PATHS=/__disabled-print-compat/**`。

修改 `.env` 后重启 Gateway：

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.nginx.yml up -d gateway
python scripts/production_smoke_test.py --base-url https://<domain> --env .env --output pressure-results/production-smoke-after-compat-tightening.json
```

## 9. 回滚入口

如果正式入口 smoke test 失败：

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.nginx.yml logs --tail=200 gateway
docker compose --env-file .env -f docker-compose.yml -f docker-compose.nginx.yml logs --tail=200 print-service
docker compose --env-file .env -f docker-compose.yml -f docker-compose.nginx.yml logs --tail=200 nginx
```

常见临时回滚：

- 兼容路由收紧导致旧客户端不可用：恢复 `GATEWAY_PRINT_COMPAT_PATHS=/api/**` 并重启 Gateway。
- photo-service 多实例异常：先用单实例启动验证模型和依赖，再恢复 `--scale photo-service=3`。
- DeepSeek 异常：临时清空 `DEEPSEEK_API_KEY`，chat-service 会回退本地知识库检索。
- 课表同步适配器异常：设置 `SCHEDULE_SYNC_HTTP_ENABLED=false`，同步接口返回 503，避免写入错误课表数据。
