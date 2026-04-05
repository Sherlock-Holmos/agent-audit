# Data 服务接口文档

## 1. 服务职责
- 数据源接入与管理
- 清洗任务与融合任务管理
- 驾驶舱数据聚合（dashboard/trend/heatmap）
- 异步作业执行与状态查询

## 2. 基础信息
- 服务地址：`http://localhost:8082`
- 网关访问：`http://localhost:8081/api/data/**`

## 3. 驾驶舱接口
1. `GET /api/data/dashboard`
2. `GET /api/data/trend`
3. `GET /api/data/heatmap`
4. `GET /api/data/dashboard/fusion-options`

说明：支持 `fusionTaskId` 参数按融合任务维度查询。

## 4. 数据源接口
1. `GET /api/data/sources`
2. `POST /api/data/sources/database`
3. `POST /api/data/sources/file`
4. `PATCH /api/data/sources/{id}/status`
5. `DELETE /api/data/sources/{id}`

## 5. 清洗规则与策略接口
1. `GET /api/data/clean/rules`
2. `POST /api/data/clean/rules`
3. `GET /api/data/clean/rules/{id}`
4. `PATCH /api/data/clean/rules/{id}`
5. `PATCH /api/data/clean/rules/{id}/enabled`
6. `DELETE /api/data/clean/rules/{id}`
7. `GET /api/data/clean/strategies`
8. `POST /api/data/clean/strategies`
9. `GET /api/data/clean/strategies/{id}`
10. `PATCH /api/data/clean/strategies/{id}`
11. `PATCH /api/data/clean/strategies/{id}/enabled`
12. `DELETE /api/data/clean/strategies/{id}`

## 6. 融合主键同义词接口
1. `GET /api/data/fusion/key-synonyms`
2. `POST /api/data/fusion/key-synonyms`
3. `GET /api/data/fusion/key-synonyms/{id}`
4. `PATCH /api/data/fusion/key-synonyms/{id}`
5. `PATCH /api/data/fusion/key-synonyms/{id}/enabled`
6. `DELETE /api/data/fusion/key-synonyms/{id}`
7. `GET /api/data/fusion/key-synonyms/{id}/history`
8. `GET /api/data/fusion/key-synonyms/history?canonicalKey=...`

## 7. 任务接口（同步）
1. `GET /api/data/clean/tasks`
2. `POST /api/data/clean/tasks`
3. `PATCH /api/data/clean/tasks/{id}`
4. `POST /api/data/clean/tasks/{id}/run`
5. `GET /api/data/clean/tasks/{id}/preview`
6. `DELETE /api/data/clean/tasks/{id}`
7. `GET /api/data/fusion/tasks`
8. `POST /api/data/fusion/tasks`
9. `PATCH /api/data/fusion/tasks/{id}`
10. `POST /api/data/fusion/tasks/{id}/run`
11. `GET /api/data/fusion/tasks/{id}/preview`
12. `DELETE /api/data/fusion/tasks/{id}`

## 8. 任务接口（异步）
1. `POST /api/data/clean/tasks/{id}/run-async`
2. `POST /api/data/fusion/tasks/{id}/run-async`
3. `GET /api/data/jobs/{jobId}`

说明：
- 可通过请求头 `Idempotency-Key` 实现幂等提交。
- 异步状态：`QUEUED`、`RUNNING`、`COMPLETED`、`FAILED`。

## 9. 观测端点
1. `GET /actuator/health`
2. `GET /actuator/metrics`
3. `GET /actuator/prometheus`

## 10. 控制平面（NiFi）接口
1. `GET /api/data/control-plane/nifi/status`
2. `POST /api/data/control-plane/nifi/flows/run`
3. `GET /api/data/control-plane/nifi/flows`
4. `GET /api/data/control-plane/nifi/templates`
5. `POST /api/data/control-plane/nifi/templates`
6. `POST /api/data/control-plane/nifi/templates/bootstrap`
7. `POST /api/data/control-plane/nifi/flows/provision`
8. `POST /api/data/control-plane/nifi/tasks/reconcile`
9. `POST /api/data/control-plane/nifi/tasks/reconcile/one`
10. `GET /api/data/control-plane/nifi/tasks/reconcile/history`
11. `GET /api/data/control-plane/layers/stats`

说明：
- `POST /api/data/control-plane/nifi/flows/run` 请求体示例：

```json
{
	"flowType": "INGEST",
	"processGroupId": "<your-process-group-id>",
	"parameters": {
		"sourceId": 1001,
		"triggerBy": "Holmes"
	}
}
```

- 每次触发都会写入 `nifi_flow_run_record`，可用于审计追溯。
- 可通过模板接口配置 `flowType -> processGroupId` 与 `parameterSchema.requiredKeys`，触发时将自动执行必填参数校验并返回 `templateVersion`。
- `POST /api/data/control-plane/nifi/templates/bootstrap` 会自动创建 CLEAN/FUSION 默认蓝图，内部通过 `APP_NIFI_DATA_SERVICE_BASE_URL` 调用 data-service 的清洗/融合执行接口。
- `POST /api/data/control-plane/nifi/flows/provision` 支持 `preset` 字段，值可为 `CLEAN` / `FUSION`，用于直接生成默认蓝图。
- `GET /api/data/control-plane/layers/stats` 支持按 `taskType`、`taskId` 过滤并返回 Bronze/Silver/Gold 行数汇总与任务明细。
