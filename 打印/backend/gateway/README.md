# Printshop Gateway

This service is the API entry point for the microservice topology.

Default routes:

- `/api/print/**` rewrites to the current print service `/api/**` endpoints.
- `/api/photo/**` forwards to `photo-service`.
- `/api/schedule/**` forwards to `schedule-service`.
- `/api/chat/**` forwards to `chat-service`.
- `/api/**` is kept as a legacy compatibility route by default. The current mini program and admin console use `/api/print/**`, so production can tighten this route with `GATEWAY_PRINT_COMPAT_PATHS` after old clients are gone.

Service discovery:

- Local development keeps Nacos disabled by default and routes to local service ports: print `8081`, photo `8091`, schedule `8092`, chat `8093`.
- Docker/production enables Nacos with `NACOS_ENABLED=true` and sets service URIs to `lb://print-service`, `lb://photo-service`, `lb://schedule-service`, and `lb://chat-service`.
- The explicit routes stay in `application.yml`; service discovery only resolves the target service instance.

Docker exposure:

- `打印/backend/docker-compose.yml` is production-oriented and keeps Gateway on the Docker internal network by default.
- Production HTTPS traffic should enter through `docker-compose.nginx.yml`, which publishes Nginx `80/443` and proxies `/api/**` to Gateway.
- For local debugging, run with `docker-compose.dev.yml` to publish Gateway `8080`, MySQL `3306` and Nacos `8848` explicitly.

Required production environment variables:

- `GATEWAY_PORT`, default `8080`
- `NACOS_ENABLED=true`
- `NACOS_SERVER_ADDR`, for example `nacos:8848`
- `PRINT_SERVICE_URI=lb://print-service`
- `PHOTO_SERVICE_URI=lb://photo-service`
- `SCHEDULE_SERVICE_URI=lb://schedule-service`
- `CHAT_SERVICE_URI=lb://chat-service`
- `GATEWAY_ACTUATOR_EXPOSURE`, default `health,info`
- `GATEWAY_AUTH_ENABLED`, default `false` for standalone local runs and `true` in Docker production.
- `GATEWAY_JWT_SECRET`, must match `PRINTSHOP_JWT_SECRET` when Gateway auth is enabled.
- `GATEWAY_PRINT_COMPAT_PATHS`, default `/api/**`; set to a narrow comma-separated allowlist such as `/api/public-files/**` or to an unused sentinel path such as `/__disabled-print-compat/**` after old clients no longer need the legacy print route.
- `GATEWAY_TRUST_FORWARDED_HEADERS`, default `false`; enable only when a trusted reverse proxy overwrites `X-Forwarded-For`
- `GATEWAY_RATE_LIMIT_ENABLED`, default `true`
- `GATEWAY_RATE_LIMIT_REQUESTS`, default `600`
- `GATEWAY_RATE_LIMIT_WINDOW_SECONDS`, default `60`
- `GATEWAY_REQUEST_LOG_ENABLED`, default `true`
- `GATEWAY_CB_SLIDING_WINDOW_SIZE`, default `20`
- `GATEWAY_CB_MINIMUM_CALLS`, default `10`
- `GATEWAY_CB_FAILURE_RATE_THRESHOLD`, default `50`
- `GATEWAY_CB_WAIT_DURATION`, default `30s`
- `GATEWAY_CB_HALF_OPEN_CALLS`, default `3`
- `GATEWAY_CB_TIMEOUT`, default `10s`
- Optional per-service circuit breaker overrides use `GATEWAY_PRINT_CB_*`, `GATEWAY_PHOTO_CB_*`, `GATEWAY_SCHEDULE_CB_*`, and `GATEWAY_CHAT_CB_*`. Each supports `SLIDING_WINDOW_SIZE`, `MINIMUM_CALLS`, `FAILURE_RATE_THRESHOLD`, `WAIT_DURATION`, `HALF_OPEN_CALLS`, and `TIMEOUT`, and falls back to the matching `GATEWAY_CB_*` value when unset.

Security defaults:

- Gateway applies Spring Cloud Gateway `SecureHeaders` to proxied responses.
- Gateway can validate the same HS256 JWT used by print-service before forwarding protected `/api/**` requests. Public routes match print-service rules, including `/api/print/**` compatibility paths.
- Gateway records structured request logs with client IP, method, path, status and duration.
- Gateway applies an in-memory client IP + route-group rate limit before forwarding to backend services.
- Gateway ignores `X-Forwarded-For` by default so clients cannot spoof IPs to bypass rate limits. Enable forwarded headers only behind a trusted proxy that overwrites them.
- Gateway wraps print, photo, schedule, and chat routes with Resilience4J circuit breakers and returns a stable 503 JSON through each service fallback endpoint when the backend is unavailable or times out. Shared `GATEWAY_CB_*` defaults can be overridden per service so long-running AI photo inference, schedule sync, and chat responses can use different timeout and breaker windows.
- Actuator exposure defaults to `health,info`; only expose `gateway` on trusted networks during troubleshooting.

Validation:

```bash
# from the backend root
mvnw.cmd clean package -DskipTests
```
