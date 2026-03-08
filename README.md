# 基于多源数据融合的审计整改智能驾驶舱系统

## 1. 目标架构（已按你的修正落地）

请求路径：

1. 用户访问 `https://audit.example.com`
2. `Nginx` 终结 SSL 并按路径分流
   - 静态资源：直接返回 `frontend/dist`
   - `/api/**`：转发到 `Gateway`
3. `Gateway` 统一做鉴权、路由、上下文注入
4. 业务服务处理请求：
   - `data-service`（数据融合）
   - `agent-service`（智能分析/对话）
   - `config-service`（配置中心）
   - `auth-service`（认证服务）
5. 基础设施：`MySQL`、`Redis`

## 2. 项目结构

```text
agent-audit/
├─ docker-compose.yml
├─ nginx/
│  ├─ nginx.conf
│  └─ ssl/
├─ frontend/
│  ├─ dist/                # 打包产物，Nginx 直接托管
│  └─ src/                 # Vue3 + Element Plus + ECharts 源码
└─ services/
   ├─ gateway/
   ├─ data-service/
   ├─ agent-service/
   ├─ config-service/
   └─ auth-service/
```

## 3. 网关路由

- `/api/auth/**` -> `auth-service:8085`
- `/api/data/**` -> `data-service:8082`
- `/api/agent/**` -> `agent-service:8083`
- `/api/config/**` -> `config-service:8084`

## 4. 前端能力覆盖

- 技术栈：`Vue3 + Element Plus + ECharts`
- 图表：饼图（整改率）、折线图（趋势+预测）、热力图（部门薄弱环节）
- 交互：预警阈值设置、AI 问答入口
- 账号：头像菜单、个人信息编辑（昵称/头像/邮箱/手机/部门）、退出登录、账号注销
- API 入口：统一 `/api`（由 Nginx 转发网关）

## 5. 快速启动

### 5.1 本地开发（推荐）

1. 启动后端与基础设施

```bash
docker compose up -d mysql redis auth-service data-service config-service gateway
```

若更新了认证服务接口（如 `/api/auth/me`）后出现 404，请执行：

```bash
docker compose up -d --build auth-service gateway
```

2. 启动前端开发服务器

```bash
cd frontend
npm install
npm run dev
```

3. 浏览器访问

- 前端开发：`http://localhost:5173`
- 网关接口：`http://localhost:8081/api/data/dashboard`

### 5.2 生产打包 + Nginx

```bash
cd frontend
npm install
npm run build
cd ..
docker compose up -d
```

访问：`https://localhost`（需放置证书到 `nginx/ssl`）

## 6. 说明

- 当前认证支持注册、登录、个人信息维护与账号注销（停用）。
- `agent-service` 示例通过网关调用数据接口，保持统一入口治理。
- 下一步可接入真实 JWT 校验、配置中心动态刷新、以及模型服务。

## 7. 接口文档

- 全系统接口说明见 [docs/API.md](docs/API.md)