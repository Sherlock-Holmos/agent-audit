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

## 7. NIFI-Only 上线验收清单（10项）
1. 服务可达性
  - 检查 `gateway`、`data-service`、`nifi` 容器均为 `healthy` 或 `running`。
  - 通过标准：三者均可访问健康端点，无连接超时。

2. NiFi 控制面连通
  - 调用 `GET /api/data/control-plane/nifi/status`。
  - 通过标准：返回 `enabled=true` 且 `reachable=true`。

3. 模板与流程组就绪
  - 调用 `GET /api/data/control-plane/nifi/templates` 检查 `CLEAN`、`FUSION` 均存在且启用。
  - 通过标准：两类模板均有有效 `processGroupId`。

4. 原生蓝图检查
  - 必要时调用 `POST /api/data/control-plane/nifi/templates/bootstrap` 后重查模板。
  - 通过标准：流程组包含 `ExecuteScript` 原生处理器，不依赖 callback 轮询端点。

5. CLEAN 任务执行链路
  - 触发一个清洗任务运行，观察状态 `READY -> RUNNING -> COMPLETED`。
  - 通过标准：`clean_task_record.cleaned_rows > 0`，标准表存在对应数据。

6. FUSION 任务执行链路
  - 分别运行 `KEY_ALIGN` / `RULE_MATCH` / `TIME_WINDOW` 至少各 1 个任务。
  - 通过标准：`fusion_task_record.fusion_rows > 0`，目标融合表数据落地。

7. 分层数据落库
  - 调用 `GET /api/data/control-plane/layers/stats?taskType=CLEAN&taskId=<id>` 与 `taskType=FUSION`。
  - 通过标准：`bronze/silver/gold` 指标与任务产出规模一致，无全 0 异常。

8. 治理产物落库
  - 查询或调用接口检查 `lineage`、`quality`、`snapshots`。
  - 通过标准：任务完成后对应治理记录存在。

9. 历史补偿能力
  - 调用 `POST /api/data/control-plane/nifi/tasks/repair-artifacts`（可带 `taskType` 和 `limit`）。
  - 通过标准：返回统计正常，`repairedTotal` 与预期一致，无异常报错。

10. 运行态对账能力
  - 调用 `POST /api/data/control-plane/nifi/tasks/reconcile` 与 `GET /api/data/control-plane/nifi/tasks/reconcile/history`。
  - 通过标准：对账结果稳定，无持续 RUNNING 且未落表的挂起任务。

建议：
- 上线窗口先执行第 1-4 项（基础与链路就绪），再执行第 5-10 项（业务与治理闭环）。
- 若出现“任务完成但层/治理缺失”，先执行第 9 项补偿，再查看第 10 项对账记录定位原因。

### 7.1 一键巡检脚本（PowerShell）
脚本位置：`scripts/nifi-go-live-check.ps1`

示例（只读模式，不触发补偿/对账写操作）：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\nifi-go-live-check.ps1 -ReadOnly
```

示例（完整模式，包含补偿与对账）：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\nifi-go-live-check.ps1 `
  -GatewayBaseUrl http://localhost:18081 `
  -DataServiceBaseUrl http://localhost:18082 `
  -UserName Holmes `
  -BearerToken <JWT_TOKEN> `
  -CleanTaskId 1 `
  -FusionTaskId 1 `
  -OutputFile .\reports\nifi-go-live-check.json
```

示例（不手动粘贴 token，使用账号密码自动登录获取 JWT）：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\nifi-go-live-check.ps1 `
  -GatewayBaseUrl http://localhost:18081 `
  -DataServiceBaseUrl http://localhost:18082 `
  -UserName Holmes `
  -AuthPassword <USER_PASSWORD> `
  -CleanTaskId 1 `
  -FusionTaskId 1 `
  -OutputFile .\reports\nifi-go-live-check.json
```

示例（自动选择最近已完成的 CLEAN/FUSION 任务，不手填 TaskId）：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\nifi-go-live-check.ps1 `
  -GatewayBaseUrl http://localhost:18081 `
  -DataServiceBaseUrl http://localhost:18082 `
  -UserName Holmes `
  -AuthPassword <USER_PASSWORD> `
  -AutoSelectTaskIds `
  -OutputFile .\reports\nifi-go-live-check-auto.json
```

参数说明：
- `-ReadOnly`：只做读检查，跳过会写数据库状态的接口。
- `-BootstrapIfMissing`：模板缺失时自动调用 bootstrap（仅完整模式建议使用）。
- `-CleanTaskId` / `-FusionTaskId`：提供后会追加单任务检查。
- `-RepairLimit`：批量补偿扫描上限，默认 200。
- `-BearerToken`：网关开启鉴权时可传入 JWT，脚本会自动附加 `Authorization: Bearer <token>`。
- `-AuthPassword`：可选。与 `-UserName` 一起使用时，脚本会自动调用 `/api/auth/login` 获取 JWT（`-BearerToken` 仍优先）。
- `-AutoSelectTaskIds`：可选。未显式提供 `-CleanTaskId` / `-FusionTaskId` 时，自动选择最近一条 `COMPLETED` 任务。
- `-OutputFile`：将巡检结果保存为 JSON 报告（适合上线留档）。
