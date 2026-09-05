#!/usr/bin/env python3
"""
Simple chat-service pressure test using only the Python standard library.

Examples:
  python scripts/chat_pressure_test.py --base-url http://localhost:8093 --jwt-secret <secret>
  python scripts/chat_pressure_test.py --base-url http://localhost:8080 --jwt-secret <secret> --requests 200 --concurrency 20
  python scripts/chat_pressure_test.py --base-url http://localhost:8093 --endpoint /api/chat/health --requests 100 --concurrency 10
"""

import argparse
import base64
import hashlib
import hmac
import json
import os
import queue
import statistics
import threading
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass
from pathlib import Path


@dataclass
class Result:
    ok: bool
    status: int
    elapsed: float
    source: str = ""
    error: str = ""


def main():
    parser = argparse.ArgumentParser(description="Pressure test chat-service endpoints.")
    parser.add_argument("--base-url", default="http://localhost:8093", help="Service or Gateway base URL.")
    parser.add_argument("--endpoint", default="/api/chat/ask", help="Endpoint path.")
    parser.add_argument("--requests", type=int, default=100, help="Total request count.")
    parser.add_argument("--concurrency", type=int, default=10, help="Concurrent worker count.")
    parser.add_argument("--timeout", type=float, default=15.0, help="Per-request timeout seconds.")
    parser.add_argument("--token", default="", help="Prebuilt Bearer token.")
    parser.add_argument(
        "--jwt-secret",
        default=os.getenv("CHAT_JWT_SECRET", os.getenv("PRINTSHOP_JWT_SECRET", "")),
        help="JWT secret used to sign a compatible platform token.",
    )
    parser.add_argument("--token-type", default="user", choices=["user", "admin"], help="Generated token type.")
    parser.add_argument("--user-id", type=int, default=1, help="Generated token principal id.")
    parser.add_argument("--question", default="证件照怎么换底色", help="Question used for /api/chat/ask.")
    parser.add_argument("--session-prefix", default="pressure", help="Session id prefix for ask requests.")
    parser.add_argument("--output", default="", help="Optional JSON report output path.")
    args = parser.parse_args()

    if args.requests <= 0 or args.concurrency <= 0:
        raise SystemExit("--requests and --concurrency must be positive")
    args.resolved_token = args.token.strip() or build_token(args)

    jobs = queue.Queue()
    results = []
    lock = threading.Lock()
    for index in range(args.requests):
        jobs.put(index)

    started = time.perf_counter()
    workers = [
        threading.Thread(target=worker, args=(args, jobs, results, lock), daemon=True)
        for _ in range(min(args.concurrency, args.requests))
    ]
    for thread in workers:
        thread.start()
    for thread in workers:
        thread.join()
    elapsed = time.perf_counter() - started

    print_report(args, results, elapsed)


def worker(args, jobs, results, lock):
    while True:
        try:
            index = jobs.get_nowait()
        except queue.Empty:
            return
        result = send_request(args, index)
        with lock:
            results.append(result)
        jobs.task_done()


def send_request(args, index: int) -> Result:
    url = args.base_url.rstrip("/") + "/" + args.endpoint.lstrip("/")
    headers = {}
    if args.resolved_token:
        headers["Authorization"] = "Bearer " + args.resolved_token
    method = request_method(args.endpoint)
    body = None
    if method == "POST":
        body = json.dumps(
            {
                "question": args.question,
                "sessionId": f"{args.session_prefix}-{index}",
            },
            ensure_ascii=False,
        ).encode("utf-8")
        headers["Content-Type"] = "application/json"
    started = time.perf_counter()
    try:
        request = urllib.request.Request(url, data=body, headers=headers, method=method)
        with urllib.request.urlopen(request, timeout=args.timeout) as response:
            response_body = response.read()
            status = response.status
        source = parse_source(response_body)
        return Result(ok=200 <= status < 300, status=status, elapsed=time.perf_counter() - started, source=source)
    except urllib.error.HTTPError as exc:
        exc.read()
        return Result(ok=False, status=exc.code, elapsed=time.perf_counter() - started, error=str(exc))
    except OSError as exc:
        return Result(ok=False, status=0, elapsed=time.perf_counter() - started, error=str(exc))


def request_method(endpoint: str) -> str:
    if endpoint.rstrip("/").endswith("/ask"):
        return "POST"
    return "GET"


def build_token(args) -> str:
    secret = (args.jwt_secret or "").strip()
    if not secret:
        return ""
    now = int(time.time())
    token_type = args.token_type
    principal_id = max(args.user_id, 1)
    claims = {
        "typ": "JWT",
        "type": token_type,
        "id": principal_id,
        "iat": now,
        "exp": now + 3600,
        "jti": str(uuid.uuid4()),
        "sub": f"{token_type}:{principal_id}",
    }
    if token_type == "user":
        claims["role"] = "user"
        claims["username"] = f"pressure-user-{principal_id}"
    else:
        claims["role"] = "superadmin"
        claims["username"] = "pressure-admin"
    header = {"alg": "HS256", "typ": "JWT"}
    header_part = b64(json.dumps(header, separators=(",", ":")).encode("utf-8"))
    payload_part = b64(json.dumps(claims, separators=(",", ":")).encode("utf-8"))
    signed = f"{header_part}.{payload_part}".encode("utf-8")
    signature = b64(hmac.new(secret.encode("utf-8"), signed, hashlib.sha256).digest())
    return f"{header_part}.{payload_part}.{signature}"


def b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("utf-8").rstrip("=")


def parse_source(response_body: bytes) -> str:
    try:
        data = json.loads(response_body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return ""
    return str(data.get("source") or "")


def print_report(args, results, elapsed):
    total = len(results)
    ok = sum(1 for result in results if result.ok)
    failed = total - ok
    statuses = {}
    sources = {}
    durations = [result.elapsed for result in results]
    for result in results:
        statuses[result.status] = statuses.get(result.status, 0) + 1
        if result.source:
            sources[result.source] = sources.get(result.source, 0) + 1
    durations_sorted = sorted(durations)
    p95 = percentile(durations_sorted, 95)
    p99 = percentile(durations_sorted, 99)
    report = {
        "baseUrl": args.base_url,
        "endpoint": args.endpoint,
        "requests": total,
        "concurrency": args.concurrency,
        "success": ok,
        "failed": failed,
        "statusCounts": statuses,
        "sourceCounts": sources,
        "elapsedSeconds": round(elapsed, 3),
        "requestsPerSecond": round(total / elapsed, 2) if elapsed else 0,
        "latencySeconds": {
            "min": round(min(durations), 4) if durations else 0,
            "avg": round(statistics.mean(durations), 4) if durations else 0,
            "p95": round(p95, 4),
            "p99": round(p99, 4),
            "max": round(max(durations), 4) if durations else 0,
        },
    }
    report_json = json.dumps(report, ensure_ascii=False, indent=2)
    print(report_json)
    if args.output:
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(report_json + "\n", encoding="utf-8")
    if failed:
        errors = [result.error for result in results if result.error][:3]
        if errors:
            print("sampleErrors:")
            for error in errors:
                print(f"- {error}")
        raise SystemExit(1)


def percentile(sorted_values, p):
    if not sorted_values:
        return 0
    index = int(round((p / 100) * (len(sorted_values) - 1)))
    return sorted_values[index]


if __name__ == "__main__":
    main()
