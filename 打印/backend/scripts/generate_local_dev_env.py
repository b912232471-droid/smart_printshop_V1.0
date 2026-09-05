#!/usr/bin/env python3
"""
Generate a local-development .env file for the smart print backend.

The output is intended for Docker Compose on a developer machine. It uses local
secrets, disables production-only friction, keeps Gateway auth enabled, enables
mock schedule sync, and leaves external integrations such as WeChat and
DeepSeek as harmless local placeholders.
"""

import argparse
import secrets
import sys
from pathlib import Path


def configure_output_encoding() -> None:
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure:
            reconfigure(encoding="utf-8")


def main() -> int:
    configure_output_encoding()
    parser = argparse.ArgumentParser(description="Generate .env.local for local Docker Compose development.")
    parser.add_argument("--template", default="", help="Template env file. Defaults to backend .env.example.")
    parser.add_argument("--output", default=".env.local", help="Output env path relative to backend dir, or '-' for stdout.")
    parser.add_argument("--force", action="store_true", help="Overwrite output file if it already exists.")
    parser.add_argument("--admin-username", default="superadmin", help="Local bootstrap admin username.")
    parser.add_argument("--admin-password", default="LocalDev@2026", help="Local bootstrap admin password.")
    args = parser.parse_args()

    backend_dir = Path(__file__).resolve().parents[1]
    template_path = Path(args.template).resolve() if args.template else backend_dir / ".env.example"
    if not template_path.is_file():
        raise SystemExit(f"template env file not found: {template_path}")

    output_path = None if args.output == "-" else resolve_output_path(backend_dir, args.output)
    if output_path and output_path.exists() and not args.force:
        raise SystemExit(f"output exists: {output_path}; pass --force to overwrite")

    rendered = render_env(template_path, build_replacements(args))
    if output_path is None:
        print(rendered, end="")
    else:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(rendered, encoding="utf-8")
        print(f"generated: {output_path}")
        print("next: docker compose --env-file .env.local -f docker-compose.yml -f docker-compose.dev.yml up -d --build")
    return 0


def resolve_output_path(backend_dir: Path, output: str) -> Path:
    path = Path(output)
    if not path.is_absolute():
        path = backend_dir / path
    return path.resolve()


def build_replacements(args) -> dict[str, str]:
    db_password = secret(24)
    jwt_secret = secret(48)
    field_key = secret(48)
    origins = "http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000"
    return {
        "MYSQL_ROOT_PASSWORD": secret(24),
        "MYSQL_DATABASE": "print_shop",
        "MYSQL_USER": "print_shop",
        "MYSQL_PASSWORD": db_password,
        "PRINTSHOP_DATASOURCE_URL": "jdbc:mysql://mysql:3306/print_shop?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
        "PRINTSHOP_DATASOURCE_USERNAME": "print_shop",
        "PRINTSHOP_DATASOURCE_PASSWORD": db_password,
        "PRINTSHOP_JWT_SECRET": jwt_secret,
        "PRINTSHOP_FIELD_ENCRYPTION_KEY": field_key,
        "PRINTSHOP_REQUIRE_HTTPS": "false",
        "PRINTSHOP_ALLOWED_ORIGINS": origins,
        "PRINTSHOP_SECURITY_ALERT_ENABLED": "false",
        "PRINTSHOP_RATE_LIMIT_ENABLED": "false",
        "PRINTSHOP_BOOTSTRAP_ADMIN_ENABLED": "true",
        "PRINTSHOP_BOOTSTRAP_ADMIN_USERNAME": args.admin_username,
        "PRINTSHOP_BOOTSTRAP_ADMIN_PASSWORD": args.admin_password,
        "PRINTSHOP_BOOTSTRAP_ADMIN_REAL_NAME": "Local Super Admin",
        "PRINTSHOP_BOOTSTRAP_ADMIN_PHONE": "13800000000",
        "PRINTSHOP_BOOTSTRAP_ADMIN_EMAIL": "local-admin@example.com",
        "PRINTSHOP_VIRUS_SCAN_ENABLED": "false",
        "NACOS_ENABLED": "true",
        "NACOS_SERVER_ADDR": "nacos:8848",
        "NACOS_NAMESPACE": "public",
        "NACOS_GROUP": "DEFAULT_GROUP",
        "GATEWAY_ALLOWED_ORIGINS": origins,
        "GATEWAY_AUTH_ENABLED": "true",
        "GATEWAY_JWT_SECRET": jwt_secret,
        "GATEWAY_PRINT_COMPAT_PATHS": "/api/**",
        "GATEWAY_RATE_LIMIT_ENABLED": "false",
        "GATEWAY_PHOTO_CB_TIMEOUT": "30s",
        "GATEWAY_SCHEDULE_CB_TIMEOUT": "20s",
        "GATEWAY_CHAT_CB_TIMEOUT": "90s",
        "CHAT_DATABASE": "chatbot_db",
        "CHAT_DATABASE_URL": f"mysql+pymysql://print_shop:{db_password}@mysql:3306/chatbot_db?charset=utf8mb4",
        "CHAT_AUTH_ENABLED": "true",
        "CHAT_JWT_SECRET": jwt_secret,
        "CHAT_SEED_DEFAULTS": "true",
        "CHAT_VECTOR_ENABLED": "true",
        "CHAT_VECTOR_REQUIRED": "true",
        "CHAT_INGESTION_INLINE": "false",
        "DEEPSEEK_API_KEY": "",
        "IMAGE_API_BASE_URL": "https://tokenhub.tencentmaas.com/v1",
        "IMAGE_API_KEY": "",
        "IMAGE_API_TIMEOUT_SECONDS": "60",
        "IMAGE_MODEL": "seedream-image-v5.0-lite",
        "SCHEDULE_DATABASE": "schedule_db",
        "SCHEDULE_DATASOURCE_URL": "jdbc:mysql://mysql:3306/schedule_db?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
        "SCHEDULE_DATASOURCE_USERNAME": "print_shop",
        "SCHEDULE_DATASOURCE_PASSWORD": db_password,
        "SCHEDULE_FIELD_ENCRYPTION_KEY": field_key,
        "SCHEDULE_AUTH_ENABLED": "true",
        "SCHEDULE_JWT_SECRET": jwt_secret,
        "SCHEDULE_SYNC_MOCK_ENABLED": "true",
        "SCHEDULE_SYNC_HTTP_ENABLED": "false",
        "SCHEDULE_SYNC_HTTP_ENDPOINT_URL": "",
        "SCHEDULE_SYNC_HTTP_BEARER_TOKEN": "",
        "NGINX_SERVER_NAME": "localhost",
    }


def render_env(template_path: Path, replacements: dict[str, str]) -> str:
    lines: list[str] = []
    for raw in template_path.read_text(encoding="utf-8").splitlines():
        if "=" not in raw or raw.lstrip().startswith("#"):
            lines.append(raw)
            continue
        key, _ = raw.split("=", 1)
        key = key.strip()
        if key in replacements:
            lines.append(f"{key}={replacements[key]}")
        else:
            lines.append(raw)
    return "\n".join(lines) + "\n"


def secret(bytes_count: int) -> str:
    return secrets.token_urlsafe(bytes_count)


if __name__ == "__main__":
    sys.exit(main())
