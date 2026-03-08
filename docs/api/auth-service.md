# Auth 服务接口文档

## 服务职责
- 用户注册、登录
- JWT 签发与当前用户身份解析
- 个人信息读取与编辑（昵称、头像、邮箱、手机号、部门）
- 当前账号注销（停用）

## 基础信息
- 服务地址：`http://localhost:8085`
- 网关访问：`http://localhost:8081/api/auth/**`
- 认证方式：`Authorization: Bearer <token>`

## 已实现接口

### 1) 注册
- `POST /api/auth/register`
- 请求体：
```json
{
  "username": "audit_user",
  "password": "123456"
}
```
- 成功响应：
```json
{
  "code": 0,
  "message": "注册成功",
  "data": {
    "id": "2",
    "username": "audit_user",
    "nickname": "audit_user",
    "avatarUrl": "",
    "email": "",
    "phone": "",
    "department": "",
    "role": "AUDITOR"
  }
}
```

### 2) 登录
- `POST /api/auth/login`
- 请求体：
```json
{
  "username": "admin",
  "password": "admin123"
}
```
- 成功响应：
```json
{
  "code": 0,
  "message": "登录成功",
  "data": {
    "token": "<jwt>",
    "user": {
      "id": "1",
      "username": "admin",
      "nickname": "管理员",
      "avatarUrl": "",
      "email": "",
      "phone": "",
      "department": "",
      "role": "ADMIN",
      "lastLoginAt": "2026-03-08 12:59:34"
    }
  }
}
```

### 3) 获取当前用户信息
- `GET /api/auth/me`
- 请求头：`Authorization: Bearer <token>`

### 4) 更新当前用户信息
- `PUT /api/auth/me`
- 请求头：`Authorization: Bearer <token>`
- 请求体示例：
```json
{
  "nickname": "审计员A",
  "avatarUrl": "data:image/png;base64,...",
  "email": "audit.a@example.com",
  "phone": "13800138000",
  "department": "内审部"
}
```

### 5) 注销当前账号（停用）
- `DELETE /api/auth/me`
- 请求头：`Authorization: Bearer <token>`

## 前端头像说明
- 当前实现为“前端本地读取文件 -> 转 Base64 -> 随 `PUT /api/auth/me` 一并保存”。
- 不存在独立的头像上传 URL，因此不会触发 `/upload` 类 404。

## 常见问题
- `DELETE /api/auth/me 404`：通常是网关后面的 `auth-service` 仍在运行旧镜像，请执行：
```bash
docker compose up -d --build auth-service gateway
```
- Docker 模式下 auth-service 需连接容器内 MySQL（`mysql:3306`），不要使用 `localhost:3307`。
