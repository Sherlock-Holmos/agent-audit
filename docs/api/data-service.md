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

## 6. 任务接口（同步）
1. `GET /api/data/clean/tasks`
2. `POST /api/data/clean/tasks`
3. `POST /api/data/clean/tasks/{id}/run`
4. `DELETE /api/data/clean/tasks/{id}`
5. `GET /api/data/fusion/tasks`
6. `POST /api/data/fusion/tasks`
7. `POST /api/data/fusion/tasks/{id}/run`
8. `DELETE /api/data/fusion/tasks/{id}`

## 7. 任务接口（异步）
1. `POST /api/data/clean/tasks/{id}/run-async`
2. `POST /api/data/fusion/tasks/{id}/run-async`
3. `GET /api/data/jobs/{jobId}`

说明：
- 可通过请求头 `Idempotency-Key` 实现幂等提交。
- 异步状态：`QUEUED`、`RUNNING`、`COMPLETED`、`FAILED`。

## 8. 观测端点
1. `GET /actuator/health`
2. `GET /actuator/metrics`
3. `GET /actuator/prometheus`
