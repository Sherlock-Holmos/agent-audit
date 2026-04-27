# Auth Service AGENTS

## 1. 架构定位
- 职责：用户注册/登录、JWT 签发、当前用户信息维护、账号停用。
- 角色：统一身份入口，提供 `/api/auth/**`；网关负责外层鉴权和透传。
- 数据边界：`auth_user` 为账号主数据，字段含 `username/role/status/unit/department` 等。

## 2. 技术栈
- 语言与运行时：Java 21
- 框架：Spring Boot 3.3.x（Web + JDBC）
- 安全与认证：Spring Security Crypto、JJWT
- 数据库：MySQL

## 3. API 与接口层
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `PUT /api/auth/me`
- `DELETE /api/auth/me`
- 登录返回：`token`、`user`
- `user` 推荐字段：`id/username/nickname/avatarUrl/email/phone/unit/department/role/lastLoginAt`

## 4. 关键配置
- 服务端口：`server.port`（默认 8085）
- 数据源：`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`
- JWT：`auth.jwt.secret`、`auth.jwt.expiration-minutes`
- SQL 初始化：`spring.sql.init.mode`、`continue-on-error`

## 5. 设计规则
- 认证逻辑集中在应用服务与领域服务，不在 Controller 写业务分支。
- 密码仅存储哈希值，禁止任何形式回传明文密码。
- JWT claim 字段保持稳定，至少包含 username 与 role，避免网关解析回归。
- `unit/department` 作为跨服务协同字段，新增逻辑不得破坏兼容。
- 数据变更接口要有参数校验和错误语义（400/401/404）区分。
- 涉及事务写入的方法应使用事务边界，避免部分成功。
- 统一 UTF-8 编码，避免中文字段在 MySQL 中乱码。
