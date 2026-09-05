#!/usr/bin/env python3
"""
Simple photo-service pressure test using only the Python standard library.

Examples:
  python scripts/photo_pressure_test.py --base-url http://localhost:8091 --requests 100 --concurrency 10
  python scripts/photo_pressure_test.py --base-url http://localhost:8080 --endpoint /api/photo/health --token <jwt>
  python scripts/photo_pressure_test.py --base-url http://localhost:8091 --endpoint /api/photo/change-background --image sample.jpg
  python scripts/photo_pressure_test.py --base-url http://localhost:18191,http://localhost:18192,http://localhost:18193 --image sample.jpg --output reports/photo.json
"""

import argparse
import json
import queue
import statistics
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path


@dataclass
class Result:
    ok: bool
    base_url: str
    status: int
    elapsed: float
    error: str = ""


def main():
    parser = argparse.ArgumentParser(description="Pressure test photo-service endpoints.")
    parser.add_argument("--base-url", default="http://localhost:8091", help="Service or Gateway base URL. Use comma-separated URLs for round-robin multi-instance tests.")
    parser.add_argument("--endpoint", default="/api/photo/health", help="Endpoint path.")
    parser.add_argument("--requests", type=int, default=100, help="Total request count.")
    parser.add_argument("--concurrency", type=int, default=10, help="Concurrent worker count.")
    parser.add_argument("--timeout", type=float, default=15.0, help="Per-request timeout seconds.")
    parser.add_argument("--token", default="", help="Bearer token when testing through Gateway.")
    parser.add_argument("--image", default="", help="Image file path for multipart endpoints.")
    parser.add_argument("--size", default="1寸", help="ID photo size for generate-id-photo.")
    parser.add_argument("--bg-color", default="#FFFFFF", help="Background color for image endpoints.")
    parser.add_argument("--output", default="", help="Optional JSON report output path.")
    args = parser.parse_args()

    if args.requests <= 0 or args.concurrency <= 0:
        raise SystemExit("--requests and --concurrency must be positive")
    if args.image and not Path(args.image).is_file():
        raise SystemExit(f"image file not found: {args.image}")
    args.base_urls = [url.strip().rstrip("/") for url in args.base_url.split(",") if url.strip()]
    if not args.base_urls:
        raise SystemExit("--base-url must contain at least one URL")

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
    base_url = args.base_urls[index % len(args.base_urls)]
    url = base_url + "/" + args.endpoint.lstrip("/")
    headers = {}
    if args.token:
        headers["Authorization"] = "Bearer " + args.token
    started = time.perf_counter()
    try:
        if args.image:
            body, content_type = multipart_body(args)
            headers["Content-Type"] = content_type
            request = urllib.request.Request(url, data=body, headers=headers, method="POST")
        else:
            request = urllib.request.Request(url, headers=headers, method="GET")
        with urllib.request.urlopen(request, timeout=args.timeout) as response:
            response.read()
            status = response.status
        return Result(ok=200 <= status < 300, base_url=base_url, status=status, elapsed=time.perf_counter() - started)
    except urllib.error.HTTPError as exc:
        exc.read()
        return Result(ok=False, base_url=base_url, status=exc.code, elapsed=time.perf_counter() - started, error=str(exc))
    except OSError as exc:
        return Result(ok=False, base_url=base_url, status=0, elapsed=time.perf_counter() - started, error=str(exc))


def multipart_body(args):
    boundary = "----photo-pressure-boundary"
    fields = {
        "bg_color": args.bg_color,
    }
    endpoint = args.endpoint.lower()
    if "generate-id-photo" in endpoint:
        fields["size"] = args.size
    if "change-background" in endpoint:
        fields["output_format"] = "png"
        fields["enhance"] = "true"
        fields["gradient_bg"] = "true"
        fields["smooth_edge"] = "true"
    chunks = []
    for name, value in fields.items():
        chunks.extend([
            f"--{boundary}\r\n".encode("utf-8"),
            f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"),
            str(value).encode("utf-8"),
            b"\r\n",
        ])
    image_path = Path(args.image)
    chunks.extend([
        f"--{boundary}\r\n".encode("utf-8"),
        f'Content-Disposition: form-data; name="image"; filename="{image_path.name}"\r\n'.encode("utf-8"),
        b"Content-Type: image/jpeg\r\n\r\n",
        image_path.read_bytes(),
        b"\r\n",
        f"--{boundary}--\r\n".encode("utf-8"),
    ])
    return b"".join(chunks), f"multipart/form-data; boundary={boundary}"


def print_report(args, results, elapsed):
    total = len(results)
    ok = sum(1 for result in results if result.ok)
    failed = total - ok
    statuses = {}
    base_urls = {}
    durations = [result.elapsed for result in results]
    for result in results:
        statuses[result.status] = statuses.get(result.status, 0) + 1
        base_urls[result.base_url] = base_urls.get(result.base_url, 0) + 1
    durations_sorted = sorted(durations)
    p95 = percentile(durations_sorted, 95)
    p99 = percentile(durations_sorted, 99)
    report = {
        "baseUrl": args.base_url,
        "baseUrls": args.base_urls,
        "endpoint": args.endpoint,
        "requests": total,
        "concurrency": args.concurrency,
        "success": ok,
        "failed": failed,
        "statusCounts": statuses,
        "baseUrlCounts": base_urls,
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
