#!/usr/bin/env python3
"""
Smoke test the production Nginx/Gateway entrypoint after deployment.

The script uses only the Python standard library. For a full production check,
pass either a real user token or the shared JWT secret from the production .env:

  python scripts/production_smoke_test.py --base-url https://example.com --env .env
  python scripts/production_smoke_test.py --base-url https://example.com --token <user-jwt>
"""

import argparse
import base64
import binascii
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass
from pathlib import Path


def configure_output_encoding() -> None:
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure:
            reconfigure(encoding="utf-8")


@dataclass
class HttpResult:
    status: int
    body: bytes
    elapsed: float
    error: str = ""


@dataclass
class SmokeResult:
    level: str
    check: str
    status: int
    elapsed: float
    message: str


def main() -> int:
    configure_output_encoding()
    parser = argparse.ArgumentParser(description="Smoke test the smart print production Gateway entrypoint.")
    parser.add_argument("--base-url", default=os.getenv("SMOKE_BASE_URL", ""), help="Production base URL, for example https://www.example.com.")
    parser.add_argument("--env", default="", help="Optional .env file used to read GATEWAY_JWT_SECRET/PRINTSHOP_JWT_SECRET.")
    parser.add_argument("--token", default="", help="Prebuilt Bearer token for authenticated checks.")
    parser.add_argument("--jwt-secret", default="", help="JWT secret used to generate a temporary user token.")
    parser.add_argument("--user-id", type=int, default=1, help="User id claim for generated tokens.")
    parser.add_argument("--timeout", type=float, default=15.0, help="Per-request timeout seconds.")
    parser.add_argument("--question", default="证件照怎么换底色", help="Question used for /api/chat/ask.")
    parser.add_argument("--expect-chat-source", default="any", choices=["any", "knowledge", "deepseek", "fallback"], help="Expected chat answer source.")
    parser.add_argument("--admin-username", default=os.getenv("SMOKE_ADMIN_USERNAME", ""), help="Optional admin username used to verify /api/print/admin/login.")
    parser.add_argument("--admin-password", default=os.getenv("SMOKE_ADMIN_PASSWORD", ""), help="Optional admin password used to verify /api/print/admin/login.")
    parser.add_argument("--expect-admin-role", default="any", choices=["any", "superadmin", "admin", "operator"], help="Expected role for the optional admin login check.")
    parser.add_argument("--skip-chat-ask", action="store_true", help="Skip /api/chat/ask; /api/chat/health is still checked.")
    parser.add_argument("--skip-authenticated", action="store_true", help="Only run anonymous public-route checks.")
    parser.add_argument("--photo-image", default="", help="Optional real face sample used to smoke test photo inference.")
    parser.add_argument("--photo-endpoint", default="/api/photo/change-background", choices=["/api/photo/change-background", "/api/photo/generate-id-photo"], help="Photo inference endpoint when --photo-image is set.")
    parser.add_argument("--schedule-sync", default="skip", choices=["skip", "expect-ready", "expect-unconfigured"], help="Optionally smoke test POST /api/schedule/sync.")
    parser.add_argument("--schedule-student-id", default=os.getenv("SMOKE_SCHEDULE_STUDENT_ID", ""), help="Optional student id used to bind a real schedule account before sync.")
    parser.add_argument("--schedule-jw-username", default=os.getenv("SMOKE_SCHEDULE_JW_USERNAME", ""), help="Optional school system username used for /api/schedule/bind.")
    parser.add_argument("--schedule-jw-password", default=os.getenv("SMOKE_SCHEDULE_JW_PASSWORD", ""), help="Optional school system password used for /api/schedule/bind.")
    parser.add_argument("--schedule-xnm", default="", help="Optional school year passed to /api/schedule/sync, for example 2025-2026.")
    parser.add_argument("--schedule-xqm", default="", help="Optional term passed to /api/schedule/sync, for example 1.")
    parser.add_argument("--output", default="", help="Optional JSON report path.")
    args = parser.parse_args()

    if not args.base_url.strip():
        raise SystemExit("--base-url is required, for example --base-url https://www.example.com")
    if args.timeout <= 0:
        raise SystemExit("--timeout must be positive")
    if args.user_id <= 0:
        raise SystemExit("--user-id must be positive")
    if args.photo_image and not Path(args.photo_image).is_file():
        raise SystemExit(f"photo image not found: {args.photo_image}")

    env = load_env(Path(args.env)) if args.env else {}
    token = resolve_token(args, env)
    results: list[SmokeResult] = []

    run_public_checks(args, results)
    run_admin_login_check(args, results)
    if args.skip_authenticated:
        results.append(SmokeResult("SKIP", "authenticated checks", 0, 0, "skipped by --skip-authenticated"))
    elif not token:
        results.append(SmokeResult("FAIL", "authenticated checks", 0, 0, "provide --token, --jwt-secret, or --env with GATEWAY_JWT_SECRET/PRINTSHOP_JWT_SECRET"))
    else:
        run_authenticated_checks(args, token, results)

    print_report(args, results)
    return 1 if any(result.level == "FAIL" for result in results) else 0


