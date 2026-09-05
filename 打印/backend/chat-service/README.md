# chat-service

AI 站内客服 Agent，默认端口 `8093`，通过 Gateway 暴露 `/api/chat/**`。

功能：

- `POST /api/chat/ask` 用户提问，优先匹配知识库；配置 `DEEPSEEK_API_KEY` 后可调用 DeepSeek 生成回答。
- `GET/POST/PUT/DELETE /api/chat/knowledge*` 管理员维护知识库。
- `POST /api/chat/knowledge/import` 管理员批量导入 CSV/XLSX FAQ，字段支持 `question/answer/category/tags/enabled` 或中文表头 `问题/答案/分类/标签/状态`。
- `GET /api/chat/metrics` 返回 JSON 指标，`GET /metrics` 返回 Prometheus 文本指标。
- 可选 Nacos 注册，服务名默认 `chat-service`。
- 服务自身可校验平台 JWT，生产保持 `CHAT_AUTH_ENABLED=true`。
- 管理端可持久化启停客服、切换知识库/Agent 模式并维护工具白名单。
- 支持 Markdown 文档版本、分块、本地 BGE Embedding、Qdrant 混合检索和 Celery 后台摄取。
- 支持持久化多轮会话、会话摘要、SSE 状态流、回答反馈和 Agent 工具审计。
- 只读站内工具通过 Gateway 转发当前 JWT，模型不能传入用户 ID 或任意 URL。

完整运维说明见 `../docs/chat-agent-operations.md`。

语法自检：

```bash
python -m compileall app
```

压测：

```bash
python ../scripts/chat_pressure_test.py --base-url http://localhost:8093 --jwt-secret <same-as-platform-jwt-secret>
```
