#!/usr/bin/env python3
"""
Check production deployment prerequisites before starting docker-compose.nginx.yml.

The script intentionally fails for .env.example because that file contains
placeholders. Run it against the real production .env:

  python scripts/production_readiness_check.py --env .env
"""

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path


def configure_output_encoding() -> None:
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure:
            reconfigure(encoding="utf-8")


PLACEHOLDER_MARKERS = (
    "replace-with",
    "your-domain.example",
    "example.com",
    "changeme",
    "change-me",
)

REQUIRED_NON_PLACEHOLDER = (
    "MYSQL_ROOT_PASSWORD",
    "MYSQL_PASSWORD",
    "PRINTSHOP_DATASOURCE_PASSWORD",
    "PRINTSHOP_JWT_SECRET",
    "PRINTSHOP_FIELD_ENCRYPTION_KEY",
    "GATEWAY_JWT_SECRET",
    "SCHEDULE_FIELD_ENCRYPTION_KEY",
    "SCHEDULE_JWT_SECRET",
    "CHAT_JWT_SECRET",
)

SECRET_MIN_LENGTH = (
    "PRINTSHOP_JWT_SECRET",
    "PRINTSHOP_FIELD_ENCRYPTION_KEY",
    "GATEWAY_JWT_SECRET",
    "SCHEDULE_FIELD_ENCRYPTION_KEY",
    "SCHEDULE_JWT_SECRET",
    "CHAT_JWT_SECRET",
)

TRUE_REQUIRED = (
    "PRINTSHOP_REQUIRE_HTTPS",
    "GATEWAY_AUTH_ENABLED",
    "SCHEDULE_AUTH_ENABLED",
    "CHAT_AUTH_ENABLED",
)

ADMIN_USERNAME_PATTERN = re.compile(r"^[A-Za-z0-9_@.-]{3,50}$")


@dataclass
class Finding:
    level: str
    check: str
    message: str


def main() -> int:
    configure_output_encoding()

    parser = argparse.ArgumentParser(description="Validate production readiness for the smart print backend.")
    parser.add_argument("--env", default=".env", help="Path to production .env file.")
    parser.add_argument("--backend-dir", default="", help="Backend directory; defaults to the parent of scripts/.")
    parser.add_argument("--json", action="store_true", help="Print JSON instead of text.")
    args = parser.parse_args()

    script_backend = Path(__file__).resolve().parents[1]
    backend_dir = Path(args.backend_dir).resolve() if args.backend_dir else script_backend
    env_path = (backend_dir / args.env).resolve() if not Path(args.env).is_absolute() else Path(args.env)

    findings: list[Finding] = []
    env = load_env(env_path, findings)
    if env:
        run_checks(backend_dir, env_path, env, findings)

    if args.json:
        print_json(env_path, findings)
    else:
        print_text(env_path, findings)

    return 1 if any(f.level == "FAIL" for f in findings) else 0


