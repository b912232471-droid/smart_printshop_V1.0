from contextlib import asynccontextmanager
import asyncio
import json
from pathlib import Path
import time

from fastapi import Depends, FastAPI, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, PlainTextResponse, StreamingResponse

from app.agent import ChatDisabledError, HybridKnowledgeRetriever, StationAgent
from app.agent_store import AgentStore
from app.config import settings
from app.llm import LlmClient
from app.ingestion import DocumentIngestionService, enqueue_ingestion
from app.importer import import_knowledge_file
from app.markdown_knowledge import save_markdown
from app.metrics import metrics
from app.nacos import NacosRegistration
from app.schemas import (
    AdminChatOverview,
    AskRequest,
    AskResponse,
    ChatSettings,
    ChatSettingsUpdate,
    ChatStatus,
    ConversationCreate,
    ConversationItem,
    ConversationMessages,
    DocumentChunkItem,
    DocumentItem,
    DocumentList,
    FeedbackCreate,
    FeedbackItem,
    KnowledgeCreate,
    KnowledgeImportRequest,
    KnowledgeImportResponse,
    KnowledgeItem,
    KnowledgeList,
    KnowledgeUpdate,
    MarkdownImportRequest,
    RetrievalTestRequest,
    RetrievalTestResponse,
)
from app.security import Principal, require_permission, require_principal
from app.store import ChatStore
from app.tools import TOOL_DEFINITIONS, PlatformToolExecutor
from app.vector_store import EmbeddingProvider, QdrantVectorStore

nacos_registration = NacosRegistration()
store = ChatStore(settings.DATABASE_URL)
agent_store = AgentStore(store)
embeddings = EmbeddingProvider()
vector_store = QdrantVectorStore()
retriever = HybridKnowledgeRetriever(store, agent_store, embeddings, vector_store)
agent = StationAgent(store, agent_store, retriever, LlmClient(), PlatformToolExecutor())
ingestion = DocumentIngestionService(agent_store, embeddings, vector_store)


@asynccontextmanager
async def lifespan(app: FastAPI):
    nacos_registration.start()
    yield
    nacos_registration.stop()


app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def collect_metrics(request: Request, call_next):
    started = time.perf_counter()
    status_code = 500
    try:
        response = await call_next(request)
        status_code = response.status_code
        return response
    finally:
        metrics.record_request(request.url.path, status_code, time.perf_counter() - started)


@app.get("/health")
async def health():
    return {"status": "ok", "service": settings.SERVICE_NAME}


@app.get("/api/chat/health")
async def gateway_health():
    return {
        "status": "ok",
        "service": settings.SERVICE_NAME,
        "vectorEnabled": settings.VECTOR_ENABLED,
        "vectorAvailable": await asyncio.to_thread(vector_store.health) if settings.VECTOR_ENABLED else False,
    }


@app.get("/api/chat/metrics")
async def chat_metrics():
    return metrics.snapshot()


@app.get("/metrics", response_class=PlainTextResponse)
async def prometheus_metrics():
    return PlainTextResponse(metrics.prometheus(), media_type="text/plain; version=0.0.4")


@app.get("/api")
async def api_info():
    return {
        "name": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "service": settings.SERVICE_NAME,
        "deepseekEnabled": bool(settings.CHAT_LLM_API_KEY.strip()),
    }


@app.post("/api/chat/ask", response_model=AskResponse)
async def ask(request: AskRequest, principal: Principal = Depends(require_principal)):
    try:
        response = await asyncio.to_thread(agent.ask, request.question, request.sessionId, principal)
    except ChatDisabledError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from None
    metrics.record_ask_source(response.source)
    return response


@app.post("/api/chat/ask/stream")
async def ask_stream(request: AskRequest, principal: Principal = Depends(require_principal)):
    async def events():
        yield _sse("status", {"state": "thinking"})
        try:
            response = await asyncio.to_thread(agent.ask, request.question, request.sessionId, principal)
            metrics.record_ask_source(response.source)
            for tool_call in response.toolCalls:
                yield _sse("tool", tool_call.model_dump())
            for index in range(0, len(response.answer), 32):
                yield _sse("delta", {"content": response.answer[index:index + 32]})
                await asyncio.sleep(0)
            yield _sse("message", response.model_dump())
            yield _sse("done", {"sessionId": response.sessionId, "messageId": response.messageId})
        except ChatDisabledError as exc:
            yield _sse("error", {"code": 503, "message": str(exc)})
        except Exception:
            yield _sse("error", {"code": 500, "message": "chat service internal error"})

    return StreamingResponse(events(), media_type="text/event-stream", headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})


@app.get("/api/chat/status", response_model=ChatStatus)
async def chat_status():
    policy = await asyncio.to_thread(agent_store.get_settings)
    return ChatStatus(
        enabled=policy.enabled,
        mode=policy.mode,
        deepseekEnabled=policy.deepseekEnabled and bool(settings.CHAT_LLM_API_KEY.strip()),
        vectorEnabled=settings.VECTOR_ENABLED,
        serviceHours=policy.serviceHours,
        hotline=policy.hotline,
    )


