# 部署与环境手册

## 1. 环境分层
- DEV：本地开发/联调。
- SIT：集成测试。
- UAT：业务验收。
- PROD：生产环境。

## 2. 依赖组件
- MySQL 8
- Redis 7
- Apache NiFi 1.28+
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
  - `APP_FUSION_KEY_SYNONYMS_JSON`（融合主键同义词 JSON，支持扩展映射）
  - `APP_NIFI_ENABLED`（是否启用 NiFi 编排）
  - `APP_NIFI_BASE_URL`（NiFi 控制平面地址，如 `http://nifi:8080`）
  - `APP_NIFI_DEFAULT_PROCESS_GROUP_ID`（默认流程组 ID）
  - `TASK_CORE_POOL_SIZE`
  - `TASK_MAX_POOL_SIZE`
  - `TASK_QUEUE_CAPACITY`
- nifi：
  - `NIFI_USERNAME`
  - `NIFI_PASSWORD`
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

### 6.1 融合主键同义词配置示例
可通过环境变量扩展 `KEY_ALIGN` 主键识别的同义字段：

```bash
APP_FUSION_KEY_SYNONYMS_JSON={"整改单位ID":["单位ID","org_id","organization_id"],"整改事项ID":["事项ID","issue_id","rect_id"],"整改批次号":["批次号","batch_no"]}
```

生效方式：
1. 设置环境变量后重启 `data-service`。
2. 查看 `data-service` 启动日志，确认出现 `Fusion key-synonyms loaded`。
3. 运行融合任务（`KEY_ALIGN`），验证新增同义字段可匹配。

### 6.2 NiFi 控制平面接入说明
1. 在环境变量中启用 `APP_NIFI_ENABLED=true`。
2. 配置 `APP_NIFI_BASE_URL` 与 `APP_NIFI_DEFAULT_PROCESS_GROUP_ID`。
3. 建议先通过 `POST /api/data/control-plane/nifi/templates/bootstrap` 一键生成 CLEAN/FUSION 原生（ExecuteScript）蓝图和模板。
4. 使用接口 `POST /api/data/control-plane/nifi/flows/run` 触发流程。
5. 使用接口 `POST /api/data/control-plane/nifi/tasks/reconcile` 与 `POST /api/data/control-plane/nifi/tasks/repair-artifacts` 做运行态对账和产物补偿。
6. 使用接口 `GET /api/data/control-plane/nifi/flows` 查看触发历史。

### 6.3 Bronze/Silver/Gold 分层结果
清洗和融合任务执行成功后，系统会自动落表：
1. Bronze：`bronze_ingest_record`（原始记录快照）
2. Silver：`silver_standard_record`（标准化结果）
3. Gold：`gold_fusion_wide_record`（融合宽表结果）

运维建议：
1. 以任务粒度做归档和保留策略（按 `source_task_id`/`fusion_task_id`）。
2. 对 Gold 层建立业务索引或同步到分析引擎以优化驾驶舱查询。

## 7. Agent 服务专项说明
- 默认 `LLM_PROVIDER=mock`，本地可在不配置模型密钥的情况下完成联调。
- 生产接入真实模型时，建议优先使用环境变量注入密钥，不要写入仓库文件。
- 启用 RAG 时，将 `VECTOR_STORE_TYPE` 改为 `chroma` 或 `pgvector`，并同步提供连接参数。

## 8. 回滚策略
- 镜像回滚到上一个稳定 tag。
- 必要时回滚数据库变更（需提前准备回滚脚本）。
- 监控确认错误率恢复后再开放流量。
