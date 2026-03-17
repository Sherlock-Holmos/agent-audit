# 运维与可观测性手册

## 1. 健康检查
- Gateway: `/actuator/health`
- Data Service: `/actuator/health`
- Agent Service: `/actuator/health`

## 2. 指标端点
- Gateway: `/actuator/prometheus`
- Data Service: `/actuator/prometheus`
- Agent Service: `/actuator/prometheus`

## 3. 重点观测指标
- 网关：
  - 429 次数（限流触发）
  - 401 次数（鉴权失败）
  - 请求耗时分位
- data-service：
  - `audit.cache.hit` / `audit.cache.miss`
  - `audit.dashboard.build.duration`
  - `audit.process.job.completed` / `audit.process.job.failed`
  - `audit.process.job.duration`
- agent-service：
  - `audit_agent_chat_requests_total`
  - `audit_agent_chat_rate_limited_total`
  - `audit_agent_chat_duration_seconds`

## 4. 日志规范
- 各服务均输出 JSON 风格日志。
- 建议日志平台按字段聚合：`ts`、`level`、`app`、`traceId`、`msg`。
- agent-service 当前日志字段不自动透传 `traceId`，如需端到端追踪需后续补充中间件。
- 生产环境建议保留至少 15 天可检索日志。

## 5. 常见故障处理
1. 大量 401：检查 token 过期策略与网关白名单配置。
2. 大量 429：检查突发流量与限流阈值，必要时临时提高阈值。
3. data-service 慢：先看 MySQL 慢查询与 Redis 命中率。
4. agent-service 响应慢：检查 data-service 下游超时与重试配置、外部 LLM 延迟与向量库检索耗时。

## 6. 巡检清单（每日）
- 核心服务健康状态。
- 错误率与 P95 延迟。
- Redis 可用率与命中率趋势。
- 异步任务失败数是否异常上升。
