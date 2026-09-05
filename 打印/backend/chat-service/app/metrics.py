"""
Lightweight in-process metrics for chat-service.
"""

import os
import re
import threading
import time
from collections import defaultdict
from typing import Dict


class ServiceMetrics:
    def __init__(self):
        self.instance_id = os.getenv("CHAT_INSTANCE_ID") or os.getenv("HOSTNAME") or "local"
        self.started_at = time.time()
        self._lock = threading.Lock()
        self._requests_total = 0
        self._status_counts = defaultdict(int)
        self._path_counts = defaultdict(int)
        self._duration_sum = 0.0
        self._duration_max = 0.0
        self._ask_source_counts = defaultdict(int)
        self._knowledge_operation_counts = defaultdict(int)

    def record_request(self, path: str, status_code: int, duration_seconds: float):
        normalized_path = self._normalize_path(path)
        with self._lock:
            self._requests_total += 1
            self._status_counts[str(status_code)] += 1
            self._path_counts[normalized_path] += 1
            self._duration_sum += duration_seconds
            if duration_seconds > self._duration_max:
                self._duration_max = duration_seconds

    def record_ask_source(self, source: str):
        normalized_source = (source or "unknown").strip() or "unknown"
        with self._lock:
            self._ask_source_counts[normalized_source] += 1

    def record_knowledge_operation(self, operation: str):
        normalized_operation = (operation or "unknown").strip() or "unknown"
        with self._lock:
            self._knowledge_operation_counts[normalized_operation] += 1

    def snapshot(self) -> Dict[str, object]:
        with self._lock:
            average = self._duration_sum / self._requests_total if self._requests_total else 0.0
            return {
                "service": "chat-service",
                "instanceId": self.instance_id,
                "uptimeSeconds": round(time.time() - self.started_at, 3),
                "requestsTotal": self._requests_total,
                "statusCounts": dict(self._status_counts),
                "pathCounts": dict(self._path_counts),
                "askSourceCounts": dict(self._ask_source_counts),
                "knowledgeOperationCounts": dict(self._knowledge_operation_counts),
                "requestDurationSeconds": {
                    "average": round(average, 6),
                    "max": round(self._duration_max, 6),
                    "sum": round(self._duration_sum, 6),
                },
            }

    def prometheus(self) -> str:
        data = self.snapshot()
        instance = data["instanceId"]
        duration = data["requestDurationSeconds"]
        lines = [
            "# HELP chat_service_uptime_seconds Service uptime in seconds.",
            "# TYPE chat_service_uptime_seconds gauge",
            f"chat_service_uptime_seconds{{instance=\"{instance}\"}} {data['uptimeSeconds']}",
            "# HELP chat_service_requests_total Total HTTP requests handled by chat-service.",
            "# TYPE chat_service_requests_total counter",
            f"chat_service_requests_total{{instance=\"{instance}\"}} {data['requestsTotal']}",
            "# HELP chat_service_request_duration_seconds_sum Total request duration in seconds.",
            "# TYPE chat_service_request_duration_seconds_sum counter",
            f"chat_service_request_duration_seconds_sum{{instance=\"{instance}\"}} {duration['sum']}",
            "# HELP chat_service_request_duration_seconds_max Maximum observed request duration in seconds.",
            "# TYPE chat_service_request_duration_seconds_max gauge",
            f"chat_service_request_duration_seconds_max{{instance=\"{instance}\"}} {duration['max']}",
        ]
        for status, count in data["statusCounts"].items():
            lines.append(f"chat_service_requests_by_status_total{{instance=\"{instance}\",status=\"{status}\"}} {count}")
        for path, count in data["pathCounts"].items():
            lines.append(f"chat_service_requests_by_path_total{{instance=\"{instance}\",path=\"{path}\"}} {count}")
        for source, count in data["askSourceCounts"].items():
            lines.append(f"chat_service_ask_source_total{{instance=\"{instance}\",source=\"{source}\"}} {count}")
        for operation, count in data["knowledgeOperationCounts"].items():
            lines.append(
                f"chat_service_knowledge_operation_total{{instance=\"{instance}\",operation=\"{operation}\"}} {count}"
            )
        return "\n".join(lines) + "\n"

    def _normalize_path(self, path: str) -> str:
        if path in {"/api/chat/health", "/health"}:
            return "/health"
        if path == "/api/chat/ask":
            return "/ask"
        if path == "/api/chat/knowledge":
            return "/knowledge"
        if path == "/api/chat/knowledge/create":
            return "/knowledge/create"
        if re.fullmatch(r"/api/chat/knowledge/\d+", path or ""):
            return "/knowledge/{id}"
        if path in {"/api/chat/metrics", "/metrics"}:
            return "/metrics"
        if path.startswith("/docs") or path.startswith("/openapi") or path.startswith("/redoc"):
            return "/docs"
        return path or "/"


metrics = ServiceMetrics()
