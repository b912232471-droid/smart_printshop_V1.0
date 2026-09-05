from app.agent_store import AgentStore
from app.celery_app import celery_app
from app.config import settings
from app.ingestion import DocumentIngestionService
from app.store import ChatStore
from app.vector_store import EmbeddingProvider, QdrantVectorStore


@celery_app.task(name="chat.process_document")
def process_document(document_id: int, job_id: int):
    store = AgentStore(ChatStore(settings.DATABASE_URL))
    service = DocumentIngestionService(store, EmbeddingProvider(), QdrantVectorStore())
    service.process(document_id, job_id)
