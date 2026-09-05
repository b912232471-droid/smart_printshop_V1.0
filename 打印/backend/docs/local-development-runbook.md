# Local Development Runbook

This path starts the microservice version locally with Docker Compose:

- MySQL 8.0
- Nacos 2.5.1
- Redis 7.4
- Qdrant
- print-service
- photo-service
- schedule-service
- chat-service
- chat-worker
- Gateway

## 1. Generate Local Env

```bash
cd 打印/backend
python scripts/generate_local_dev_env.py --force
```

The generated `.env.local` keeps production auth boundaries enabled, but uses local secrets and development-friendly switches:

- `PRINTSHOP_REQUIRE_HTTPS=false`
- `PRINTSHOP_VIRUS_SCAN_ENABLED=false`
- `SCHEDULE_SYNC_MOCK_ENABLED=true`
- `CHAT_SEED_DEFAULTS=true`
- `CHAT_VECTOR_ENABLED=true`
- `CHAT_INGESTION_INLINE=false`
- first local `superadmin` bootstrap enabled when the `admin` table is empty

Default local admin:

```text
username: superadmin
password: LocalDev@2026
```

If `mysql/data` already exists, do not regenerate `.env.local` with `--force` unless you also reset the local database. MySQL stores the old generated password inside the data directory, while a new `.env.local` contains new credentials.

## 2. Start Services

Package Java service jars first:

```bash
python scripts/package_java_services.py
```

Start middleware first, then start the application services. Both Compose files use the fixed `print_shop` project name and the same external network:

```bash
docker compose --env-file .env.middleware.local -f docker-compose.middleware.yml up -d
docker compose --env-file .env.local --env-file .env.middleware.local -f docker-compose.yml -f docker-compose.dev.yml up -d --build
```

The second env file deliberately wins for MySQL credentials, so application containers connect to the existing `print_db` volume. Do not add `--remove-orphans`; middleware and application services are managed by separate Compose files under the same project.

The development override exposes these ports on the host:

```text
Gateway:          http://127.0.0.1:8080
photo-service:    http://127.0.0.1:8091
schedule-service: http://127.0.0.1:8092
chat-service:     http://127.0.0.1:8093
Nacos:            http://127.0.0.1:8848
MySQL:            127.0.0.1:3307
Qdrant:           http://127.0.0.1:6333
```

## 3. Quick Check

```bash
python scripts/production_smoke_test.py \
  --base-url http://127.0.0.1:8080 \
  --env .env.local \
  --admin-username superadmin \
  --admin-password LocalDev@2026 \
  --skip-chat-ask
```

For chat knowledge-base verification, remove `--skip-chat-ask` after `chat-service` has finished starting.

Markdown 文档由 `chat-worker` 后台处理。首次导入会下载本地中文 Embedding 模型，等待管理端文档状态从 `pending/processing` 变为 `ready` 后再验证语义检索。

For real photo inference, add:

```bash
--photo-image <local-face-sample.jpg>
```

## 4. Stop Services

```bash
docker compose --env-file .env.local --env-file .env.middleware.local -f docker-compose.yml -f docker-compose.dev.yml down
docker compose --env-file .env.middleware.local -f docker-compose.middleware.yml down
```

To reset local database data:

```bash
docker compose --env-file .env.local --env-file .env.middleware.local -f docker-compose.yml -f docker-compose.dev.yml down
docker compose --env-file .env.middleware.local -f docker-compose.middleware.yml down -v
```

The reset command deletes the `print_shop_mysql_data` volume. Back up the database before using it.
