# 新成员 10 分钟上手（按角色）

## 1. 使用目标
本指南面向首次接触本项目的成员，按角色给出最短上手路径：
- 开发：本地拉起、接口联调、代码落位
- 测试：登录鉴权、核心业务链路冒烟
- 运维：服务健康、指标与故障定位

## 2. 启动基线（所有角色通用）
1. 启动后端与基础组件：

```bash
docker compose up -d mysql redis auth-service data-service config-service agent-service gateway
```

2. 启动前端（可选）：

```bash
cd frontend
npm install
npm run dev
```

3. 常用访问地址（宿主机端口）：
- 网关：http://localhost:18081
- Data Service：http://localhost:18082
- Agent Service：http://localhost:18083
- Auth Service：http://localhost:18085
- NiFi：http://localhost:18090

## 3. 开发角色（推荐 10 分钟）
1. 阅读架构与代码规范：
- docs/01-overview.md
- docs/02-architecture.md
- docs/07-code-architecture.md

2. 完成最小联调路径：
- 调用 `POST /api/auth/login` 获取 token
- 调用 `GET /api/data/dashboard`
- 调用 `POST /api/agent/chat`

3. 代码落位规则速记：
- Data Service 分层规范：docs/08-data-service-layering.md
- 新增接口优先保持 Controller 轻量，复杂逻辑下沉到 service/domain

## 4. 测试角色（推荐 10 分钟）
1. 核心冒烟用例：
- 登录成功与 token 鉴权
- 清洗任务创建、执行、状态变更
- 融合任务创建、执行、结果落表
- Agent 问答接口返回结果

2. 快速核验点：
- 网关鉴权失败返回 401
- 频控触发返回 429
- 异步任务状态可从 `QUEUED` 演进到 `COMPLETED/FAILED`

3. 参考文档：
- docs/API.md
- docs/api/data-service.md
- docs/04-operations.md

## 5. 运维角色（推荐 10 分钟）
1. 健康检查：
- Gateway：`GET /actuator/health`
- Data Service：`GET /actuator/health`
- Agent Service：`GET /actuator/health`

2. 指标检查：

3. NiFi 上线前巡检：
- 参考 docs/04-operations.md 第 7 节与脚本 `scripts/nifi-go-live-check.ps1`

## 6. 常见误区
1. 使用了容器内端口（808x）直接访问宿主机：应优先使用 1808x。
2. 绕过网关直接联调业务 API：鉴权、限流、trace 头会不一致。
3. 忽略异步幂等：重复提交异步任务时应传 `Idempotency-Key`。

## 7. 建议阅读顺序
1. docs/README.md
2. docs/01-overview.md
3. docs/02-architecture.md
4. docs/API.md
5. docs/04-operations.md

---
最后更新时间：2026-04-15
