# 对话式 Agent 操作方案（V1）

## 1. 背景与目标
在现有“审计整改智能驾驶舱”基础上，新增对话式操作入口，让用户通过自然语言完成以下高频动作：
- 打开目标页面（减少菜单层级查找成本）
- 查询“本账号可见”数据摘要（权限内信息可解释）
- 发起督办或进入整改填写入口（提升闭环效率）

V1 目标：
- 实现可用、可控、可审计的对话操作能力
- 不改变既有业务权限边界
- 避免高风险“静默写入”，优先“页面确认 + 人工提交”

## 2. 适用角色
- 审计管理员（AUDIT_ADMIN）
- 审计人员（AUDITOR）
- 被审计单位管理员（ORG_ADMIN）
- 被审计单位经办人（ORG_OPERATOR）

## 3. 功能范围（V1）
### 3.1 对话打开页面
支持语义：打开/进入/跳转 + 业务关键词
- 驾驶舱、总览、看板 -> /dashboard
- 重点问题、督办台账 -> /audit-admin/focus-issues
- 任务协同 -> /org-admin/tasks/collaboration
- 整改汇报、整改报告 -> /org-admin/report/submit
- 任务认领 -> /org-operator/tasks/claim
- 执行反馈、整改填写 -> /org-operator/execution-center
- 消息中心、通知 -> /messages
- AI 分析 -> /ai

### 3.2 本账号可见信息查询
支持语义：本账号、我的权限、我能看什么、可查询
返回内容：
- 账号与角色
- 当前可见问题数/任务数/报告数/未读通知数
- 推荐首页入口

### 3.3 督办与整改入口联动
- 督办：
  - AUDIT_ADMIN：打开重点问题台账并自动弹出督办窗口（首条可督办问题）
  - ORG_ADMIN：打开任务协同页并自动弹出任务督办窗口（首条可督办任务）
- 整改汇报：
  - ORG_ADMIN：打开整改总报告页，自动预填标题模板
- 整改填写：
  - ORG_OPERATOR：打开执行反馈中心并自动弹出首条任务填写窗口

## 4. 交互设计原则
- 优先本地意图执行：命中明确意图时，不走大模型，确保稳定与可控
- 未命中再走 AI 问答：继续保留原有 chat 能力
- 拒绝越权：无权限时明确提示，不进行隐式跳转
- 人工确认提交：所有写操作仍在业务页面由用户确认完成

## 5. 技术方案
### 5.1 前端架构
- 入口组件：FloatingAssistant
- 核心能力：
  - 意图识别（关键词 + 动词规则）
  - 路由权限校验（按路由 meta.roles）
  - 本账号数据摘要（拉取 rectification snapshot 后按角色裁剪）
  - 全局动作事件分发（assistant-action）

### 5.2 页面联动机制
整改页面监听全局事件 assistant-action，并执行最小动作：
- open-issue-supervision
- open-task-supervision
- open-org-report-fill
- open-operator-execution-fill

### 5.3 安全与边界
- 不在对话层直接调用高风险写接口
- 保留页面原有校验、提交流程、审计记录
- 统一依赖现有网关鉴权与角色控制

## 6. 验收标准（V1）
- 输入“打开重点问题督办台账”可跳转到对应页面
- 输入“本账号可以查询什么”可返回角色内统计摘要
- 输入“发起督办”可在支持角色页面自动拉起督办弹窗
- 输入“我要整改汇报/整改填写”可打开对应页面并拉起填写入口
- 非授权角色执行受限动作时，返回明确权限提示

## 7. 已落地实现（当前代码）
- FloatingAssistant 增加本地意图执行层
- AuditAdminFocusIssuesView 增加助手督办动作监听
- OrgAdminTaskCollaborationView 增加助手督办动作监听
- OrgAdminReportSubmitView 增加助手汇报动作监听
- OrgOperatorExecutionCenterView 增加助手整改填写动作监听

## 8. 后续迭代建议（V2）
- 引入结构化意图协议（intent + slots）替代纯关键词规则
- 增加“候选任务选择”而非默认首条
- 增加操作回执与审计事件上报（谁在何时由 Agent 触发了什么页面动作）
- 接入 function calling，实现“建议 -> 确认 -> 执行 -> 回执”闭环
