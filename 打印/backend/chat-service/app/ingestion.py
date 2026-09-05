import logging

from app.agent_store import AgentStore
from app.config import settings
from app.markdown_knowledge import load_and_chunk
from app.vector_store import EmbeddingProvider, QdrantVectorStore

logger = logging.getLogger(__name__)


class DocumentIngestionService:
    def __init__(self, store: AgentStore, embeddings: EmbeddingProvider, vectors: QdrantVectorStore):
        self.store = store
        self.embeddings = embeddings
        self.vectors = vectors

    def process(self, document_id: int, job_id: int):
        try:
            self.store.update_ingestion_job(job_id, "processing", 10)
            self.store.set_document_status(document_id, "processing")
            document = self.store.get_document_record(document_id)
            parsed = load_and_chunk(document["storage_path"])
            if not parsed:
                raise ValueError("Markdown document produced no knowledge chunks")
            rows = self.store.replace_document_chunks(
                document_id,
                [
                    {
                        "chunk_index": item.chunk_index,
                        "heading": item.heading,
                        "content": item.content,
                        "token_estimate": item.token_estimate,
                        "vector_status": "pending" if settings.VECTOR_ENABLED else "disabled",
                    }
                    for item in parsed
                ],
            )
            self.store.update_ingestion_job(job_id, "processing", 45)
            status = "keyword_only"
            if settings.VECTOR_ENABLED:
                vectors = self.embeddings.embed([f"{row.heading}\n{row.content}".strip() for row in rows])
                self.vectors.delete_document(document_id)
                self.vectors.upsert(rows, vectors)
                self.store.set_chunk_vector_status([row.id for row in rows], "ready")
                status = "ready"
            self.store.set_document_status(document_id, status, len(rows))
            self.store.activate_document_version(document_id)
            self.store.update_ingestion_job(job_id, "completed", 100)
        except Exception as exc:
            logger.exception("knowledge document ingestion failed")
            self.store.set_document_status(document_id, "failed", error=str(exc))
            self.store.update_ingestion_job(job_id, "failed", 100, str(exc))
            if settings.VECTOR_REQUIRED:
                raise


def enqueue_ingestion(document_id: int, job_id: int, service: DocumentIngestionService):
    if settings.INGESTION_INLINE:
        service.process(document_id, job_id)
        return
    from app.tasks import process_document

    process_document.delay(document_id, job_id)
