# Gateway Service AGENTS

## 1. 架构定位
- 职责：统一 API 入口、JWT 鉴权、限流、用户上下文注入、Trace 透传。
- 角色：所有业务服务前置入口，路由到 auth/data/agent/config。
- 核心价值：把安全与流量治理前移，简化下游服务负担。

## 2. 技术栈
- 语言与运行时：Java 21
- 框架：Spring Boot 3.3.x + Spring Cloud Gateway 2023.x
- 缓存与限流：Reactive Redis
- 认证：JJWT
- 可观测：Actuator + Micrometer

## 3. API 与接口层
- `/api/auth/**` -> auth-service
- `/api/data/**` -> data-service
- `/api/agent/**` -> agent-service
- `/api/config/**` -> config-service
- 白名单仅允许登录、注册、健康检查和通用指标端点。
- 非白名单请求必须携带 `Authorization: Bearer <token>`。
- 鉴权失败返回 401，并携带可诊断头（如 `X-Auth-Error`）。
- 限流维度：用户与 IP 双维度；超限返回 429，并附带 `Retry-After`。
- 上下文头约定：`X-User-Name`、`X-User-Role`、`X-User-Dept`、`X-Trace-Id`。

## 4. 关键配置
- 端口：`server.port`（默认 8081）
- 路由目标：`spring.cloud.gateway.routes[].uri`
- 限流：`GATEWAY_RATE_LIMIT_PER_MINUTE`、`GATEWAY_IP_RATE_LIMIT_PER_MINUTE`
- JWT：`auth.jwt.secret`

## 5. 设计规则
- 网关 filter 只做协议、安全、治理，不承载业务编排。
- 请求头注入字段应保持向后兼容，避免下游解析异常。
- 限流与鉴权策略变更需先灰度，避免全量误拦截。
- 路由变更必须同步更新网关文档与联调清单。
- 健康与指标端点需保持可用，便于运维观测与告警。
