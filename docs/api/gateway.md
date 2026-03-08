# Gateway 服务接口与网关规则

## 服务职责
- 统一 API 入口（`/api/**`）
- JWT 鉴权、用户上下文注入
- 路由分发到各微服务

## 基础信息
- 本地地址：`http://localhost:8081`
- 当前已配置路由：
  - `/api/auth/**` -> auth-service
  - `/api/data/**` -> data-service
  - `/api/agent/**` -> agent-service
  - `/api/config/**` -> config-service

## 鉴权与白名单
- 白名单（免鉴权）：
  - `POST /api/auth/login`
  - `POST /api/auth/register`
  - `GET /actuator/health`
- 其他接口需携带：`Authorization: Bearer <token>`
- 认证失败：HTTP `401`，并附加响应头 `X-Auth-Error`

## 网关注入头（已实现）
- `X-User-Name`
- `X-User-Role`
- `X-User-Dept`

## 规划接口/能力（待实现）
- `GET /api/gateway/health/routes`：路由可用性检测
- `GET /api/gateway/metrics/auth`：鉴权统计（成功率、拒绝次数）
- `POST /api/gateway/cache/invalidate`：网关配置缓存刷新
- 限流策略：按用户、部门、IP 维度限流
- 灰度路由：按 header 或用户组分流
