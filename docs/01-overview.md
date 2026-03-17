# 项目总览

## 1. 项目定位
Agent Audit 是面向审计整改场景的多源数据融合与智能分析平台，目标是将数据接入、清洗、融合、可视化大屏与智能问答打通为统一闭环。

## 2. 核心价值
- 统一入口：通过网关集中治理认证、路由、上下文。
- 数据闭环：支持数据源接入、清洗任务、融合任务、质量分析。
- 智能辅助：基于 LangChain 结合仪表板数据与知识检索给出问答建议。
- 可运维：具备 Redis 缓存、限流、监控与日志基础能力。

## 3. 业务域拆分
- 认证域（auth-service）：账号注册登录、个人信息维护、账号注销。
- 数据域（data-service）：数据源管理、清洗/融合任务、驾驶舱统计。
- 智能域（agent-service）：Python 智能问答、多轮上下文会话管理、RAG 扩展入口。
- 配置域（config-service）：阈值与系统配置。
- 网关域（gateway）：统一鉴权、限流、路由分发。

## 4. 关键特性
- 网关 JWT 鉴权与用户上下文注入。
- 网关按用户/IP 频控（分钟级窗口）。
- data-service 仪表板接口 Redis 缓存与失效控制。
- 清洗/融合支持同步执行与异步执行（带幂等键）。
- agent-service 支持 Redis 会话历史缓存、接口限流与 LangChain 链式调用。
- agent-service 预留向量数据库接入能力，可扩展 Chroma / pgvector。
- Prometheus 指标暴露与 Grafana 可视化基础。

## 5. 技术栈
- 前端：Vue 3、Element Plus、ECharts、Vite。
- 后端：Spring Boot 3、Spring Cloud Gateway、JdbcTemplate、FastAPI、LangChain。
- 数据与缓存：MySQL 8、Redis 7。
- 运维与监控：Docker Compose、Prometheus、Grafana。

## 6. 非功能目标（建议基线）
- 可用性：核心链路目标可用性 >= 99.9%。
- 性能：核心读接口 P95 < 300ms（缓存命中场景）。
- 安全：所有业务请求必须经 Gateway 鉴权。
- 可追踪：关键链路应具备统一 traceId 与结构化日志。
