#!/usr/bin/env python3
"""
Generate a production .env draft from .env.example.

The generated file is a draft: real external values such as WeChat, TLS
certificates, DeepSeek and school schedule adapter credentials still need to be
provided by deployment operators and verified with production_readiness_check.py.
"""

import argparse
import secrets
import sys
from dataclasses import dataclass
from pathlib import Path


def configure_output_encoding() -> None:
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure:
            reconfigure(encoding="utf-8")


@dataclass(frozen=True)
class EnvLine:
    raw: str
    key: str = ""
    value: str = ""


def main() -> int:
    configure_output_encoding()
    parser = argparse.ArgumentParser(description="Generate a production .env draft for the smart print backend.")
    parser.add_argument("--template", default="", help="Template env file. Defaults to backend .env.example.")
    parser.add_argument("--output", default=".env.production.local", help="Output env path relative to backend dir, or '-' for stdout.")
    parser.add_argument("--force", action="store_true", help="Overwrite output file if it already exists.")
    parser.add_argument("--domain", default="", help="Production domain, for example www.guangxun.ltd.")
    parser.add_argument("--allowed-origins", default="", help="Allowed origins. Defaults to https://<domain> when --domain is set.")
    parser.add_argument("--tencent-map-key", default="", help="Tencent map browser key for admin build.")
    parser.add_argument("--deepseek-api-key", default="", help="DeepSeek API key for chat-service.")
    parser.add_argument("--schedule-sync-url", default="", help="HTTP JSON schedule adapter endpoint URL.")
    parser.add_argument("--schedule-sync-token", default="", help="Bearer token for the HTTP JSON schedule adapter.")
    parser.add_argument("--enable-schedule-sync", action="store_true", help="Set SCHEDULE_SYNC_HTTP_ENABLED=true.")
    parser.add_argument("--image-moderation-url", default="", help="Image moderation endpoint URL.")
    parser.add_argument("--image-moderation-key", default="", help="Image moderation API key.")
    parser.add_argument("--enable-image-moderation", action="store_true", help="Set PRINTSHOP_IMAGE_MODERATION_ENABLED=true.")
    parser.add_argument(
        "--compat-mode",
        default="full",
        choices=["full", "public-files", "disabled"],
        help="Initial GATEWAY_PRINT_COMPAT_PATHS mode.",
    )
    args = parser.parse_args()

    backend_dir = Path(__file__).resolve().parents[1]
    template_path = Path(args.template).resolve() if args.template else backend_dir / ".env.example"
    if not template_path.is_file():
        raise SystemExit(f"template env file not found: {template_path}")

    output_path = resolve_output_path(backend_dir, args.output)
    if output_path and output_path.exists() and not args.force:
        raise SystemExit(f"output exists: {output_path}; pass --force to overwrite")

    lines = parse_template(template_path)
    replacements = build_replacements(args)
    rendered, changed = render(lines, replacements)

    if output_path is None:
        print(rendered, end="")
    else:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(rendered, encoding="utf-8")
        print(f"generated: {output_path}")
    print_summary(args, template_path, changed, output_path, sys.stderr if output_path is None else sys.stdout)
    return 0


def resolve_output_path(backend_dir: Path, output: str) -> Path | None:
    if output == "-":
        return None
    path = Path(output)
    if not path.is_absolute():
        path = backend_dir / path
    return path.resolve()


def parse_template(path: Path) -> list[EnvLine]:
    lines: list[EnvLine] = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        stripped = raw.strip()
        if not stripped or stripped.startswith("#") or "=" not in raw:
            lines.append(EnvLine(raw=raw))
            continue
        key, value = raw.split("=", 1)
        lines.append(EnvLine(raw=raw, key=key.strip(), value=value.strip()))
    return lines


