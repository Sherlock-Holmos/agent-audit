# Gateway 服务接口与治理规则

## 1. 服务职责
- 统一 API 入口（`/api/**`）
- JWT 鉴权与用户上下文注入
- 用户/IP 双维度限流
- Trace 头透传（`X-Trace-Id`）

## 2. 基础信息
- 地址：`http://localhost:8081`
- 路由：
  - `/api/auth/**` -> auth-service
  - `/api/data/**` -> data-service
  - `/api/agent/**` -> agent-service
  - `/api/config/**` -> config-service

## 3. 鉴权与白名单
- 白名单：
  - `POST /api/auth/login`
  - `POST /api/auth/register`
  - `GET /actuator/health`
  - `GET /actuator/info`
  - `GET /actuator/prometheus`
- 非白名单请求需携带：`Authorization: Bearer <token>`
- 鉴权失败：`401`，并返回 `X-Auth-Error`

## 4. 限流策略
- 限流维度：用户、IP
- 窗口：60 秒
- 默认阈值：
  - 用户：`120 req/min`
  - IP：`240 req/min`
- 超限返回：`429`，并附加 `Retry-After: 60`

## 5. 上下文注入头
- `X-User-Name`
- `X-User-Role`
- `X-User-Dept`
- `X-Trace-Id`

## 6. 运维端点
- `GET /actuator/health`
- `GET /actuator/info`
- `GET /actuator/metrics`
- `GET /actuator/prometheus`
