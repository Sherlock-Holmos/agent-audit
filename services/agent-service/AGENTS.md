# Agent Service AGENTS

## 1. 架构定位
- 职责：审计智能问答、会话管理、限流、模型编排。
- 角色：作为 AI 能力服务，提供 `/api/agent/**` 接口，由网关统一鉴权和路由。
- 依赖：上游为 Gateway；下游为 Redis（会话/频控）、Data Service（业务数据补充）和外部 LLM API。
- 扩展方向：支持 RAG 检索增强（Chroma / pgvector，当前默认关闭）。

## 2. 技术栈
- 语言与运行时：Python 3.12
- Web 框架：FastAPI + Uvicorn
- LLM 编排：LangChain、langchain-openai、langchain-community
- 基础设施：Redis、httpx
- 配置管理：pydantic-settings

## 3. API 与接口层
- `POST /api/agent/chat`：非流式问答
- `POST /api/agent/chat/stream`：SSE 流式问答
- `GET /actuator/health`：健康检查
- `GET /actuator/info`：服务信息
- 可选 `llmConfig` 字段：`provider/model/apiKey/baseUrl/apiVersion`
- provider 支持：`mock | openai | azure | custom`
- 流式事件约定：`chunk`、`heartbeat`、`final`、`error`、`[DONE]`

## 4. 关键配置
- Redis：`REDIS_HOST`、`REDIS_PORT`
- 下游 Data Service：`DATA_BASE_URL`、`AGENT_DASHBOARD_TIMEOUT_MS`、`AGENT_DASHBOARD_RETRY_TIMES`
- 会话与限流：`AGENT_SESSION_TTL_MINUTES`、`AGENT_MAX_SESSION_TURNS`、`AGENT_RATE_LIMIT_PER_MINUTE`
- LLM：`LLM_PROVIDER`、`OPENAI_*`、`AZURE_OPENAI_*`
- 向量检索：`VECTOR_STORE_TYPE`、`CHROMA_*`、`PGVECTOR_DSN`

## 5. 设计规则
- 统一经网关访问，不在客户端直连本服务端口。
- Chat 路由只负责协议转换与校验，模型调用逻辑下沉到 service 层。
- 下游调用必须有超时、重试与降级策略，避免链路雪崩。
- 流式接口必须输出心跳并限制最大执行时长，避免代理超时断连。
- 不在日志打印明文 API Key、Token、敏感业务数据。
- 结构化 JSON 日志保持字段稳定（`ts/level/app/logger/msg`）。
- 新增 provider 时保持 `mock/openai/azure/custom` 兼容约定，避免破坏前端联调。