def run_public_checks(args, results: list[SmokeResult]) -> None:
    expect_status(args, results, "print public services", "GET", "/api/print/service/", None, expect_2xx=True)
    expect_status(args, results, "print active stores", "GET", "/api/print/store/active", None, expect_2xx=True)
    expect_status(args, results, "gateway auth guard", "GET", "/api/print/order/1", None, expected={401})
    expect_status(args, results, "photo route auth guard", "GET", "/api/photo/health", None, expected={401})
    expect_status(args, results, "chat route auth guard", "GET", "/api/chat/health", None, expected={401})


def run_authenticated_checks(args, token: str, results: list[SmokeResult]) -> None:
    auth = {"Authorization": f"Bearer {token}"}
    expect_status(args, results, "photo health through gateway", "GET", "/api/photo/health", auth, expect_2xx=True)
    expect_status(args, results, "chat health through gateway", "GET", "/api/chat/health", auth, expect_2xx=True)
    expect_status(args, results, "schedule bind status", "GET", "/api/schedule/bind-status", auth, expect_2xx=True)
    run_schedule_bind_check(args, auth, results)
    if args.skip_chat_ask:
        results.append(SmokeResult("SKIP", "chat ask", 0, 0, "skipped by --skip-chat-ask"))
    else:
        run_chat_ask(args, auth, results)
    if args.photo_image:
        run_photo_inference(args, auth, results)
    else:
        results.append(SmokeResult("SKIP", "photo inference", 0, 0, "pass --photo-image to test real face inference"))
    if args.schedule_sync == "skip":
        results.append(SmokeResult("SKIP", "schedule sync", 0, 0, "pass --schedule-sync expect-ready or expect-unconfigured"))
    else:
        expected = {200} if args.schedule_sync == "expect-ready" else {503}
        run_json_case(args, results, "schedule sync", "POST", "/api/schedule/sync", auth, schedule_sync_body(args), expected=expected)


def run_chat_ask(args, headers: dict[str, str], results: list[SmokeResult]) -> None:
    started = time.perf_counter()
    http = send_json(args, "POST", "/api/chat/ask", headers, {"question": args.question, "sessionId": f"smoke-{uuid.uuid4().hex[:12]}"})
    elapsed = http.elapsed or time.perf_counter() - started
    if not 200 <= http.status < 300:
        results.append(SmokeResult("FAIL", "chat ask", http.status, elapsed, http.error or "expected 2xx"))
        return
    data = parse_json(http.body)
    source = str(data.get("source", ""))
    if args.expect_chat_source != "any" and source != args.expect_chat_source:
        results.append(SmokeResult("FAIL", "chat ask", http.status, elapsed, f"source={source!r}, expected {args.expect_chat_source!r}"))
        return
    results.append(SmokeResult("PASS", "chat ask", http.status, elapsed, f"source={source or 'unknown'}"))


