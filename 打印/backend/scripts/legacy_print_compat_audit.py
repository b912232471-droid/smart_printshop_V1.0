#!/usr/bin/env python3
"""
Audit Gateway/Nginx access logs before tightening GATEWAY_PRINT_COMPAT_PATHS.

Examples:
  python scripts/legacy_print_compat_audit.py logs/gateway.log logs/nginx-access.log
  docker compose logs gateway | python scripts/legacy_print_compat_audit.py -
"""

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit


GATEWAY_PATH_RE = re.compile(r"\bpath=(?P<path>/api[^\s]*)")
HTTP_REQUEST_RE = re.compile(r"\b[A-Z]+\s+(?:https?://[^/\s\"]+)?(?P<path>/api[^\s\"]*)\s+HTTP/[0-9.]+")

CURRENT_PREFIXES = (
    "/api/print",
    "/api/photo",
    "/api/chat",
    "/api/schedule",
)

LEGACY_PRINT_PREFIXES = (
    "/api/admin",
    "/api/file",
    "/api/order",
    "/api/service",
    "/api/store",
    "/api/user",
    "/api/wx",
)

PUBLIC_FILE_PREFIX = "/api/public-files"


@dataclass
class Sample:
    source: str
    line_number: int
    line: str


def configure_output_encoding() -> None:
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure:
            reconfigure(encoding="utf-8")


def main() -> int:
    configure_output_encoding()
    parser = argparse.ArgumentParser(description="Audit legacy print-core /api/** paths before tightening Gateway compatibility route.")
    parser.add_argument("logs", nargs="*", help="Gateway/Nginx log files. Use '-' to read stdin.")
    parser.add_argument("--json", action="store_true", help="Print JSON report instead of text.")
    parser.add_argument("--sample-limit", type=int, default=8, help="Sample lines to keep for each finding category.")
    parser.add_argument("--min-api-requests", type=int, default=1, help="Fail if fewer API request paths are observed.")
    parser.add_argument("--fail-on-public-files", action="store_true", help="Also fail when legacy /api/public-files/** traffic remains.")
    args = parser.parse_args()

    if args.sample_limit < 0:
        raise SystemExit("--sample-limit must be non-negative")
    if args.min_api_requests < 0:
        raise SystemExit("--min-api-requests must be non-negative")

    log_inputs = args.logs or ["-"]
    report = audit(log_inputs, args.sample_limit)
    report["minApiRequests"] = args.min_api_requests
    report["recommendation"] = recommendation(report, args.min_api_requests)
    report["exitCode"] = exit_code(report, args.min_api_requests, args.fail_on_public_files)

    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        print_text(report, args.fail_on_public_files)
    return int(report["exitCode"])


def audit(log_inputs: list[str], sample_limit: int) -> dict:
    counters = {
        "totalLines": 0,
        "apiRequests": 0,
        "currentApiRequests": 0,
        "legacyDynamicRequests": 0,
        "legacyPublicFileRequests": 0,
        "unknownLegacyApiRequests": 0,
    }
    path_counts: dict[str, Counter] = {
        "legacyDynamic": Counter(),
        "legacyPublicFiles": Counter(),
        "unknownLegacyApi": Counter(),
        "currentApi": Counter(),
    }
    samples: dict[str, list[Sample]] = defaultdict(list)

    for source, line_number, line in iter_lines(log_inputs):
        counters["totalLines"] += 1
        for path in extract_paths(line):
            counters["apiRequests"] += 1
            category = classify(path)
            if category == "currentApi":
                counters["currentApiRequests"] += 1
            elif category == "legacyDynamic":
                counters["legacyDynamicRequests"] += 1
            elif category == "legacyPublicFiles":
                counters["legacyPublicFileRequests"] += 1
            elif category == "unknownLegacyApi":
                counters["unknownLegacyApiRequests"] += 1
            path_counts[category][path] += 1
            if category != "currentApi" and len(samples[category]) < sample_limit:
                samples[category].append(Sample(source, line_number, line.rstrip("\n")))

    return {
        **counters,
        "pathCounts": {
            name: counter.most_common()
            for name, counter in path_counts.items()
            if counter
        },
        "samples": {
            name: [sample.__dict__ for sample in category_samples]
            for name, category_samples in samples.items()
            if category_samples
        },
    }


