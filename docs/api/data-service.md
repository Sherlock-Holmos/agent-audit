# Data 服务接口文档

## 服务职责
- 数据驾驶舱统计
- 数据源管理（数据库与本地文件）
- 为清洗/融合流程提供数据接入基础

## 基础信息
- 服务地址：`http://localhost:8082`
- 网关访问：`http://localhost:8081/api/data/**`

## 已实现接口（驾驶舱）

### 1) 整改概览
- `GET /api/data/dashboard`
- 响应字段：`department`、`completedRate`、`overdueCount`、`departmentRank`

### 2) 趋势预测
- `GET /api/data/trend`
- 响应字段：`dates`、`rates`、`predicted`

### 3) 部门热力图
- `GET /api/data/heatmap`
- 响应字段：`departments`、`metrics`、`values`

## 已实现接口（数据源管理）

### 4) 查询数据源列表
- `GET /api/data/sources`
- Query：`keyword`（可选）、`type`（可选）、`status`（可选）

### 5) 新增数据库数据源
- `POST /api/data/sources/database`
- 请求体：
```json
{
  "name": "生产审计库",
  "dbType": "MYSQL",
  "host": "10.10.1.10",
  "port": 3306,
  "databaseName": "audit_prod",
  "username": "audit",
  "password": "******",
  "remark": "生产数据源"
}
```

### 6) 导入本地文件数据源
- `POST /api/data/sources/file`
- `multipart/form-data`：`name`、`remark`、`file`

### 7) 修改数据源状态
- `PATCH /api/data/sources/{id}/status`
- 请求体：
```json
{ "status": "ENABLED" }
```

### 8) 删除数据源
- `DELETE /api/data/sources/{id}`

## 规划接口（待实现）
- `POST /api/data/sources/{id}/test-connection`：测试数据库连通性
- `GET /api/data/sources/{id}/preview`：文件/表结构预览
- `POST /api/data/clean/jobs`：启动清洗任务
- `GET /api/data/clean/jobs/{jobId}`：查询清洗任务进度
- `POST /api/data/fusion/jobs`：启动融合任务
- `GET /api/data/fusion/jobs/{jobId}`：查询融合任务进度