def run_admin_login_check(args, results: list[SmokeResult]) -> None:
    username = args.admin_username.strip()
    password = args.admin_password
    if not username and not password:
        results.append(SmokeResult("SKIP", "admin login", 0, 0, "pass --admin-username and --admin-password to verify management login"))
        return
    if not username or not password:
        results.append(SmokeResult("FAIL", "admin login", 0, 0, "provide both --admin-username and --admin-password"))
        return

    http = send_json(args, "POST", "/api/print/admin/login", {}, {"username": username, "password": password})
    if not 200 <= http.status < 300:
        results.append(SmokeResult("FAIL", "admin login", http.status, http.elapsed, http.error or "expected 2xx"))
        return

    data = parse_json(http.body)
    if data.get("code") != 200 or not isinstance(data.get("data"), dict):
        results.append(SmokeResult("FAIL", "admin login", http.status, http.elapsed, "response missing code=200 data"))
        return

    payload = data["data"]
    admin = payload.get("admin") if isinstance(payload.get("admin"), dict) else {}
    token = str(payload.get("token", ""))
    role = str(admin.get("role", ""))
    if not token:
        results.append(SmokeResult("FAIL", "admin login", http.status, http.elapsed, "response missing admin token"))
        return
    if args.expect_admin_role != "any" and role != args.expect_admin_role:
        results.append(SmokeResult("FAIL", "admin login", http.status, http.elapsed, f"role={role!r}, expected {args.expect_admin_role!r}"))
        return

    results.append(SmokeResult("PASS", "admin login", http.status, http.elapsed, f"username={username} role={role or 'unknown'}"))
    expect_status(args, results, "admin list with login token", "GET", "/api/print/admin/", {"Authorization": f"Bearer {token}"}, expect_2xx=True)


def run_schedule_bind_check(args, headers: dict[str, str], results: list[SmokeResult]) -> None:
    student_id = args.schedule_student_id.strip()
    username = args.schedule_jw_username.strip()
    password = args.schedule_jw_password
    provided = [bool(student_id), bool(username), bool(password)]
    if not any(provided):
        results.append(SmokeResult("SKIP", "schedule bind", 0, 0, "pass --schedule-student-id, --schedule-jw-username and --schedule-jw-password to bind a real account"))
        return
    if not all(provided):
        results.append(SmokeResult("FAIL", "schedule bind", 0, 0, "provide schedule student id, username and password together"))
        return

    http = send_json(args, "POST", "/api/schedule/bind", headers, {
        "studentId": student_id,
        "jwUsername": username,
        "jwPassword": password,
    })
    if not 200 <= http.status < 300:
        results.append(SmokeResult("FAIL", "schedule bind", http.status, http.elapsed, http.error or "expected 2xx"))
        return
    data = parse_json(http.body)
    if data.get("jwPassword") is not None:
        results.append(SmokeResult("FAIL", "schedule bind", http.status, http.elapsed, "response exposed jwPassword"))
        return
    if data.get("bound") is not True:
        results.append(SmokeResult("FAIL", "schedule bind", http.status, http.elapsed, "response missing bound=true"))
        return
    results.append(SmokeResult("PASS", "schedule bind", http.status, http.elapsed, "bound=true passwordHidden=true"))


def run_photo_inference(args, headers: dict[str, str], results: list[SmokeResult]) -> None:
    fields = {"bg_color": "#FFFFFF"}
    if args.photo_endpoint.endswith("/generate-id-photo"):
        fields.update({"size": "1寸", "enhance": "true"})
    else:
        fields.update({"output_format": "png", "enhance": "true", "gradient_bg": "true", "smooth_edge": "true"})
    body, content_type = multipart_body(Path(args.photo_image), fields)
    request_headers = dict(headers)
    request_headers["Content-Type"] = content_type
    http = send_request(args, "POST", args.photo_endpoint, request_headers, body)
    if not 200 <= http.status < 300:
        results.append(SmokeResult("FAIL", "photo inference", http.status, http.elapsed, http.error or "expected 2xx"))
        return

    ok, message = validate_photo_payload(http.body)
    level = "PASS" if ok else "FAIL"
    results.append(SmokeResult(level, "photo inference", http.status, http.elapsed, f"{args.photo_endpoint} {message}"))


def run_json_case(args, results: list[SmokeResult], check: str, method: str, path: str, headers: dict[str, str], body: dict, expected: set[int]) -> None:
    http = send_json(args, method, path, headers, body)
    append_expected(results, check, http, expected=expected)