@app.get("/api/chat/settings", response_model=ChatSettings)
async def get_settings(_: Principal = Depends(require_permission("chat:knowledge:manage"))):
    return await asyncio.to_thread(agent_store.get_settings)


@app.put("/api/chat/settings", response_model=ChatSettings)
async def update_settings(request: ChatSettingsUpdate, principal: Principal = Depends(require_permission("chat:knowledge:manage"))):
    unknown = sorted(set(request.allowedTools) - set(TOOL_DEFINITIONS))
    if unknown:
        raise HTTPException(status_code=400, detail="unknown tools: " + ", ".join(unknown))
    return await asyncio.to_thread(agent_store.update_settings, request, principal.id)


@app.post("/api/chat/conversations", response_model=ConversationItem)
async def create_conversation(request: ConversationCreate, principal: Principal = Depends(require_principal)):
    return await asyncio.to_thread(agent_store.create_conversation, principal.type, principal.id, request.title)


@app.get("/api/chat/conversations", response_model=list[ConversationItem])
async def list_conversations(principal: Principal = Depends(require_principal)):
    return await asyncio.to_thread(agent_store.list_conversations, principal.type, principal.id)


@app.get("/api/chat/conversations/{conversation_id}/messages", response_model=ConversationMessages)
async def conversation_messages(conversation_id: str, principal: Principal = Depends(require_principal)):
    try:
        conversation = await asyncio.to_thread(agent_store.get_conversation, conversation_id, principal.type, principal.id)
    except KeyError:
        raise HTTPException(status_code=404, detail="conversation not found") from None
    messages = await asyncio.to_thread(agent_store.list_messages, conversation_id, 200)
    return ConversationMessages(conversation=conversation, messages=messages)


@app.delete("/api/chat/conversations/{conversation_id}")
async def delete_conversation(conversation_id: str, principal: Principal = Depends(require_principal)):
    try:
        await asyncio.to_thread(agent_store.archive_conversation, conversation_id, principal.type, principal.id)
    except KeyError:
        raise HTTPException(status_code=404, detail="conversation not found") from None
    return {"deleted": True}


@app.post("/api/chat/feedback", response_model=FeedbackItem)
async def create_feedback(request: FeedbackCreate, principal: Principal = Depends(require_principal)):
    try:
        return await asyncio.to_thread(
            agent_store.create_feedback,
            request.messageId,
            principal.type,
            principal.id,
            request.rating,
            request.comment,
        )
    except KeyError:
        raise HTTPException(status_code=404, detail="assistant message not found") from None


@app.get("/api/chat/admin/overview", response_model=AdminChatOverview)
async def admin_overview(_: Principal = Depends(require_permission("chat:knowledge:manage"))):
    return AdminChatOverview(**(await asyncio.to_thread(agent_store.overview)))


@app.get("/api/chat/documents", response_model=DocumentList)
async def list_documents(_: Principal = Depends(require_permission("chat:knowledge:manage"))):
    items = await asyncio.to_thread(agent_store.list_documents, True)
    return DocumentList(items=items, total=len(items))


@app.post("/api/chat/documents/import", response_model=DocumentItem)
async def import_document(request: MarkdownImportRequest, _: Principal = Depends(require_permission("chat:knowledge:import"))):
    try:
        upload = await asyncio.to_thread(save_markdown, request.filename, request.contentBase64, request.title)
        document = await asyncio.to_thread(
            agent_store.create_document,
            upload.filename,
            upload.title,
            request.category,
            upload.checksum,
            upload.storage_path,
        )
        job_id = await asyncio.to_thread(agent_store.create_ingestion_job, document.id)
        await asyncio.to_thread(enqueue_ingestion, document.id, job_id, ingestion)
        return await asyncio.to_thread(agent_store.get_document, document.id)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from None


@app.post("/api/chat/documents/retrieval-test", response_model=RetrievalTestResponse)
async def test_document_retrieval(request: RetrievalTestRequest, _: Principal = Depends(require_permission("chat:knowledge:manage"))):
    contexts = await asyncio.to_thread(retriever.search, request.query.strip(), request.limit)
    return RetrievalTestResponse(query=request.query.strip(), sources=[item.source for item in contexts])


@app.get("/api/chat/documents/{document_id}/chunks", response_model=list[DocumentChunkItem])
async def document_chunks(document_id: int, _: Principal = Depends(require_permission("chat:knowledge:manage"))):
    try:
        await asyncio.to_thread(agent_store.get_document, document_id)
        return await asyncio.to_thread(agent_store.list_document_chunks, document_id)
    except KeyError:
        raise HTTPException(status_code=404, detail="document not found") from None


