"""
Nacos service registration for photo-service.
"""

import logging
import socket
import threading
import time
import urllib.parse
import urllib.request

from app.core.config import settings

logger = logging.getLogger(__name__)


class NacosRegistration:
    """Minimal Nacos registration using the HTTP OpenAPI."""

    def __init__(self):
        self._stop = threading.Event()
        self._thread = None

    def start(self):
        if not settings.NACOS_ENABLED:
            return
        try:
            self._register()
        except OSError:
            logger.warning("photo-service initial Nacos registration failed", exc_info=True)
        self._thread = threading.Thread(target=self._heartbeat_loop, name="nacos-heartbeat", daemon=True)
        self._thread.start()

    def stop(self):
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=2)

    def _heartbeat_loop(self):
        while not self._stop.wait(settings.NACOS_HEARTBEAT_SECONDS):
            try:
                self._register()
            except OSError:
                logger.warning("photo-service Nacos heartbeat failed", exc_info=True)

    def _register(self):
        data = urllib.parse.urlencode({
            "serviceName": settings.SERVICE_NAME,
            "groupName": settings.NACOS_GROUP,
            "namespaceId": settings.NACOS_NAMESPACE,
            "ip": self._service_ip(),
            "port": str(settings.SERVICE_PORT),
            "ephemeral": "true",
            "healthy": "true",
            "metadata": '{"version":"1.0.0"}',
        }).encode("utf-8")
        request = urllib.request.Request(
            f"http://{settings.NACOS_SERVER_ADDR}/nacos/v1/ns/instance",
            data=data,
            method="POST",
        )
        with urllib.request.urlopen(request, timeout=3) as response:
            response.read()

    def _service_ip(self):
        if settings.NACOS_IP:
            return settings.NACOS_IP
        try:
            return socket.gethostbyname(socket.gethostname())
        except OSError:
            return "127.0.0.1"
