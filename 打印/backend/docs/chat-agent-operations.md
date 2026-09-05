# 站内客服 Agent 运维说明

## 1. 组件

- `chat-service`：问答 API、会话、策略、检索和受控工具编排。
- `chat-worker`：Celery 文档解析、分块、Embedding 和 Qdrant 写入。
- `chatbot_db`：策略、FAQ、文档元数据、知识块、会话、消息、Agent 审计和反馈。
- `Qdrant`：Markdown 知识块向量索引。
- `Redis`：Celery Broker 和任务结果。
- `DeepSeek`：Agent 决策与回答生成；未配置时降级为知识库回答。

## 2. 管理端

管理端 `/knowledge` 包含三个视图：

- 运行策略：全局启停、`knowledge_only/agent` 模式、DeepSeek、模型参数、工具白名单、系统提示词、系统提示词版本、人工转接提示语和人工客服配置。
- Markdown 文档：上传、替换、版本、启停、索引状态、摄取进度/错误、知识块预览、重建/重试、检索测试和删除。
- 人工 FAQ：单条维护和 CSV/XLSX 批量导入。

配置修改写入 `chat_setting_audits`。全局停用只阻止新的问答，健康检查和管理员维护接口保持可用。

管理端补充接口：

- `POST /api/chat/documents/{document_id}/replace`：按同文件名创建新版本并重新摄取。
- `POST /api/chat/documents/retrieval-test`：管理员测试 FAQ + Markdown 混合检索效果。

## 3. Markdown 摄取

生产默认异步执行：

1. API 校验 UTF-8 Markdown、5MB 大小上限并保存到 `/data/chat-documents`。
2. MySQL 创建带版本号的 `knowledge_documents` 和摄取任务。
3. Celery Worker 按 Markdown 标题层级解析并切分知识块。
4. FastEmbed 使用 `BAAI/bge-small-zh-v1.5` 生成 512 维向量。
5. Qdrant 写入向量，MySQL 将文档标记为 `ready`。

状态含义：

| 状态 | 含义 |
|---|---|
| `pending` | 等待 Worker |
| `processing` | 正在解析或向量化 |
| `ready` | 关键词和向量索引均可用 |
| `keyword_only` | 仅测试环境关键词索引 |
| `failed` | 摄取失败，管理端显示原因 |

生产保持 `CHAT_VECTOR_REQUIRED=true`，向量失败时必须标记失败。首次使用 Embedding 模型需要下载约 80MB，后续复用 `data/models/fastembed` 缓存。

## 4. Agent 工具

默认只读工具：

- `search_knowledge`（正式 Agent 工具，可审计；预检索仍会先执行一次）
- `get_my_order`
- `list_my_orders`
- `get_store_info`
- `get_service_price`
- `get_schedule_status`
- `get_photo_service_status`
- `handoff_to_human`（使用管理员维护的 `handoffMessage` + 电话/服务时间）

模型只能选择管理员允许的工具。工具执行层使用当前请求 JWT 调用 Gateway，用户 ID 由服务端 Principal 注入，不接受模型传入。订单、手机号、教务密码和 JWT 不写入模型参数；所有工具调用写入 `agent_tool_calls`。

## 5. 会话和反馈

- 会话与消息分别存储在 `chat_conversations`、`chat_messages`。
- 每轮只携带最近 `CHAT_HISTORY_LIMIT` 条消息，较早消息生成受限长度摘要。
- Web 使用 `/api/chat/ask/stream` SSE 接收 `status` / `tool` / `delta` / `message` / `done` / `error` 事件；`delta` 用于逐段追加文本，最终 `message` 补齐来源、工具调用和消息 ID。
- 用户可停止前端流、查看历史会话并对回答提交正负反馈。

## 6. 关键环境变量

```text
CHAT_QDRANT_URL=http://qdrant:6333
CHAT_QDRANT_COLLECTION=printshop_knowledge
CHAT_VECTOR_ENABLED=true
CHAT_VECTOR_REQUIRED=true
CHAT_EMBEDDING_MODEL=BAAI/bge-small-zh-v1.5
CHAT_MAX_MARKDOWN_BYTES=5242880
CHAT_CHUNK_SIZE=900
CHAT_CHUNK_OVERLAP=120
CHAT_INGESTION_INLINE=false
CHAT_INTERNAL_GATEWAY_URL=http://gateway:8080
CHAT_TOOL_TIMEOUT_SECONDS=8
CHAT_AGENT_MAX_STEPS=4
CHAT_HISTORY_LIMIT=12
DEEPSEEK_TIMEOUT_SECONDS=25
GATEWAY_CHAT_CB_TIMEOUT=90s
```

`DEEPSEEK_API_KEY` 仅通过环境变量注入。开发环境使用 `.env.local` 中的 `LOCAL_DEEPSEEK_API_KEY`，生产环境单独配置 `DEEPSEEK_API_KEY`。

## 7. 验证

```powershell
cd 打印\backend\chat-service
python -m compileall app

cd ..
docker compose --env-file .env.local -f docker-compose.middleware.yml -f docker-compose.yml -f docker-compose.dev.yml config --quiet
python scripts\production_readiness_check.py --env .env --backend-dir .
```

生产上线后验证：

- Qdrant `/healthz` 正常。
- `chat-worker` 正常连接 Redis，文档由 `pending` 进入 `ready`。
- `/api/chat/status` 与管理端策略一致。
- 普通用户无法访问设置、文档和 FAQ 管理接口。
- 普通用户无法通过订单工具查询其他用户订单。
- DeepSeek 不可用时仍能返回知识库答案或人工客服提示。