def build_replacements(args) -> dict[str, str]:
    db_password = random_secret()
    jwt_secret = random_secret(48)
    domain = normalize_domain(args.domain)
    origin = args.allowed_origins.strip() or (f"https://{domain}" if domain else "")
    schedule_enabled = args.enable_schedule_sync or bool(args.schedule_sync_url or args.schedule_sync_token)
    moderation_enabled = args.enable_image_moderation or bool(args.image_moderation_url or args.image_moderation_key)

    compat_paths = {
        "full": "/api/**",
        "public-files": "/api/public-files/**",
        "disabled": "/__disabled-print-compat/**",
    }[args.compat_mode]

    replacements = {
        "MYSQL_ROOT_PASSWORD": random_secret(),
        "MYSQL_PASSWORD": db_password,
        "PRINTSHOP_DATASOURCE_PASSWORD": db_password,
        "CHAT_DATABASE_URL": f"mysql+pymysql://print_shop:{db_password}@mysql:3306/chatbot_db?charset=utf8mb4",
        "SCHEDULE_DATASOURCE_PASSWORD": db_password,
        "PRINTSHOP_JWT_SECRET": jwt_secret,
        "GATEWAY_JWT_SECRET": jwt_secret,
        "CHAT_JWT_SECRET": jwt_secret,
        "SCHEDULE_JWT_SECRET": jwt_secret,
        "PRINTSHOP_FIELD_ENCRYPTION_KEY": random_secret(48),
        "SCHEDULE_FIELD_ENCRYPTION_KEY": random_secret(48),
        "GATEWAY_PRINT_COMPAT_PATHS": compat_paths,
        "SCHEDULE_SYNC_HTTP_ENABLED": "true" if schedule_enabled else "false",
        "SCHEDULE_SYNC_HTTP_ENDPOINT_URL": args.schedule_sync_url.strip() if schedule_enabled and args.schedule_sync_url.strip() else ("replace-with-schedule-sync-endpoint" if schedule_enabled else ""),
        "SCHEDULE_SYNC_HTTP_BEARER_TOKEN": args.schedule_sync_token.strip() if schedule_enabled and args.schedule_sync_token.strip() else ("replace-with-schedule-sync-token" if schedule_enabled else ""),
        "PRINTSHOP_IMAGE_MODERATION_ENABLED": "true" if moderation_enabled else "false",
        "PRINTSHOP_IMAGE_MODERATION_ENDPOINT_URL": args.image_moderation_url.strip() if moderation_enabled and args.image_moderation_url.strip() else ("replace-with-image-moderation-endpoint" if moderation_enabled else ""),
        "PRINTSHOP_IMAGE_MODERATION_API_KEY": args.image_moderation_key.strip() if moderation_enabled and args.image_moderation_key.strip() else ("replace-with-image-moderation-api-key" if moderation_enabled else ""),
    }
    if origin:
        replacements["PRINTSHOP_ALLOWED_ORIGINS"] = origin
        replacements["GATEWAY_ALLOWED_ORIGINS"] = origin
    if domain:
        replacements["NGINX_SERVER_NAME"] = domain
    optional_values = {
        "VITE_TENCENT_MAP_KEY": args.tencent_map_key,
        "DEEPSEEK_API_KEY": args.deepseek_api_key,
    }
    for key, value in optional_values.items():
        if value.strip():
            replacements[key] = value.strip()
    return replacements


def render(lines: list[EnvLine], replacements: dict[str, str]) -> tuple[str, int]:
    rendered: list[str] = []
    changed = 0
    for line in lines:
        if line.key and line.key in replacements:
            rendered.append(f"{line.key}={replacements[line.key]}")
            changed += 1
        else:
            rendered.append(line.raw)
    return "\n".join(rendered) + "\n", changed


def random_secret(bytes_count: int = 32) -> str:
    return secrets.token_urlsafe(bytes_count)


def normalize_domain(value: str) -> str:
    domain = value.strip()
    if domain.startswith("http://"):
        domain = domain[len("http://"):]
    if domain.startswith("https://"):
        domain = domain[len("https://"):]
    return domain.strip("/")


def print_summary(args, template_path: Path, changed: int, output_path: Path | None, stream) -> None:
    unresolved = []
    if not args.domain.strip():
        unresolved.extend(["NGINX_SERVER_NAME", "PRINTSHOP_ALLOWED_ORIGINS", "GATEWAY_ALLOWED_ORIGINS"])
    print(f"template: {template_path}", file=stream)
    print(f"replacedVariables: {changed}", file=stream)
    print(f"compatMode: {args.compat_mode}", file=stream)
    if output_path is not None:
        print("next: review the generated file, rename/copy it to .env on the server, then run production_readiness_check.py", file=stream)
    if unresolved:
        print("stillRequired: " + ", ".join(unresolved), file=stream)


if __name__ == "__main__":
    sys.exit(main())
