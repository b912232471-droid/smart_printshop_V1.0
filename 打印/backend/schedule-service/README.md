# Schedule Service

Independent course schedule service for the printshop platform.

Implemented endpoints:

- `POST /api/schedule/bind`: bind a school education account for the current platform user.
- `GET /api/schedule/bind-status`: return whether the current user has bound an account.
- `POST /api/schedule/sync`: trigger schedule synchronization. Production returns 503 until a real school adapter is configured; when the HTTP JSON adapter is enabled it imports returned courses into the local cache.
- `GET /api/schedule`: query stored courses by optional `xnm` and `xqm`.
- `GET /api/schedule/terms`: list available stored terms.

Security:

- API requests can validate the same HS256 JWT used by print-service through `SCHEDULE_AUTH_ENABLED=true` and `SCHEDULE_JWT_SECRET`.
- Education account passwords are encrypted with AES-GCM before storage through `SCHEDULE_FIELD_ENCRYPTION_KEY`.
- Bind/status/query responses never return `jw_password`.

Runtime configuration:

- `SCHEDULE_SERVER_PORT`, default `8092`
- `SCHEDULE_DATASOURCE_URL`, default `jdbc:mysql://localhost:3306/schedule_db?...`
- `SCHEDULE_DATASOURCE_USERNAME`
- `SCHEDULE_DATASOURCE_PASSWORD`
- `SCHEDULE_FIELD_ENCRYPTION_KEY`, at least 32 bytes
- `SCHEDULE_AUTH_ENABLED`, default `false` for standalone local runs and `true` in Docker production
- `SCHEDULE_JWT_SECRET`, must match `PRINTSHOP_JWT_SECRET` when auth is enabled
- `SCHEDULE_SYNC_MOCK_ENABLED`, default `false`; keep disabled in production
- `SCHEDULE_SYNC_HTTP_ENABLED`, default `false`; enables the HTTP JSON adapter
- `SCHEDULE_SYNC_HTTP_ENDPOINT_URL`, adapter endpoint called by `POST /api/schedule/sync`
- `SCHEDULE_SYNC_HTTP_BEARER_TOKEN`, optional bearer token sent to the adapter endpoint
- `SCHEDULE_SYNC_HTTP_TIMEOUT_MS`, default `10000`
- `SCHEDULE_ACTUATOR_EXPOSURE`, default `health,info`
- `NACOS_ENABLED`, `NACOS_SERVER_ADDR`, `NACOS_NAMESPACE`, `NACOS_GROUP`

HTTP JSON adapter contract:

```json
{
  "studentId": "20260001",
  "jwPassword": "plain-password-after-decryption",
  "xnm": "2025-2026",
  "xqm": "1"
}
```

Expected response:

```json
{
  "courses": [
    {
      "kcmc": "高等数学",
      "xqj": "1",
      "jcs": "1-2",
      "cdmc": "教学楼201",
      "xm": "王老师"
    }
  ]
}
```

`kcmc`、`xqj`、`jcs` are required. `cdmc` and `xm` may be empty. The schedule service stores courses under the requested `xnm/xqm` and never returns `jwPassword` to clients.

Validation:

```bash
# from the backend root
mvnw.cmd clean package -DskipTests
```