@app.post("/api/chat/documents/{document_id}/replace", response_model=DocumentItem)
async def replace_document(
    document_id: int,
    request: MarkdownImportRequest,
    _: Principal = Depends(require_permission("chat:knowledge:import")),
):
    try:
        current = await asyncio.to_thread(agent_store.get_document, document_id)
        upload = await asyncio.to_thread(save_markdown, request.filename, request.contentBase64, request.title)
        document = await asyncio.to_thread(
            agent_store.create_document,
            current.filename,
            request.title.strip() or upload.title or current.title,
            request.category.strip() or current.category,
            upload.checksum,
            upload.storage_path,
        )
        job_id = await asyncio.to_thread(agent_store.create_ingestion_job, document.id)
        await asyncio.to_thread(enqueue_ingestion, document.id, job_id, ingestion)
        return await asyncio.to_thread(agent_store.get_document, document.id)
    except KeyError:
        raise HTTPException(status_code=404, detail="document not found") from None
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from None


@app.post("/api/chat/documents/{document_id}/enabled", response_model=DocumentItem)
async def set_document_enabled(document_id: int, enabled: bool = Query(...), _: Principal = Depends(require_permission("chat:knowledge:manage"))):
    try:
        return await asyncio.to_thread(agent_store.set_document_enabled, document_id, enabled)
    except KeyError:
        raise HTTPException(status_code=404, detail="document not found") from None
    except ValueError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from None


@app.post("/api/chat/documents/{document_id}/reindex", response_model=DocumentItem)
async def reindex_document(document_id: int, _: Principal = Depends(require_permission("chat:knowledge:manage"))):
    try:
        await asyncio.to_thread(agent_store.get_document, document_id)
        job_id = await asyncio.to_thread(agent_store.create_ingestion_job, document_id)
        await asyncio.to_thread(enqueue_ingestion, document_id, job_id, ingestion)
        return await asyncio.to_thread(agent_store.get_document, document_id)
    except KeyError:
        raise HTTPException(status_code=404, detail="document not found") from None


@app.delete("/api/chat/documents/{document_id}")
async def delete_document(document_id: int, _: Principal = Depends(require_permission("chat:knowledge:delete"))):
    try:
        record = await asyncio.to_thread(agent_store.get_document_record, document_id)
        await asyncio.to_thread(vector_store.delete_document, document_id)
        await asyncio.to_thread(agent_store.delete_document, document_id)
        path = Path(record["storage_path"])
        if path.is_file():
            path.unlink()
    except KeyError:
        raise HTTPException(status_code=404, detail="document not found") from None
    return {"deleted": True}


@app.get("/api/chat/knowledge", response_model=KnowledgeList)
async def list_knowledge(
    keyword: str = "",
    category: str = "",
    includeDisabled: bool = Query(default=False),
    _: Principal = Depends(require_permission("chat:knowledge:delete")),
):
    items, total = store.list_knowledge(keyword=keyword.strip(), category=category.strip(), include_disabled=includeDisabled)
    metrics.record_knowledge_operation("list")
    return KnowledgeList(items=items, total=total)


@app.post("/api/chat/knowledge/import", response_model=KnowledgeImportResponse)
async def import_knowledge(request: KnowledgeImportRequest, _: Principal = Depends(require_permission("chat:knowledge:import"))):
    try:
        result = import_knowledge_file(store, request.filename, request.contentBase64)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from None
    metrics.record_knowledge_operation("import")
    return result


@app.get("/api/chat/knowledge/{item_id}", response_model=KnowledgeItem)
async def get_knowledge(item_id: int, _: Principal = Depends(require_permission("chat:knowledge:manage"))):
    item = store.get_knowledge(item_id)
    if item is None:
        raise HTTPException(status_code=404, detail="knowledge item not found")
    metrics.record_knowledge_operation("get")
    return item


@app.post("/api/chat/knowledge/create", response_model=KnowledgeItem)
async def create_knowledge(request: KnowledgeCreate, _: Principal = Depends(require_permission("chat:knowledge:manage"))):
    item = store.create_knowledge(request)
    metrics.record_knowledge_operation("create")
    return item


@app.put("/api/chat/knowledge/{item_id}", response_model=KnowledgeItem)
async def update_knowledge(item_id: int, request: KnowledgeUpdate, _: Principal = Depends(require_permission("chat:knowledge:manage"))):
    try:
        item = store.update_knowledge(item_id, request)
        metrics.record_knowledge_operation("update")
        return item
    except KeyError:
        raise HTTPException(status_code=404, detail="knowledge item not found") from None


@app.delete("/api/chat/knowledge/{item_id}")
async def delete_knowledge(item_id: int, _: Principal = Depends(require_permission("chat:knowledge:delete"))):
    try:
        store.delete_knowledge(item_id)
        metrics.record_knowledge_operation("delete")
    except KeyError:
        raise HTTPException(status_code=404, detail="knowledge item not found") from None
    return {"deleted": True}


@app.exception_handler(HTTPException)
async def http_exception_handler(request, exc: HTTPException):
    message = exc.detail if isinstance(exc.detail, str) else "request failed"
    return JSONResponse(status_code=exc.status_code, content={"code": exc.status_code, "message": message})


@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    return JSONResponse(status_code=500, content={"code": 500, "message": "chat service internal error"})


def _sse(event: str, payload) -> str:
    return f"event: {event}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"