def schedule_sync_body(args) -> dict:
    body = {}
    if args.schedule_xnm.strip():
        body["xnm"] = args.schedule_xnm.strip()
    if args.schedule_xqm.strip():
        body["xqm"] = args.schedule_xqm.strip()
    return body


def expect_status(args, results: list[SmokeResult], check: str, method: str, path: str, headers: dict[str, str] | None, expected: set[int] | None = None, expect_2xx: bool = False) -> None:
    http = send_request(args, method, path, headers or {}, None)
    append_expected(results, check, http, expected=expected, expect_2xx=expect_2xx)


def append_expected(results: list[SmokeResult], check: str, http: HttpResult, expected: set[int] | None = None, expect_2xx: bool = False) -> None:
    if expect_2xx:
        ok = 200 <= http.status < 300
        expected_text = "2xx"
    else:
        expected = expected or set()
        ok = http.status in expected
        expected_text = "/".join(str(status) for status in sorted(expected))
    level = "PASS" if ok else "FAIL"
    message = f"expected {expected_text}" if not ok else "ok"
    if http.error and not ok:
        message = f"{message}; {http.error}"
    results.append(SmokeResult(level, check, http.status, http.elapsed, message))


def send_json(args, method: str, path: str, headers: dict[str, str], body: dict) -> HttpResult:
    request_headers = dict(headers)
    request_headers["Content-Type"] = "application/json"
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    return send_request(args, method, path, request_headers, data)


def send_request(args, method: str, path: str, headers: dict[str, str], body: bytes | None) -> HttpResult:
    url = args.base_url.rstrip("/") + "/" + path.lstrip("/")
    started = time.perf_counter()
    try:
        request = urllib.request.Request(url, data=body, headers=headers, method=method)
        with urllib.request.urlopen(request, timeout=args.timeout) as response:
            response_body = response.read()
            return HttpResult(response.status, response_body, time.perf_counter() - started)
    except urllib.error.HTTPError as exc:
        response_body = exc.read()
        return HttpResult(exc.code, response_body, time.perf_counter() - started, str(exc))
    except OSError as exc:
        return HttpResult(0, b"", time.perf_counter() - started, str(exc))


def multipart_body(image_path: Path, fields: dict[str, str]) -> tuple[bytes, str]:
    boundary = "----printshop-smoke-boundary"
    chunks: list[bytes] = []
    for name, value in fields.items():
        chunks.extend([
            f"--{boundary}\r\n".encode("utf-8"),
            f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"),
            str(value).encode("utf-8"),
            b"\r\n",
        ])
    chunks.extend([
        f"--{boundary}\r\n".encode("utf-8"),
        f'Content-Disposition: form-data; name="image"; filename="{image_path.name}"\r\n'.encode("utf-8"),
        b"Content-Type: image/jpeg\r\n\r\n",
        image_path.read_bytes(),
        b"\r\n",
        f"--{boundary}--\r\n".encode("utf-8"),
    ])
    return b"".join(chunks), f"multipart/form-data; boundary={boundary}"


def resolve_token(args, env: dict[str, str]) -> str:
    if args.token.strip():
        return args.token.strip()
    secret = (
        args.jwt_secret.strip()
        or env.get("GATEWAY_JWT_SECRET", "").strip()
        or env.get("PRINTSHOP_JWT_SECRET", "").strip()
        or os.getenv("GATEWAY_JWT_SECRET", "").strip()
        or os.getenv("PRINTSHOP_JWT_SECRET", "").strip()
    )
    if not secret:
        return ""
    return build_user_token(secret, args.user_id)


def build_user_token(secret: str, user_id: int) -> str:
    now = int(time.time())
    claims = {
        "typ": "JWT",
        "type": "user",
        "id": user_id,
        "sub": f"user:{user_id}",
        "role": "user",
        "username": f"smoke-user-{user_id}",
        "username": "smoke-user",
        "iat": now,
        "exp": now + 3600,
        "jti": str(uuid.uuid4()),
    }
    header = {"alg": "HS256", "typ": "JWT"}
    header_part = b64(json.dumps(header, separators=(",", ":")).encode("utf-8"))
    payload_part = b64(json.dumps(claims, separators=(",", ":")).encode("utf-8"))
    signed = f"{header_part}.{payload_part}".encode("utf-8")
    signature = b64(hmac.new(secret.encode("utf-8"), signed, hashlib.sha256).digest())
    return f"{header_part}.{payload_part}.{signature}"