def load_env(env_path: Path, findings: list[Finding]) -> dict[str, str]:
    if not env_path.is_file():
        findings.append(Finding("FAIL", "env", f"environment file not found: {env_path}"))
        return {}

    env: dict[str, str] = {}
    for line_number, raw_line in enumerate(env_path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export "):].strip()
        if "=" not in line:
            findings.append(Finding("FAIL", "env", f"invalid .env line {line_number}: missing '='"))
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = strip_quotes(value.strip())
        if not key:
            findings.append(Finding("FAIL", "env", f"invalid .env line {line_number}: empty key"))
            continue
        env[key] = value
    return env


def run_checks(backend_dir: Path, env_path: Path, env: dict[str, str], findings: list[Finding]) -> None:
    check_required_values(env, findings)
    check_secret_lengths(env, findings)
    check_secret_consistency(env, findings)
    check_security_switches(env, findings)
    check_admin_bootstrap(env, findings)
    check_domain_config(env, findings)
    check_gateway_compat(env, findings)
    check_optional_integrations(env, findings)
    check_filesystem_artifacts(backend_dir, env, findings)
    check_compose_files(backend_dir, findings)
    findings.append(Finding("PASS", "env", f"parsed {len(env)} variables from {env_path.name}"))


def check_required_values(env: dict[str, str], findings: list[Finding]) -> None:
    for key, value in env.items():
        if value and any(marker in value.lower() for marker in PLACEHOLDER_MARKERS):
            findings.append(Finding("FAIL", key, "contains a placeholder marker"))
    for key in REQUIRED_NON_PLACEHOLDER:
        value = env.get(key, "")
        if not value.strip():
            findings.append(Finding("FAIL", key, "missing required value"))


def check_secret_lengths(env: dict[str, str], findings: list[Finding]) -> None:
    for key in SECRET_MIN_LENGTH:
        value = env.get(key, "")
        if value and not is_placeholder(value) and len(value) < 32:
            findings.append(Finding("FAIL", key, "must be at least 32 characters"))


def check_secret_consistency(env: dict[str, str], findings: list[Finding]) -> None:
    print_secret = env.get("PRINTSHOP_JWT_SECRET", "")
    for key in ("GATEWAY_JWT_SECRET", "SCHEDULE_JWT_SECRET", "CHAT_JWT_SECRET"):
        value = env.get(key, "")
        if value and print_secret and not is_placeholder(value) and not is_placeholder(print_secret) and value != print_secret:
            findings.append(Finding("FAIL", key, "must match PRINTSHOP_JWT_SECRET"))

    mysql_password = env.get("MYSQL_PASSWORD", "")
    for key in ("PRINTSHOP_DATASOURCE_PASSWORD", "SCHEDULE_DATASOURCE_PASSWORD"):
        value = env.get(key, "")
        if value and mysql_password and not is_placeholder(value) and not is_placeholder(mysql_password) and value != mysql_password:
            findings.append(Finding("WARN", key, "differs from MYSQL_PASSWORD; confirm this is intentional"))


def check_security_switches(env: dict[str, str], findings: list[Finding]) -> None:
    for key in TRUE_REQUIRED:
        if env.get(key, "").lower() != "true":
            findings.append(Finding("FAIL", key, "must be true in production"))

    if env.get("PRINTSHOP_RATE_LIMIT_ENABLED", "").lower() != "true":
        findings.append(Finding("WARN", "PRINTSHOP_RATE_LIMIT_ENABLED", "backend rate limiting is not enabled"))
    if env.get("GATEWAY_RATE_LIMIT_ENABLED", "").lower() != "true":
        findings.append(Finding("WARN", "GATEWAY_RATE_LIMIT_ENABLED", "gateway rate limiting is not enabled"))
    if env.get("PRINTSHOP_VIRUS_SCAN_ENABLED", "").lower() != "true":
        findings.append(Finding("WARN", "PRINTSHOP_VIRUS_SCAN_ENABLED", "virus scanning is not enabled"))
    if env.get("PRINTSHOP_VIRUS_SCAN_FAIL_CLOSED", "").lower() != "true":
        findings.append(Finding("WARN", "PRINTSHOP_VIRUS_SCAN_FAIL_CLOSED", "virus scanner failures will not fail closed"))


def check_admin_bootstrap(env: dict[str, str], findings: list[Finding]) -> None:
    enabled = env.get("PRINTSHOP_BOOTSTRAP_ADMIN_ENABLED", "").lower() == "true"
    username = env.get("PRINTSHOP_BOOTSTRAP_ADMIN_USERNAME", "").strip()
    password = env.get("PRINTSHOP_BOOTSTRAP_ADMIN_PASSWORD", "")
    has_credentials = bool(username or password.strip())

    if enabled:
        if not username:
            findings.append(Finding("FAIL", "PRINTSHOP_BOOTSTRAP_ADMIN_USERNAME", "required when bootstrap admin is enabled"))
        elif not ADMIN_USERNAME_PATTERN.fullmatch(username):
            findings.append(Finding("FAIL", "PRINTSHOP_BOOTSTRAP_ADMIN_USERNAME", "must match print-service admin username rules"))

        if not password.strip():
            findings.append(Finding("FAIL", "PRINTSHOP_BOOTSTRAP_ADMIN_PASSWORD", "required when bootstrap admin is enabled"))
        elif not is_strong_admin_password(password):
            findings.append(Finding("FAIL", "PRINTSHOP_BOOTSTRAP_ADMIN_PASSWORD", "must be 8-72 chars, contain at least two of letters/digits/symbols, and contain no whitespace"))

        findings.append(Finding("WARN", "PRINTSHOP_BOOTSTRAP_ADMIN_ENABLED", "one-time superadmin bootstrap is enabled; disable it after first successful login"))
    elif has_credentials:
        findings.append(Finding("WARN", "PRINTSHOP_BOOTSTRAP_ADMIN_USERNAME/PASSWORD", "bootstrap admin credentials are present while bootstrap is disabled; remove them after first admin creation"))


def check_domain_config(env: dict[str, str], findings: list[Finding]) -> None:
    server_name = env.get("NGINX_SERVER_NAME", "")
    if is_placeholder(server_name):
        findings.append(Finding("FAIL", "NGINX_SERVER_NAME", "must be a real production domain"))

    for key in ("PRINTSHOP_ALLOWED_ORIGINS", "GATEWAY_ALLOWED_ORIGINS"):
        value = env.get(key, "")
        if is_placeholder(value):
            findings.append(Finding("FAIL", key, "must be set to real production origin(s)"))
        if "*" in value:
            findings.append(Finding("FAIL", key, "must not contain wildcard origins in production"))
        if "localhost" in value or "127.0.0.1" in value:
            findings.append(Finding("WARN", key, "contains a local development origin"))


def check_gateway_compat(env: dict[str, str], findings: list[Finding]) -> None:
    compat_paths = env.get("GATEWAY_PRINT_COMPAT_PATHS", "/api/**").strip()
    if compat_paths == "/api/**":
        findings.append(Finding("WARN", "GATEWAY_PRINT_COMPAT_PATHS", "legacy print compatibility route is still fully open"))
    elif not compat_paths:
        findings.append(Finding("FAIL", "GATEWAY_PRINT_COMPAT_PATHS", "must not be empty; use a sentinel path to disable"))
    else:
        findings.append(Finding("PASS", "GATEWAY_PRINT_COMPAT_PATHS", f"compatibility route narrowed to {compat_paths}"))


def check_optional_integrations(env: dict[str, str], findings: list[Finding]) -> None:
    if not env.get("DEEPSEEK_API_KEY", "").strip():
        findings.append(Finding("WARN", "DEEPSEEK_API_KEY", "chat-service will use local knowledge-base fallback only"))

    if env.get("CHAT_VECTOR_ENABLED", "").lower() != "true":
        findings.append(Finding("FAIL", "CHAT_VECTOR_ENABLED", "must be true for production Markdown semantic retrieval"))
    if env.get("CHAT_VECTOR_REQUIRED", "").lower() != "true":
        findings.append(Finding("FAIL", "CHAT_VECTOR_REQUIRED", "must fail closed when production vector indexing is unavailable"))
    if env.get("CHAT_INGESTION_INLINE", "").lower() == "true":
        findings.append(Finding("FAIL", "CHAT_INGESTION_INLINE", "production ingestion must run in the Celery worker"))
    for key in ("CHAT_QDRANT_URL", "CHAT_EMBEDDING_MODEL"):
        if not env.get(key, "").strip():
            findings.append(Finding("FAIL", key, "required for production Markdown knowledge indexing"))

    if env.get("SCHEDULE_SYNC_MOCK_ENABLED", "").lower() == "true":
        findings.append(Finding("FAIL", "SCHEDULE_SYNC_MOCK_ENABLED", "mock schedule sync must stay false in production"))

    if env.get("SCHEDULE_SYNC_HTTP_ENABLED", "").lower() == "true":
        for key in ("SCHEDULE_SYNC_HTTP_ENDPOINT_URL", "SCHEDULE_SYNC_HTTP_BEARER_TOKEN"):
            if not env.get(key, "").strip():
                findings.append(Finding("FAIL", key, "required when SCHEDULE_SYNC_HTTP_ENABLED=true"))
    else:
        findings.append(Finding("WARN", "SCHEDULE_SYNC_HTTP_ENABLED", "real schedule sync adapter is not enabled"))

    if env.get("PRINTSHOP_IMAGE_MODERATION_ENABLED", "").lower() == "true":
        for key in ("PRINTSHOP_IMAGE_MODERATION_ENDPOINT_URL", "PRINTSHOP_IMAGE_MODERATION_API_KEY"):
            if not env.get(key, "").strip():
                findings.append(Finding("FAIL", key, "required when PRINTSHOP_IMAGE_MODERATION_ENABLED=true"))

    ocr_has_any = bool(env.get("BAIDU_OCR_API_KEY", "").strip() or env.get("BAIDU_OCR_SECRET_KEY", "").strip())
    ocr_has_both = bool(env.get("BAIDU_OCR_API_KEY", "").strip() and env.get("BAIDU_OCR_SECRET_KEY", "").strip())
    if ocr_has_any and not ocr_has_both:
        findings.append(Finding("FAIL", "BAIDU_OCR_API_KEY/BAIDU_OCR_SECRET_KEY", "configure both Baidu OCR credentials or leave both empty"))
    if not ocr_has_both:
        findings.append(Finding("WARN", "BAIDU_OCR_API_KEY/BAIDU_OCR_SECRET_KEY", "Baidu OCR image-to-document conversion is not configured"))


def check_filesystem_artifacts(backend_dir: Path, env: dict[str, str], findings: list[Finding]) -> None:
    web_dist = backend_dir.parent / "printshop-web" / "dist" / "index.html"
    check_file(web_dist, "web-dist", findings, fail=True)

    photo_model = resolve_photo_model(backend_dir, env.get("PHOTO_MODEL_PATH", "pretrained/modnet.onnx"))
    check_file(photo_model, "PHOTO_MODEL_PATH", findings, fail=True)

    cert_dir = resolve_path(backend_dir, env.get("NGINX_CERT_DIR", "./nginx/certs"))
    cert_file = cert_dir / basename(env.get("NGINX_CERTIFICATE", "/etc/nginx/certs/fullchain.pem"))
    key_file = cert_dir / basename(env.get("NGINX_CERTIFICATE_KEY", "/etc/nginx/certs/privkey.pem"))
    check_file(cert_file, "NGINX_CERTIFICATE", findings, fail=True)
    check_file(key_file, "NGINX_CERTIFICATE_KEY", findings, fail=True)

    check_file(backend_dir / "printshop" / "target" / "printshop-0.0.1-SNAPSHOT.jar", "print-service jar", findings, fail=False)
    check_file(backend_dir / "gateway" / "target" / "printshop-gateway-0.0.1-SNAPSHOT.jar", "gateway jar", findings, fail=False)


def check_compose_files(backend_dir: Path, findings: list[Finding]) -> None:
    for name in ("docker-compose.yml", "docker-compose.middleware.yml", "docker-compose.nginx.yml", "nginx/templates/default.conf.template"):
        check_file(backend_dir / name, name, findings, fail=True)
    base_compose = backend_dir / "docker-compose.yml"
    middleware_compose = backend_dir / "docker-compose.middleware.yml"
    if base_compose.is_file() and "chat-worker:" not in base_compose.read_text(encoding="utf-8"):
        findings.append(Finding("FAIL", "chat-worker", "docker-compose.yml does not define the ingestion worker"))
    if middleware_compose.is_file() and "qdrant:" not in middleware_compose.read_text(encoding="utf-8"):
        findings.append(Finding("FAIL", "qdrant", "docker-compose.middleware.yml does not define Qdrant"))


def check_file(path: Path, check: str, findings: list[Finding], fail: bool) -> None:
    if path.is_file():
        findings.append(Finding("PASS", check, f"found {path}"))
    else:
        level = "FAIL" if fail else "WARN"
        findings.append(Finding(level, check, f"missing {path}"))


def resolve_photo_model(backend_dir: Path, value: str) -> Path:
    path = Path(value)
    if path.is_absolute():
        return path
    return backend_dir / "photo-service" / path


def resolve_path(base: Path, value: str) -> Path:
    path = Path(value)
    if path.is_absolute():
        return path
    return (base / path).resolve()


def basename(value: str) -> str:
    normalized = value.replace("\\", "/").rstrip("/")
    return normalized.rsplit("/", 1)[-1]


def strip_quotes(value: str) -> str:
    if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
        return value[1:-1]
    return value


def is_placeholder(value: str) -> bool:
    normalized = value.strip().lower()
    if not normalized:
        return True
    return any(marker in normalized for marker in PLACEHOLDER_MARKERS)


def is_strong_admin_password(value: str) -> bool:
    if len(value) < 8 or len(value) > 72:
        return False
    if any(ch.isspace() for ch in value):
        return False
    has_letter = any(ch.isalpha() for ch in value)
    has_digit = any(ch.isdigit() for ch in value)
    has_symbol = any((not ch.isalnum()) and (not ch.isspace()) for ch in value)
    return sum((has_letter, has_digit, has_symbol)) >= 2


def print_text(env_path: Path, findings: list[Finding]) -> None:
    order = {"FAIL": 0, "WARN": 1, "PASS": 2}
    for finding in sorted(findings, key=lambda item: (order.get(item.level, 99), item.check)):
        print(f"[{finding.level}] {finding.check}: {finding.message}")
    counts = {level: sum(1 for finding in findings if finding.level == level) for level in ("FAIL", "WARN", "PASS")}
    print(f"summary: env={env_path} fail={counts['FAIL']} warn={counts['WARN']} pass={counts['PASS']}")


def print_json(env_path: Path, findings: list[Finding]) -> None:
    payload = {
        "env": str(env_path),
        "summary": {level.lower(): sum(1 for finding in findings if finding.level == level) for level in ("FAIL", "WARN", "PASS")},
        "findings": [finding.__dict__ for finding in findings],
    }
    print(json.dumps(payload, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    sys.exit(main())
