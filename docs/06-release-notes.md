# 发布与变更记录

## v1.5.0 (2026-03-24)
### 新增
- Data Service 新增 NiFi 控制平面接口：
  - GET /api/data/control-plane/nifi/status
  - POST /api/data/control-plane/nifi/flows/run
  - GET /api/data/control-plane/nifi/flows
  - GET /api/data/control-plane/nifi/templates
  - POST /api/data/control-plane/nifi/templates
- 新增分层统计接口：
  - GET /api/data/control-plane/layers/stats
- 新增 NiFi 运行与模板相关数据表：
  - nifi_flow_run_record
  - nifi_flow_template_record
- 新增目标分层数据表：
  - bronze_ingest_record
  - silver_standard_record
  - gold_fusion_wide_record

### 变更
- 清洗任务执行成功后，自动回写 Bronze 和 Silver。
- 融合任务执行成功后，自动回写 Gold。
- NiFi 触发支持模板参数校验：
  - 支持按 flowType 自动读取启用模板。
  - 支持 parameterSchema.requiredKeys 必填校验。
  - 触发结果返回 templateVersion。
- 前端规则页面新增：
  - NiFi 模板管理（新增/编辑/启用状态查看）
  - NiFi 模板触发测试弹窗
  - Bronze/Silver/Gold 分层统计看板

### 验证结果
- 后端编译通过：mvn -DskipTests compile
- 前端构建通过：npm run build
- 接口冒烟通过：
  - 模板查询返回 200，存在模板记录
  - 分层统计返回 200，统计结果非零
  - 实测统计样例：tasks=3, bronzeRows=16, silverRows=16, goldRows=7

### 兼容性说明
- 既有清洗/融合接口保持不变，新增能力通过控制平面接口扩展。
- 未配置 NiFi 模板时，仍可通过请求体直接传 processGroupId 触发流程。

---

## v1.4.0 (2026-03-17)
### 主要内容
- 完成微服务接口化重构，落地面向接口编程与依赖倒置。
- Java 服务新增和归整接口层，提升可测试性与可替换性。
- Python Agent 服务补齐抽象接口与实现解耦。

### 验证
- auth-service、config-service、data-service、gateway 编译通过。
- agent-service 语法验证通过。

---

## v1.3.0 (2026-03-14)
### 新增
- data-service 新增清洗与融合异步执行接口：
  - POST /api/data/clean/tasks/{id}/run-async
  - POST /api/data/fusion/tasks/{id}/run-async
  - GET /api/data/jobs/{jobId}
- 新增异步任务落库表：process_job_record、task_idempotency_record
- 新增基础监控

### 优化
- Gateway 增加用户/IP 维度限流与 traceId 透传。
- data-service 增加缓存与任务指标埋点。
- agent-service 切换为 Python/FastAPI/LangChain 实现。

---

## v1.2.0 (2026-03)
### 新增
- Redis 缓存 dashboard、trend、heatmap、fusion-options。
- Agent 会话管理增强与缓存优化。

---

## v1.1.0 (2026-03)
### 新增
- 前后端模块化整理与基础联调能力建设。
- 登录鉴权与基础数据流转能力增强。