def b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("utf-8").rstrip("=")


def load_env(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise SystemExit(f"env file not found: {path}")
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export "):].strip()
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = strip_quotes(value.strip())
    return values


def strip_quotes(value: str) -> str:
    if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
        return value[1:-1]
    return value


def parse_json(body: bytes) -> dict:
    try:
        parsed = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return {}
    return parsed if isinstance(parsed, dict) else {}


def validate_photo_payload(body: bytes) -> tuple[bool, str]:
    response = parse_json(body)
    if not response:
        return False, "response is not JSON object"
    if response.get("code") != 0:
        return False, f"response code={response.get('code')!r}, expected 0"

    payload = response.get("data")
    if not isinstance(payload, dict):
        return False, "response missing data object"

    image_value = payload.get("image")
    if not isinstance(image_value, str) or not image_value.strip():
        return False, "response missing data.image"

    encoded, declared_data_uri_format = normalize_base64_image(image_value)
    if not encoded:
        return False, "data.image is empty"
    try:
        decoded = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError):
        return False, "data.image is not valid base64"
    if len(decoded) < 12:
        return False, "decoded image is too small"

    actual_format = detect_image_format(decoded)
    if not actual_format:
        return False, "decoded image is not PNG or JPEG"

    declared_format = normalize_image_format(payload.get("format"))
    if declared_data_uri_format and declared_format and declared_data_uri_format != declared_format:
        return False, f"data URI format={declared_data_uri_format}, field format={declared_format}"
    expected_format = declared_format or declared_data_uri_format
    if expected_format and expected_format != actual_format:
        return False, f"format={expected_format}, bytes={actual_format}"

    width = payload.get("width")
    height = payload.get("height")
    if not isinstance(width, int) or width <= 0:
        return False, f"width={width!r}, expected positive integer"
    if not isinstance(height, int) or height <= 0:
        return False, f"height={height!r}, expected positive integer"

    return True, f"format={actual_format} size={width}x{height} bytes={len(decoded)}"


def normalize_base64_image(value: str) -> tuple[str, str]:
    stripped = value.strip()
    if not stripped.lower().startswith("data:"):
        return stripped, ""
    header, separator, encoded = stripped.partition(",")
    if not separator or ";base64" not in header.lower():
        return "", ""
    media_type = header[5:].split(";", 1)[0].lower()
    declared_format = ""
    if media_type == "image/png":
        declared_format = "png"
    elif media_type in ("image/jpeg", "image/jpg"):
        declared_format = "jpg"
    return encoded.strip(), declared_format


def normalize_image_format(value) -> str:
    if not isinstance(value, str):
        return ""
    normalized = value.strip().lower().lstrip(".")
    if normalized == "jpeg":
        return "jpg"
    if normalized in ("png", "jpg"):
        return normalized
    return normalized


def detect_image_format(data: bytes) -> str:
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if data.startswith(b"\xff\xd8\xff"):
        return "jpg"
    return ""


def print_report(args, results: list[SmokeResult]) -> None:
    for result in results:
        elapsed = f"{result.elapsed:.3f}s" if result.elapsed else "-"
        status = result.status if result.status else "-"
        print(f"[{result.level}] {result.check}: status={status} elapsed={elapsed} {result.message}")
    summary = {level: sum(1 for result in results if result.level == level) for level in ("FAIL", "SKIP", "PASS")}
    print(f"summary: baseUrl={args.base_url.rstrip('/')} fail={summary['FAIL']} skip={summary['SKIP']} pass={summary['PASS']}")
    if args.output:
        payload = {
            "baseUrl": args.base_url.rstrip("/"),
            "summary": {key.lower(): value for key, value in summary.items()},
            "results": [result.__dict__ for result in results],
        }
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    sys.exit(main())
