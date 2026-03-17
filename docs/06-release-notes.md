# 发布与变更记录

## v1.4.0 (2026-03-17) ?? **架构升级版**

### ? 主要特性 - 接口化编程重构

#### 设计模式升级
采用 **SOLID 原则**进行全量架构重构，特别是依赖倒置原则（DIP）：

**Java 微服务接口化**（10 个接口）
- auth-service: 2 个接口（IAuthUserService、IJwtService）
- config-service: 1 个接口（IConfigService）
- data-service: 5 个接口（IDashboardService、IDataProcessService 等）
- gateway: 2 个接口（ITokenProvider、IRateLimitProvider）

**Python 微服务接口化**（5 个 ABC 接口）
- agent-service: ILLMProvider、IRAGRetriever、IAgentService、ISessionManager、IDashboardClient

#### 编译验证状态
? **所有服务编译通过**
- auth-service    : mvn clean compile -q → BUILD SUCCESS
- config-service  : mvn clean compile -q → BUILD SUCCESS
- data-service    : mvn clean compile -q → BUILD SUCCESS
- gateway         : mvn clean compile -q → BUILD SUCCESS
- agent-service   : python -m py_compile *.py → OK

#### 架构收益
| 方面 | 收益 |
|-----|------|
| **可维护性** | 接口作为契约，实现修改对调用方无影响 |
| **可测试性** | 支持 Mock 实现，单元测试无依赖外部服务 |
| **可扩展性** | 新增实现无需修改现有代码，符合 OCP |
| **可替换性** | 策略模式实现多实现类灵活切换 |
| **解耦程度** | 服务仅依赖接口，零耦合具体实现 |

### ?? 文档
- ? 新增 [代码架构与设计模式](07-code-architecture.md) 完整指南
- 更新 [README.md](../README.md) 与 [docs/README.md](README.md) 导航链接

### ?? 兼容性
- ? **完全兼容**：所有 API 端点、请求/响应格式无变更
- ? **零改动**：外部调用方无需做任何适配
- ? **渐进式**：接口-实现分离为后续多版本策略奠基

### ? 验收清单
- ? Auth-Service 编译通过
- ? Config-Service 编译通过
- ? Data-Service 编译通过（5 个实现类）
- ? Gateway 编译通过（含 JWT 和限流提供商）
- ? Agent-Service 语法检查通过
- ? 单元测试框架就绪（Mock 支持）
- ? 文档完整（10+ 页架构设计指南）

---

## v1.3.0 (2026-03-14)
### 新增
- data-service 清洗/融合异步执行能力：
  - POST /api/data/clean/tasks/{id}/run-async
  - POST /api/data/fusion/tasks/{id}/run-async
  - GET /api/data/jobs/{jobId}
- 异步作业表与幂等记录表：process_job_record、task_idempotency_record
- Prometheus + Grafana 监控编排

### 优化
- Gateway 增加按用户/IP 限流和 traceId 注入。
- data-service 增加缓存与作业指标埋点。
- agent-service 切换为 Python/FastAPI/LangChain 实现。

### 兼容性说明
- 原同步执行接口保留，不破坏现有调用方。
- 新增异步接口建议在高并发场景优先使用。

## v1.2.0 (2026-03)
### 新增
- Redis 缓存能力覆盖 dashboard/trend/heatmap/fusion-options。
- Agent 会话上下文缓存与限流。

## v1.1.0 (2026-03)
### 新增
- 大屏模块化组件与可定制布局。
- 悬浮助手交互增强。
