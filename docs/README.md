# Agent Audit 企业级文档中心

## 文档目标
本目录用于统一沉淀系统架构、部署规范、运维手册、接口治理与发布流程，支持研发、测试、运维、安全和管理角色协同。

## 文档目录

### 架构与设计
1. [项目总览](01-overview.md)
2. [系统架构说明](02-architecture.md)
3. [**代码架构与设计模式**](07-code-architecture.md) ⭐ **2026 新增** - 接口化编程、SOLID 原则、设计模式应用
4. [Data-Service 分层与目录规范](08-data-service-layering.md) ⭐ - Data-Service 职责分层与落位规则

### 部署与运维
4. [部署与环境手册](03-deployment.md)
5. [运维与可观测性手册](04-operations.md)

### 安全与治理
6. [安全与合规基线](05-security-compliance.md)
7. [接口文档总览](API.md)

### 发布与变更
8. [发布与变更记录](06-release-notes.md)

## 快速入口
- 根 README: [../README.md](../README.md)
- 系统架构图: [02-architecture.md](02-architecture.md)
- **代码架构详解**: [07-code-architecture.md](07-code-architecture.md)
- API 细分文档: [api/](api/)

## 读者建议

### 👨‍💻 新成员入项
**阅读路径**：3-5 分钟 快速认知 → 15 分钟 深入理解

1. [项目总览](01-overview.md) - 了解项目在企业中的角色
2. [系统架构说明](02-architecture.md) - 理解微服务架构
3. [代码架构与设计模式](07-code-architecture.md) - 掌握编码规范与接口体系

### 🏗️ 架构设计与代码审查
**重点阅读**：[代码架构与设计模式](07-code-architecture.md)

- 所有微服务的接口清单
- SOLID 原则应用（特别是 DIP）
- 服务间依赖链
- 设计模式（Strategy、Factory、Adapter）
- 测试支持与 Mock 机制

### 🔧 运维与故障排查
**重点阅读**：[部署与环境手册](03-deployment.md) + [运维与可观测性手册](04-operations.md)

- 服务部署流程
- 监控与告警
- 日志查看
- 常见故障解决

### 🔗 联调与 API 开发
**重点阅读**：[接口文档总览](API.md) + [api/](api/) 子目录 + [代码架构与设计模式](07-code-architecture.md) 接口章节

- 各微服务 API 端点
- 请求/响应格式
- 错误码定义
- 接口依赖关系

### 🔐 安全评审
**重点阅读**：[安全与合规基线](05-security-compliance.md) + [代码架构与设计模式](07-code-architecture.md) 接口隔离章节

- 认证与授权
- 数据加密
- 敏感信息处理
- 接口权限管理

## 文档更新日志

### 2026-03-17
- ✨ **新增** [代码架构与设计模式](07-code-architecture.md)
  - 完整的接口清单（Java 10 个接口 + Python 5 个接口）
  - SOLID 原则与 DIP 在微服务中的应用
  - 服务间依赖关系图
  - 三大设计模式（Strategy、Factory、Adapter）详解
  - 单元测试 Mock 示例
  - 编译验证状态与后续优化方向

### 2026-03-23
- ✨ **新增** [Data-Service 分层与目录规范](08-data-service-layering.md)
  - 明确 `orchestration/domain/infrastructure/cache/api` 的目录边界
  - 给出新增代码落位规则与编排层约束
  - 记录“目录归位但 package 不变”的兼容策略

### 2026-03-16
- ✅ 完成所有微服务接口化重构
- ✅ Auth-Service、Config-Service、Data-Service、Gateway 编译通过
- ✅ Agent-Service Python 语法验证通过

## 维护人员
- 架构负责人：待指定
- 文档维护：Team
- 最后更新时间：2026-03-23
