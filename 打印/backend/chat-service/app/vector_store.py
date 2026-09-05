import json
import logging
import threading
import urllib.error
import urllib.request
from typing import Any, Dict, Iterable, List, Sequence

from app.config import settings

logger = logging.getLogger(__name__)


class EmbeddingProvider:
    def __init__(self):
        self._model = None
        self._lock = threading.Lock()

    @property
    def enabled(self) -> bool:
        return settings.VECTOR_ENABLED

    def embed(self, texts: Sequence[str]) -> List[List[float]]:
        if not self.enabled:
            return []
        model = self._get_model()
        return [vector.tolist() if hasattr(vector, "tolist") else list(vector) for vector in model.embed(list(texts))]

    def _get_model(self):
        if self._model is not None:
            return self._model
        with self._lock:
            if self._model is None:
                try:
                    from fastembed import TextEmbedding
                except ImportError as exc:
                    raise RuntimeError("fastembed is required when CHAT_VECTOR_ENABLED=true") from exc
                self._model = TextEmbedding(model_name=settings.EMBEDDING_MODEL, cache_dir=settings.EMBEDDING_CACHE_DIR)
        return self._model


class QdrantVectorStore:
    def __init__(self):
        self.base_url = settings.QDRANT_URL.rstrip("/")
        self.collection = settings.QDRANT_COLLECTION
        self._ready = False
        self._lock = threading.Lock()

    @property
    def enabled(self) -> bool:
        return settings.VECTOR_ENABLED

    def upsert(self, chunk_rows: Sequence[Any], vectors: Sequence[Sequence[float]]):
        if not vectors:
            return
        self.ensure_collection(len(vectors[0]))
        points = []
        for chunk, vector in zip(chunk_rows, vectors):
            points.append(
                {
                    "id": int(chunk.id),
                    "vector": list(vector),
                    "payload": {
                        "document_id": int(chunk.documentId),
                        "chunk_index": int(chunk.chunkIndex),
                        "heading": chunk.heading,
                    },
                }
            )
        self._request("PUT", f"/collections/{self.collection}/points?wait=true", {"points": points})

    def search(self, vector: Sequence[float], limit: int = 20) -> List[Dict[str, Any]]:
        if not self.enabled:
            return []
        data = self._request(
            "POST",
            f"/collections/{self.collection}/points/search",
            {"vector": list(vector), "limit": max(1, min(limit, 100)), "with_payload": True},
        )
        result = data.get("result", []) if isinstance(data, dict) else []
        return [item for item in result if isinstance(item, dict)]

    def delete_document(self, document_id: int):
        if not self.enabled:
            return
        try:
            self._request(
                "POST",
                f"/collections/{self.collection}/points/delete?wait=true",
                {"filter": {"must": [{"key": "document_id", "match": {"value": int(document_id)}}]}},
            )
        except RuntimeError:
            logger.warning("failed to delete document vectors", exc_info=True)

    def health(self) -> bool:
        if not self.enabled:
            return False
        try:
            self._request("GET", "/healthz")
            return True
        except RuntimeError:
            return False

    def ensure_collection(self, dimension: int):
        if self._ready:
            return
        with self._lock:
            if self._ready:
                return
            try:
                self._request("GET", f"/collections/{self.collection}")
            except RuntimeError as exc:
                if "status=404" not in str(exc):
                    raise
                self._request(
                    "PUT",
                    f"/collections/{self.collection}",
                    {"vectors": {"size": int(dimension), "distance": "Cosine"}},
                )
            self._ready = True

    def _request(self, method: str, path: str, payload: Dict[str, Any] | None = None) -> Dict[str, Any]:
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8") if payload is not None else None
        request = urllib.request.Request(
            self.base_url + path,
            data=raw,
            headers={"Content-Type": "application/json"},
            method=method,
        )
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                body = response.read()
            return json.loads(body.decode("utf-8")) if body else {}
        except urllib.error.HTTPError as exc:
            raise RuntimeError(f"Qdrant request failed: status={exc.code}") from exc
        except (OSError, json.JSONDecodeError) as exc:
            raise RuntimeError("Qdrant request failed") from exc
