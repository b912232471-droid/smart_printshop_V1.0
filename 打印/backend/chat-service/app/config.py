import os
from pydantic_settings import BaseSettings, SettingsConfigDict


def _bool_env(name: str, default: str = "false") -> bool:
    return os.getenv(name, default).lower() in {"1", "true", "yes", "on"}


class Settings(BaseSettings):
    model_config = SettingsConfigDict(case_sensitive=True)

    APP_NAME: str = "AI智能客服服务"
    APP_VERSION: str = "1.0.0"
    SERVICE_NAME: str = os.getenv("CHAT_SERVICE_NAME", "chat-service")
    SERVICE_PORT: int = int(os.getenv("CHAT_PORT", "8093"))

    DATABASE_URL: str = os.getenv("CHAT_DATABASE_URL", "sqlite:///./chatbot.sqlite3")
    SEED_DEFAULTS: bool = _bool_env("CHAT_SEED_DEFAULTS", "true")

    AUTH_ENABLED: bool = _bool_env("CHAT_AUTH_ENABLED", "true")
    JWT_SECRET: str = os.getenv("CHAT_JWT_SECRET", os.getenv("PRINTSHOP_JWT_SECRET", ""))

    # 通用 OpenAI 兼容 LLM 接入（客服 Agent 与问答；旧 DEEPSEEK_* 变量名保留回退，历史命名已废弃）
    CHAT_LLM_API_KEY: str = os.getenv("CHAT_LLM_API_KEY", os.getenv("DEEPSEEK_API_KEY", ""))
    CHAT_LLM_BASE_URL: str = os.getenv("CHAT_LLM_BASE_URL", os.getenv("DEEPSEEK_BASE_URL", "https://tokenhub.tencentmaas.com/v1"))
    CHAT_LLM_MODEL: str = os.getenv("CHAT_LLM_MODEL", os.getenv("DEEPSEEK_MODEL", "hy3"))
    CHAT_LLM_TIMEOUT_SECONDS: int = int(os.getenv("CHAT_LLM_TIMEOUT_SECONDS", os.getenv("DEEPSEEK_TIMEOUT_SECONDS", "25")))

    REDIS_URL: str = os.getenv("CHAT_REDIS_URL", "redis://redis:6379/2")
    QDRANT_URL: str = os.getenv("CHAT_QDRANT_URL", "http://qdrant:6333")
    QDRANT_COLLECTION: str = os.getenv("CHAT_QDRANT_COLLECTION", "printshop_knowledge")
    VECTOR_ENABLED: bool = _bool_env("CHAT_VECTOR_ENABLED", "false")
    VECTOR_REQUIRED: bool = _bool_env("CHAT_VECTOR_REQUIRED", "false")
    EMBEDDING_MODEL: str = os.getenv("CHAT_EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5")
    EMBEDDING_CACHE_DIR: str = os.getenv("CHAT_EMBEDDING_CACHE_DIR", "/models/fastembed")
    DOCUMENT_DIR: str = os.getenv("CHAT_DOCUMENT_DIR", "./data/documents")
    MAX_MARKDOWN_BYTES: int = int(os.getenv("CHAT_MAX_MARKDOWN_BYTES", str(5 * 1024 * 1024)))
    CHUNK_SIZE: int = int(os.getenv("CHAT_CHUNK_SIZE", "900"))
    CHUNK_OVERLAP: int = int(os.getenv("CHAT_CHUNK_OVERLAP", "120"))
    INGESTION_INLINE: bool = _bool_env("CHAT_INGESTION_INLINE", "true")

    INTERNAL_GATEWAY_URL: str = os.getenv("CHAT_INTERNAL_GATEWAY_URL", "http://gateway:8080")
    TOOL_TIMEOUT_SECONDS: int = int(os.getenv("CHAT_TOOL_TIMEOUT_SECONDS", "8"))
    AGENT_MAX_STEPS: int = int(os.getenv("CHAT_AGENT_MAX_STEPS", "4"))
    HISTORY_LIMIT: int = int(os.getenv("CHAT_HISTORY_LIMIT", "12"))

    NACOS_ENABLED: bool = _bool_env("NACOS_ENABLED", "false")
    NACOS_SERVER_ADDR: str = os.getenv("NACOS_SERVER_ADDR", "127.0.0.1:8848")
    NACOS_NAMESPACE: str = os.getenv("NACOS_NAMESPACE", "public")
    NACOS_GROUP: str = os.getenv("NACOS_GROUP", "DEFAULT_GROUP")
    NACOS_IP: str = os.getenv("CHAT_NACOS_IP", "")
    NACOS_HEARTBEAT_SECONDS: int = int(os.getenv("CHAT_NACOS_HEARTBEAT_SECONDS", "10"))

settings = Settings()
