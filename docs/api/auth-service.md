# Auth 服务接口文档

## 1. 服务职责
- 用户注册与登录
- JWT 令牌签发
- 当前用户信息维护
- 当前账号注销（停用）

## 2. 基础信息
- 服务地址：`http://localhost:8085`
- 网关访问：`http://localhost:8081/api/auth/**`

## 3. 已实现接口
1. `POST /api/auth/register`
2. `POST /api/auth/login`
3. `GET /api/auth/me`
4. `PUT /api/auth/me`
5. `DELETE /api/auth/me`

## 4. 字段说明
- 登录返回字段：`token`、`user`。
- `user` 包含：`id`、`username`、`nickname`、`avatarUrl`、`email`、`phone`、`department`、`role`、`lastLoginAt`。

## 5. 开发约定
- 头像通过 `avatarUrl`（可为 Base64）字段写入，不依赖独立上传接口。
- 联调建议始终通过网关地址访问，避免绕过鉴权策略。

## 6. 常见问题
- `DELETE /api/auth/me` 或 `PUT /api/auth/me` 返回 404：
  - 优先检查 auth-service 镜像版本与网关路由。
  - 可执行：
```bash
docker compose up -d --build auth-service gateway
```
