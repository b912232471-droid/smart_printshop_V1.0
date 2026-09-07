"""
AI 图片生成服务 - TokenHub /v1/images/generations 适配层

职责边界（见 docs/图文生成功能更新迭代方案-2026-09-06.md 第 2 节）：
- 只做模型调用与参数白名单校验，不做配额、审核、落盘等业务编排（print-service 负责）
- 未配置 IMAGE_API_KEY 时抛 503，不伪造成功（对齐 BAIDU_OCR_* 语义）
- 服务本身无状态、不连库
"""

import json
import time
import urllib.error
import urllib.request
from typing import Optional, Tuple

from fastapi import HTTPException

from app.core.config import settings


class ImageGenerationService:
    """TokenHub 图片生成模型客户端"""

    def generate(self, model: str, prompt: str, size: str, watermark_text: Optional[str] = None) -> dict:
        if not settings.IMAGE_API_KEY:
            raise HTTPException(status_code=503, detail="图片生成服务未配置，暂不可用")
        if model not in settings.IMAGE_MODEL_CATALOG:
            raise HTTPException(status_code=400, detail="不支持的模型")
        catalog_item = settings.IMAGE_MODEL_CATALOG[model]

        payload = {
            "model": model,
            "prompt": prompt,
            "size": self._api_size(size, catalog_item),
            "response_format": "b64_json",
        }
        if watermark_text and catalog_item.get("supports_watermark", False):
            # 水印脚注字段名以 M0 联调结论为准（方案 5.1.1），默认关闭不影响主链路
            payload["watermark_text"] = watermark_text

        started = time.perf_counter()
        data = self._request_json(payload)
        image_b64 = self._extract_b64(data)
        if not image_b64:
            remote_url = self._extract_url(data)
            if remote_url:
                image_b64 = self._download_as_b64(remote_url)
        if not image_b64:
            raise HTTPException(status_code=502, detail="模型未返回图片数据")

        usage_tokens = self._extract_usage(data)
        duration_ms = int((time.perf_counter() - started) * 1000)
        width, height = self._parse_size(size)
        return {
            "image": image_b64,
            "width": width,
            "height": height,
            "size": size,
            "usage_tokens": usage_tokens,
            "duration_ms": duration_ms,
        }

    def _api_size(self, size: str, catalog_item: dict) -> str:
        """平台 size 为 宽x高；API 为 高x宽（size_axis=HxW）时换轴"""
        if catalog_item.get("size_axis", "WxH") != "HxW":
            return size
        width, height = size.lower().split("x", 1)
        return f"{height}x{width}"

    def _request_json(self, payload: dict) -> dict:
        url = settings.IMAGE_API_BASE_URL.rstrip("/") + "/images/generations"
        body = json.dumps(payload).encode("utf-8")
        request = urllib.request.Request(
            url,
            data=body,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {settings.IMAGE_API_KEY}",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=settings.IMAGE_API_TIMEOUT_SECONDS) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            detail = self._error_detail(exc)
            raise HTTPException(status_code=502, detail=detail)
        except urllib.error.URLError as exc:
            raise HTTPException(status_code=502, detail=f"图片生成服务连接失败: {getattr(exc, 'reason', exc)}")
        except TimeoutError:
            raise HTTPException(status_code=504, detail="图片生成超时，请稍后重试")

    def _error_detail(self, exc: urllib.error.HTTPError) -> str:
        try:
            body = json.loads(exc.read().decode("utf-8"))
            message = body.get("error", {}).get("message") or body.get("message")
            if message:
                return f"图片生成服务返回错误: {message}"
        except Exception:
            pass
        return f"图片生成服务返回错误 (HTTP {exc.code})"

    def _extract_b64(self, data: dict) -> Optional[str]:
        for item in self._data_items(data):
            value = item.get("b64_json") if isinstance(item, dict) else None
            if value:
                return value
        return None

    def _extract_url(self, data: dict) -> Optional[str]:
        for item in self._data_items(data):
            value = item.get("url") if isinstance(item, dict) else None
            if value:
                return value
        return None

    def _data_items(self, data: dict) -> list:
        items = data.get("data") if isinstance(data, dict) else None
        return items if isinstance(items, list) else []

    def _download_as_b64(self, url: str) -> Optional[str]:
        import base64

        request = urllib.request.Request(url, method="GET")
        try:
            with urllib.request.urlopen(request, timeout=settings.IMAGE_API_TIMEOUT_SECONDS) as response:
                return base64.b64encode(response.read()).decode("ascii")
        except Exception:
            return None

    def _extract_usage(self, data: dict) -> Optional[int]:
        usage = data.get("usage") if isinstance(data, dict) else None
        if not isinstance(usage, dict):
            return None
        for key in ("total_tokens", "output_tokens", "input_tokens"):
            value = usage.get(key)
            if isinstance(value, int):
                return value
        return None

    def _parse_size(self, size: str) -> Tuple[int, int]:
        try:
            width_text, height_text = size.lower().split("x", 1)
            return int(width_text), int(height_text)
        except (ValueError, AttributeError):
            return 0, 0
