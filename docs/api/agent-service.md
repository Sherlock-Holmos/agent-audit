# Agent 服务接口文档

## 1. 服务职责
- 审计智能问答
- 基于 Redis 的会话历史管理与频控
- 基于 LangChain 的提示词编排与模型调用
- 为 RAG / 向量数据库预留检索扩展位

## 2. 基础信息
- 服务地址：`http://localhost:18083`
- 网关访问：`http://localhost:18081/api/agent/**`
- 运行栈：Python 3.12、FastAPI、LangChain

## 3. 已实现接口
1. `POST /api/agent/chat`
2. `POST /api/agent/chat/stream`（SSE 流式返回）

请求体示例：
```json
{
  "question": "本周整改薄弱环节是什么？",
  "llmConfig": {
    "provider": "custom",
    "model": "Pro/zai-org/GLM-4.7",
    "apiKey": "YOUR_API_KEY",
    "baseUrl": "https://api.siliconflow.cn/v1",
    "apiVersion": ""
  }
}
```

`llmConfig` 为可选字段：
- `provider`: `mock | openai | azure | custom`
- `model`: 模型名称或部署名
- `apiKey`: 模型平台 API Key
- `baseUrl`: OpenAI 兼容网关地址（如 siliconflow）或 Azure endpoint
- `apiVersion`: Azure OpenAI API 版本（可选）
- 安全校验：仅允许上述字段；`baseUrl` 必须为合法 `http/https`，默认拒绝本地/内网地址（可通过 `AGENT_ALLOW_PRIVATE_BASE_URL=true` 放开）

响应字段：
- `question`
- `answer`
- `confidence`
- `historyTurns`
- `user`

流式接口事件格式（`text/event-stream`）：
- `{"type":"chunk","content":"..."}`：增量文本分片
- `{"type":"heartbeat","ts":...}`：连接保活心跳（客户端可忽略）
- `{"type":"final", ...}`：最终结果，字段同普通接口（服务端会对分片合并结果做一次空值/异常兜底后返回）
- `{"type":"error","code":"...","message":"...","retryable":true|false}`：错误事件
- `[DONE]`：结束标记

`error.code` 约定：
- `rate_limit`：触发频控限制
- `stream_timeout`：流式生成超时
- `upstream_error`：LLM 上游服务异常
- `http_error` / `stream_error`：其他异常

## 4. 稳定性机制
- 基于 Redis 的用户限流。
- 下游 data-service 调用超时与重试。
- 下游失败时返回兜底数据，避免接口整体失败。
- 流式接口支持周期心跳，降低代理层空闲断连风险。
- 流式接口带最大持续时长保护，超时后返回 `error` 事件。
- 默认支持 `mock/openai/azure` 三类模型提供商切换。
- 预留 `VECTOR_STORE_TYPE` 配置，可启用 Chroma 或 pgvector 检索增强。

## 5. 观测端点
1. `GET /actuator/health`
2. `GET /actuator/info`

## 6. 规划能力
- `POST /api/agent/report/generate`
- `GET /api/agent/report/{reportId}`
