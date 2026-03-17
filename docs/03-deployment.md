# 部署与环境手册

## 1. 环境分层
- DEV：本地开发/联调。
- SIT：集成测试。
- UAT：业务验收。
- PROD：生产环境。

## 2. 依赖组件
- MySQL 8
- Redis 7
- Nginx
- Gateway + 4 个业务服务
- Prometheus + Grafana（建议全环境启用）

## 3. 本地部署
```bash
docker compose up -d mysql redis auth-service data-service config-service agent-service gateway
```

前端开发：
```bash
cd frontend
npm install
npm run dev
```

## 4. 全量启动（含监控）
```bash
docker compose up -d
```

## 5. 核心环境变量
- 网关：
  - `GATEWAY_RATE_LIMIT_PER_MINUTE`
  - `GATEWAY_IP_RATE_LIMIT_PER_MINUTE`
- data-service：
  - `SPRING_DATASOURCE_URL`
  - `SPRING_DATA_REDIS_HOST`
  - `TASK_CORE_POOL_SIZE`
  - `TASK_MAX_POOL_SIZE`
  - `TASK_QUEUE_CAPACITY`
- agent-service：
  - `DATA_BASE_URL`
  - `REDIS_HOST`
  - `REDIS_PORT`
  - `AGENT_RATE_LIMIT_PER_MINUTE`
  - `AGENT_SESSION_TTL_MINUTES`
  - `AGENT_MAX_SESSION_TURNS`
  - `AGENT_DASHBOARD_TIMEOUT_MS`
  - `AGENT_DASHBOARD_RETRY_TIMES`
  - `LLM_PROVIDER`
  - `OPENAI_API_KEY` / `OPENAI_MODEL`
  - `AZURE_OPENAI_API_KEY` / `AZURE_OPENAI_ENDPOINT` / `AZURE_OPENAI_DEPLOYMENT`
  - `VECTOR_STORE_TYPE`
  - `CHROMA_HOST` / `CHROMA_PORT`
  - `PGVECTOR_DSN`

## 6. 发布建议流程
1. 构建：Java 服务执行 `mvn -DskipTests compile`，agent-service 执行 `pip install -r requirements.txt` 并启动 `uvicorn app.main:app --host 0.0.0.0 --port 8083` 验证。
2. 镜像：`docker compose build`。
3. 灰度：先 gateway + 单个业务服务。
4. 验证：健康检查、核心接口冒烟、指标无异常。
5. 全量：逐步放量并观察 15-30 分钟。

## 7. Agent 服务专项说明
- 默认 `LLM_PROVIDER=mock`，本地可在不配置模型密钥的情况下完成联调。
- 生产接入真实模型时，建议优先使用环境变量注入密钥，不要写入仓库文件。
- 启用 RAG 时，将 `VECTOR_STORE_TYPE` 改为 `chroma` 或 `pgvector`，并同步提供连接参数。

## 8. 回滚策略
- 镜像回滚到上一个稳定 tag。
- 必要时回滚数据库变更（需提前准备回滚脚本）。
- 监控确认错误率恢复后再开放流量。
