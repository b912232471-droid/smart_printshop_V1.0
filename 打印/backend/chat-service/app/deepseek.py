import json
import logging
import urllib.error
import urllib.request
from typing import Any, Dict, List, Sequence

from app.config import settings
from app.schemas import KnowledgeItem

logger = logging.getLogger(__name__)


class DeepSeekClient:
    @property
    def available(self) -> bool:
        return bool(settings.DEEPSEEK_API_KEY.strip())

    def generate(self, question: str, contexts: Sequence[KnowledgeItem]) -> str | None:
        if not self.available:
            return None
        context_text = "\n".join(
            f"- 问：{item.question}\n  答：{item.answer}" for item in contexts
        ) or "暂无知识库命中内容。"
        result = self.complete(
            messages=[
                {
                    "role": "system",
                    "content": (
                        "你是智慧打印平台的客服。只能回答与打印下单、订单、证件照、课表、门店和平台使用相关的问题。"
                        "优先依据知识库内容，回答要简洁、准确；没有把握时引导用户联系人工客服 400-778-1811。"
                    ),
                },
                {
                    "role": "user",
                    "content": f"知识库参考：\n{context_text}\n\n用户问题：{question}",
                },
            ]
        )
        if not result:
            return None
        return str(result.get("content") or "").strip() or None

    def complete(
        self,
        messages: List[Dict[str, Any]],
        tools: List[Dict[str, Any]] | None = None,
        model: str | None = None,
        temperature: float = 0.2,
        max_tokens: int = 800,
    ) -> Dict[str, Any] | None:
        if not self.available:
            return None
        payload: Dict[str, Any] = {
            "model": model or settings.DEEPSEEK_MODEL,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
        }
        if tools:
            payload["tools"] = tools
            payload["tool_choice"] = "auto"
        request = urllib.request.Request(
            f"{settings.DEEPSEEK_BASE_URL.rstrip('/')}/chat/completions",
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {settings.DEEPSEEK_API_KEY.strip()}",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=settings.DEEPSEEK_TIMEOUT_SECONDS) as response:
                data = json.loads(response.read().decode("utf-8"))
            choices = data.get("choices") or []
            if not choices or not isinstance(choices[0], dict):
                return None
            message = choices[0].get("message") or {}
            return message if isinstance(message, dict) else None
        except (OSError, urllib.error.HTTPError, json.JSONDecodeError, UnicodeDecodeError):
            logger.warning("DeepSeek request failed", exc_info=True)
            return None
