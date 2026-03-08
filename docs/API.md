# 审计整改智能驾驶舱 - API 总览

## 1. 访问约定
- 网关统一入口：`http://localhost:8081`
- 业务接口统一前缀：`/api/**`
- 认证方式：`Authorization: Bearer <token>`

## 2. 服务级接口文档
- 网关（路由、鉴权、上下文）：[api/gateway.md](api/gateway.md)
- 认证服务（登录、注册、个人信息、注销）：[api/auth-service.md](api/auth-service.md)
- 数据服务（驾驶舱、数据源、清洗融合规划）：[api/data-service.md](api/data-service.md)
- 智能体服务（问答、报告规划）：[api/agent-service.md](api/agent-service.md)
- 配置服务（阈值、分级设置规划）：[api/config-service.md](api/config-service.md)

## 3. 使用说明
- 每个服务文档均包含“已实现接口”和“规划接口（待实现）”。
- 前端联调优先通过网关地址进行，避免绕过网关鉴权。

## 4. 认证服务联调注意事项
- 前端报 `DELETE /api/auth/me 404` 或 `PUT /api/auth/me 404` 时，优先确认 `auth-service` 是否已升级到新版本。
- Docker 推荐更新命令：
```bash
docker compose up -d --build auth-service gateway
```
- 开发环境头像为 Base64 存储，不需要单独文件上传接口。
