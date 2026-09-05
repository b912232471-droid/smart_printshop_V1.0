"""
Lightweight in-process metrics for photo-service.
"""

import os
import threading
import time
from collections import defaultdict
from typing import Dict


class ServiceMetrics:
    def __init__(self):
        self.instance_id = os.getenv("PHOTO_INSTANCE_ID") or os.getenv("HOSTNAME") or "local"
        self.started_at = time.time()
        self._lock = threading.Lock()
        self._requests_total = 0
        self._status_counts = defaultdict(int)
        self._path_counts = defaultdict(int)
        self._duration_sum = 0.0
        self._duration_max = 0.0

    def record(self, path: str, status_code: int, duration_seconds: float):
        normalized_path = self._normalize_path(path)
        with self._lock:
            self._requests_total += 1
            self._status_counts[str(status_code)] += 1
            self._path_counts[normalized_path] += 1
            self._duration_sum += duration_seconds
            if duration_seconds > self._duration_max:
                self._duration_max = duration_seconds

    def snapshot(self) -> Dict[str, object]:
        with self._lock:
            average = self._duration_sum / self._requests_total if self._requests_total else 0.0
            return {
                "service": "photo-service",
                "instanceId": self.instance_id,
                "uptimeSeconds": round(time.time() - self.started_at, 3),
                "requestsTotal": self._requests_total,
                "statusCounts": dict(self._status_counts),
                "pathCounts": dict(self._path_counts),
                "requestDurationSeconds": {
                    "average": round(average, 6),
                    "max": round(self._duration_max, 6),
                    "sum": round(self._duration_sum, 6),
                },
            }

    def prometheus(self) -> str:
        data = self.snapshot()
        lines = [
            "# HELP photo_service_uptime_seconds Service uptime in seconds.",
            "# TYPE photo_service_uptime_seconds gauge",
            f"photo_service_uptime_seconds{{instance=\"{data['instanceId']}\"}} {data['uptimeSeconds']}",
            "# HELP photo_service_requests_total Total HTTP requests handled by photo-service.",
            "# TYPE photo_service_requests_total counter",
            f"photo_service_requests_total{{instance=\"{data['instanceId']}\"}} {data['requestsTotal']}",
            "# HELP photo_service_request_duration_seconds_sum Total request duration in seconds.",
            "# TYPE photo_service_request_duration_seconds_sum counter",
            f"photo_service_request_duration_seconds_sum{{instance=\"{data['instanceId']}\"}} {data['requestDurationSeconds']['sum']}",
            "# HELP photo_service_request_duration_seconds_max Maximum observed request duration in seconds.",
            "# TYPE photo_service_request_duration_seconds_max gauge",
            f"photo_service_request_duration_seconds_max{{instance=\"{data['instanceId']}\"}} {data['requestDurationSeconds']['max']}",
        ]
        for status, count in data["statusCounts"].items():
            lines.append(
                f"photo_service_requests_by_status_total{{instance=\"{data['instanceId']}\",status=\"{status}\"}} {count}"
            )
        for path, count in data["pathCounts"].items():
            lines.append(
                f"photo_service_requests_by_path_total{{instance=\"{data['instanceId']}\",path=\"{path}\"}} {count}"
            )
        return "\n".join(lines) + "\n"

    def _normalize_path(self, path: str) -> str:
        if path in {"/api/photo/generate-id-photo", "/api/v1/generate-id-photo"}:
            return "/generate-id-photo"
        if path in {"/api/photo/change-background", "/api/v1/change-background"}:
            return "/change-background"
        if path in {"/api/photo/health", "/health"}:
            return "/health"
        if path.startswith("/docs") or path.startswith("/openapi"):
            return "/docs"
        return path or "/"


metrics = ServiceMetrics()
