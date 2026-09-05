from datetime import datetime
from typing import Any, Dict, List, Literal, Optional

from pydantic import BaseModel, Field


class KnowledgeBase(BaseModel):
    question: str = Field(min_length=2, max_length=500)
    answer: str = Field(min_length=2, max_length=4000)
    category: str = Field(default="通用", max_length=80)
    tags: List[str] = Field(default_factory=list)
    enabled: bool = True


class KnowledgeCreate(KnowledgeBase):
    pass


class KnowledgeUpdate(KnowledgeBase):
    pass


class KnowledgeItem(KnowledgeBase):
    id: int
    createdAt: datetime
    updatedAt: datetime


class KnowledgeList(BaseModel):
    items: List[KnowledgeItem]
    total: int


class KnowledgeImportRequest(BaseModel):
    filename: str = Field(min_length=1, max_length=255)
    contentBase64: str = Field(min_length=1)


class KnowledgeImportError(BaseModel):
    row: int
    message: str


class KnowledgeImportResponse(BaseModel):
    totalRows: int
    imported: int
    skipped: int
    failed: int
    errors: List[KnowledgeImportError]


class AskRequest(BaseModel):
    question: str = Field(min_length=1, max_length=1000)
    sessionId: Optional[str] = Field(default=None, max_length=80)


class AskSource(BaseModel):
    id: int
    question: str
    category: str
    score: float
    sourceType: str = "faq"
    documentId: Optional[int] = None
    title: Optional[str] = None


class AskResponse(BaseModel):
    answer: str
    source: str
    sources: List[AskSource]
    sessionId: str
    messageId: Optional[int] = None
    runId: Optional[int] = None
    toolCalls: List["ToolCallView"] = Field(default_factory=list)


class ChatStatus(BaseModel):
    enabled: bool
    mode: Literal["knowledge_only", "agent"]
    deepseekEnabled: bool
    vectorEnabled: bool
    serviceHours: str
    hotline: str


class ChatSettingsUpdate(BaseModel):
    enabled: bool
    mode: Literal["knowledge_only", "agent"]
    deepseekEnabled: bool
    allowedTools: List[str]
    systemPrompt: str = Field(min_length=20, max_length=6000)
    systemPromptVersion: str = Field(min_length=1, max_length=80)
    model: str = Field(min_length=1, max_length=100)
    temperature: float = Field(ge=0, le=1.5)
    maxTokens: int = Field(ge=128, le=4000)
    hotline: str = Field(min_length=3, max_length=40)
    serviceHours: str = Field(min_length=1, max_length=80)
    handoffMessage: str = Field(min_length=3, max_length=500)


class ChatSettings(ChatSettingsUpdate):
    updatedBy: Optional[int] = None
    updatedAt: datetime


class ConversationCreate(BaseModel):
    title: str = Field(default="新会话", min_length=1, max_length=120)


class ConversationItem(BaseModel):
    id: str
    title: str
    status: str
    summary: str = ""
    createdAt: datetime
    updatedAt: datetime


class MessageItem(BaseModel):
    id: int
    conversationId: str
    role: Literal["user", "assistant", "tool"]
    content: str
    source: str = ""
    sources: List[AskSource] = Field(default_factory=list)
    createdAt: datetime


class ConversationMessages(BaseModel):
    conversation: ConversationItem
    messages: List[MessageItem]


class FeedbackCreate(BaseModel):
    messageId: int
    rating: Literal[-1, 1]
    comment: str = Field(default="", max_length=1000)


class FeedbackItem(FeedbackCreate):
    id: int
    createdAt: datetime


class MarkdownImportRequest(BaseModel):
    filename: str = Field(min_length=1, max_length=255)
    contentBase64: str = Field(min_length=1)
    title: str = Field(default="", max_length=255)
    category: str = Field(default="文档", max_length=80)


class DocumentItem(BaseModel):
    id: int
    filename: str
    title: str
    category: str
    version: int
    checksum: str
    status: str
    enabled: bool
    chunkCount: int
    errorMessage: str = ""
    ingestionStatus: str = ""
    ingestionProgress: int = 0
    ingestionError: str = ""
    createdAt: datetime
    updatedAt: datetime


class DocumentList(BaseModel):
    items: List[DocumentItem]
    total: int


class DocumentChunkItem(BaseModel):
    id: int
    documentId: int
    chunkIndex: int
    heading: str
    content: str
    tokenEstimate: int
    vectorStatus: str


class RetrievalTestRequest(BaseModel):
    query: str = Field(min_length=1, max_length=1000)
    limit: int = Field(default=6, ge=1, le=20)


class RetrievalTestResponse(BaseModel):
    query: str
    sources: List[AskSource]


class ToolCallView(BaseModel):
    id: Optional[int] = None
    tool: str
    status: str
    summary: str = ""
    durationMs: int = 0


class AgentRunItem(BaseModel):
    id: int
    conversationId: str
    status: str
    mode: str
    model: str
    steps: int
    durationMs: int
    errorMessage: str = ""
    createdAt: datetime


class AdminChatOverview(BaseModel):
    conversations: int
    messages: int
    documents: int
    readyDocuments: int
    feedbackPositive: int
    feedbackNegative: int
    asks: int
    knowledgeHitRate: float
    fallbackAnswers: int
    recentRuns: List[AgentRunItem]


class ToolExecutionResult(BaseModel):
    ok: bool
    data: Any = None
    summary: str