def iter_lines(log_inputs: list[str]):
    for item in log_inputs:
        if item == "-":
            for line_number, line in enumerate(sys.stdin, start=1):
                yield "stdin", line_number, line
            continue
        path = Path(item)
        if not path.is_file():
            raise SystemExit(f"log file not found: {path}")
        with path.open("r", encoding="utf-8", errors="replace") as handle:
            for line_number, line in enumerate(handle, start=1):
                yield str(path), line_number, line


def extract_paths(line: str) -> list[str]:
    candidates: list[str] = []
    for match in GATEWAY_PATH_RE.finditer(line):
        candidates.append(match.group("path"))
    for match in HTTP_REQUEST_RE.finditer(line):
        candidates.append(match.group("path"))

    paths: list[str] = []
    seen: set[str] = set()
    for candidate in candidates:
        path = normalize_path(candidate)
        if path and path not in seen:
            paths.append(path)
            seen.add(path)
    return paths


def normalize_path(value: str) -> str:
    parsed = urlsplit(value)
    path = parsed.path or value.split("?", 1)[0]
    if not path.startswith("/"):
        path = "/" + path
    if len(path) > 1:
        path = path.rstrip("/")
    return path


def classify(path: str) -> str:
    if matches_any(path, CURRENT_PREFIXES):
        return "currentApi"
    if matches_prefix(path, PUBLIC_FILE_PREFIX):
        return "legacyPublicFiles"
    if matches_any(path, LEGACY_PRINT_PREFIXES):
        return "legacyDynamic"
    if path == "/api" or path.startswith("/api/"):
        return "unknownLegacyApi"
    return "currentApi"


def matches_any(path: str, prefixes: tuple[str, ...]) -> bool:
    return any(matches_prefix(path, prefix) for prefix in prefixes)


def matches_prefix(path: str, prefix: str) -> bool:
    return path == prefix or path.startswith(prefix + "/")


def recommendation(report: dict, min_api_requests: int) -> str:
    if report["apiRequests"] < min_api_requests:
        return "Collect more Gateway/Nginx API traffic before changing GATEWAY_PRINT_COMPAT_PATHS."
    if report["legacyDynamicRequests"] or report["unknownLegacyApiRequests"]:
        return "Do not tighten yet. Keep GATEWAY_PRINT_COMPAT_PATHS=/api/** until old print-core clients stop using legacy /api/* paths."
    if report["legacyPublicFileRequests"]:
        return "Dynamic legacy paths are gone. If public file compatibility is still needed, set GATEWAY_PRINT_COMPAT_PATHS=/api/public-files/**."
    return "No legacy print-core traffic observed. Set GATEWAY_PRINT_COMPAT_PATHS=/__disabled-print-compat/** after confirming the observation window is representative."


def exit_code(report: dict, min_api_requests: int, fail_on_public_files: bool) -> int:
    if report["apiRequests"] < min_api_requests:
        return 1
    if report["legacyDynamicRequests"] or report["unknownLegacyApiRequests"]:
        return 1
    if fail_on_public_files and report["legacyPublicFileRequests"]:
        return 1
    return 0


def print_text(report: dict, fail_on_public_files: bool) -> None:
    level = "FAIL" if report["exitCode"] else "PASS"
    if report["legacyPublicFileRequests"] and not fail_on_public_files and not report["legacyDynamicRequests"] and not report["unknownLegacyApiRequests"]:
        level = "WARN"
    print(
        f"[{level}] lines={report['totalLines']} apiRequests={report['apiRequests']} "
        f"current={report['currentApiRequests']} legacyDynamic={report['legacyDynamicRequests']} "
        f"legacyPublicFiles={report['legacyPublicFileRequests']} unknownLegacyApi={report['unknownLegacyApiRequests']}"
    )
    for category in ("legacyDynamic", "legacyPublicFiles", "unknownLegacyApi", "currentApi"):
        counts = report.get("pathCounts", {}).get(category, [])
        if not counts:
            continue
        print(f"{category}:")
        for path, count in counts[:12]:
            print(f"  {count:>5} {path}")
    for category, samples in report.get("samples", {}).items():
        print(f"{category} samples:")
        for sample in samples:
            print(f"  {sample['source']}:{sample['line_number']}: {sample['line']}")
    print(f"recommendation: {report['recommendation']}")


if __name__ == "__main__":
    sys.exit(main())
