# Agent 服务接口文档

## 1. 服务职责
- 审计智能问答
- 基于 Redis 的会话历史管理与频控
- 基于 LangChain 的提示词编排与模型调用
- 为 RAG / 向量数据库预留检索扩展位

## 2. 基础信息
- 服务地址：`http://localhost:8083`
- 网关访问：`http://localhost:8081/api/agent/**`
- 运行栈：Python 3.12、FastAPI、LangChain

## 3. 已实现接口
1. `POST /api/agent/chat`

请求体示例：
```json
{
  "question": "本周整改薄弱环节是什么？"
}
```

响应字段：
- `question`
- `answer`
- `confidence`
- `historyTurns`
- `user`

## 4. 稳定性机制
- 基于 Redis 的用户限流。
- 下游 data-service 调用超时与重试。
- 下游失败时返回兜底数据，避免接口整体失败。
- 默认支持 `mock/openai/azure` 三类模型提供商切换。
- 预留 `VECTOR_STORE_TYPE` 配置，可启用 Chroma 或 pgvector 检索增强。

## 5. 观测端点
1. `GET /actuator/health`
2. `GET /actuator/info`
3. `GET /actuator/prometheus`
4. `GET /metrics`

主要指标：
- `audit_agent_chat_requests_total`
- `audit_agent_chat_rate_limited_total`
- `audit_agent_chat_duration_seconds`

## 6. 规划能力
- `POST /api/agent/chat/stream`（SSE 流式响应）
- `POST /api/agent/report/generate`
- `GET /api/agent/report/{reportId}`
