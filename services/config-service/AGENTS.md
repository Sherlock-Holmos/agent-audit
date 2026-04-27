# Config Service AGENTS

## 1. 架构定位
- 职责：提供系统运行参数与治理配置读取能力。
- 角色：配置中心微服务，接口统一挂载于 `/api/config/**`。
- 交互模式：当前以读取为主，后续可扩展配置树、版本历史和回滚能力。

## 2. 技术栈
- 语言与运行时：Java 21
- 框架：Spring Boot 3.3.x（Web）
- 存储：当前实现以静态/内存配置为主，后续可接 DB。

## 3. API 与接口层
- `GET /api/config/threshold`
- 规划接口：`GET /api/config/settings/tree`、`GET /api/config/settings/{groupKey}`、`PUT /api/config/settings/{groupKey}`、`POST /api/config/settings/validate`、`GET /api/config/history`、`POST /api/config/history/{id}/rollback`

## 4. 关键配置
- 服务端口：`server.port`（默认 8084）
- 应用名：`spring.application.name=config-service`

## 5. 设计规则
- 配置读取接口应保证幂等和可缓存，避免副作用。
- 配置 key 命名必须语义化、分组化，便于前后端对齐。
- 新增配置项需提供默认值与兼容策略，避免客户端空值崩溃。
- 若扩展写接口，必须增加变更审计（操作者、时间、变更前后值）。
- 敏感配置（密钥、凭据）不得明文返回给前端。
- 错误响应需区分参数错误与配置缺失，便于联调定位。
