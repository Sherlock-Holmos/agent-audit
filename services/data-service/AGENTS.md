# Data Service AGENTS

## 1. 架构定位
- 职责：数据源接入、清洗/融合任务、驾驶舱聚合、异步作业、NiFi 控制平面、整改业务域。
- 角色：核心业务服务，承载 `/api/data/**` 主体能力。
- 分层原则：遵循编排层 + 领域层 + 基础设施层分离，参考 docs/08-data-service-layering.md。

## 2. 技术栈
- 语言与运行时：Java 21
- 框架：Spring Boot 3.3.x（Web / Validation / JDBC / Actuator）
- 数据与缓存：MySQL、Redis
- 观测：Micrometer + Actuator
- 文件处理：Apache POI（Excel）

## 3. API 与接口层
- 驾驶舱：`/api/data/dashboard`、`/api/data/trend`、`/api/data/heatmap`
- 数据源：`/api/data/sources`（查询、创建、状态、删除）
- 清洗任务：`/api/data/clean/tasks`（CRUD、运行、预览、异步）
- 融合任务：`/api/data/fusion/tasks`（CRUD、运行、预览、异步）
- 清洗规则/策略：`/api/data/clean/rules`、`/api/data/clean/strategies`
- 融合同义词：`/api/data/fusion/key-synonyms`（含历史）
- NiFi 控制平面：`/api/data/control-plane/nifi/**`
- 分层统计：`/api/data/control-plane/layers/stats`
- 整改域：`/api/data/rectification/**`
- 作业状态：`/api/data/jobs/{jobId}`

## 4. 关键配置
- 端口：`server.port`（默认 8082）
- 数据库：`SPRING_DATASOURCE_*`
- Redis：`SPRING_DATA_REDIS_*`
- 线程池：`TASK_CORE_POOL_SIZE`、`TASK_MAX_POOL_SIZE`、`TASK_QUEUE_CAPACITY`
- 上传与分层：`app.datasource.upload-dir`、`app.datasource.staging-schema`
- 融合同义词扩展：`APP_FUSION_KEY_SYNONYMS_JSON`
- NiFi：`APP_NIFI_*`（启用、地址、超时、自动对账策略）

## 5. 设计规则
- Controller 仅做协议层职责，参数校验和响应封装要收敛，业务编排下沉到 application/service。
- 事务边界放在应用服务层，避免跨仓储更新出现部分成功。
- 异步任务必须支持幂等（`Idempotency-Key`）与状态机闭环（QUEUED/RUNNING/COMPLETED/FAILED）。
- 数据库 schema 变更要可增量兼容，避免破坏存量环境。
- 所有外部调用（NiFi/下游）必须设置超时、失败兜底和可观测日志。
- 中文字段与文件读写统一 UTF-8，避免乱码，特别是 SQL 连接与文件输出。
- 对账、补偿、回放等高风险操作必须保留审计记录。
- 新功能优先补充 API 文档与最小回归验证步骤；变更 `rectification`、`nifi`、`task` 等核心域时，必须验证关键链路可用。
- 保持接口向后兼容；若必须破坏性变更，需提供迁移说明。
