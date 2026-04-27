# Agent Service V2 - LangChain ReAct Agent 架构

基于 LangChain Agent 框架重新实现的 Agent 服务，具备显式推理步骤分解、动态工具选择和推理结果验证机制。

## 核心特性

### 1. 显式推理步骤分解 (ReAct Pattern)

```
Thought → Action (Tool Selection) → Observation → Reflection → Answer
```

- 每个推理步骤都被记录和追踪
- 支持流式输出推理过程
- 可追溯的推理轨迹 `ReasoningTrace`

### 2. 动态工具选择与调用

**工具注册中心** (`ToolRegistry`)
- 单例模式管理所有工具
- 支持懒加载（工厂模式）
- 按分类检索工具

**工具基类** (`BaseTool`)
```python
class MyTool(BaseTool):
    @property
    def metadata(self) -> ToolMetadata:
        return ToolMetadata(
            name="my_tool",
            description="工具描述",
            category=ToolCategory.DATA_QUERY,
            parameters={...},
            required_params=[...],
        )

    async def _execute(self, **kwargs) -> ToolResult:
        # 实现逻辑
        return ToolResult.ok(data)
```

**内置工具**
- `fetch_dashboard` - 获取数据看板
- `query_completion_rate` - 查询完成率
- `query_overdue_tasks` - 查询逾期任务
- `query_issues` - 查询问题列表
- `analyze_trend` - 分析整改趋势
- `calculate_risk_score` - 计算风险评分
- `summarize_issues` - 问题汇总
- `compare_performance` - 绩效对比

### 3. 推理结果验证机制

**多维度验证**
- `completeness` - 完整性检查
- `factual_accuracy` - 事实准确性
- `tool_usage_validity` - 工具使用有效性
- `consistency` - 内部一致性
- `relevance` - 相关性

**LLM 辅助验证**
- 自动评估回答质量
- 提供改进建议

**修复机制**
- 验证失败时自动尝试重新生成
- 可配置的最大修复轮次

### 4. 高可扩展性

**插件化工具系统**
```python
from app.tools import BaseTool, ToolMetadata, register_tool

@register_tool
class MyCustomTool(BaseTool):
    ...
```

**自定义验证规则**
```python
from app.core import ResultVerifier, VerificationRule

verifier = ResultVerifier()
verifier.add_rule(VerificationRule(
    name="custom_check",
    description="自定义验证",
    check_fn=my_check_function,
    weight=1.0,
    critical=True,
))
```

**推理步骤钩子**
```python
engine = ReasoningEngine()
engine.add_step_hook(async_handler)
```

## 项目结构

```
app/
├── agents/                 # Agent 实现
│   ├── base.py            # Agent 基类
│   ├── react_agent.py     # ReAct Agent
│   └── __init__.py
├── core/                   # 核心组件
│   ├── reasoning_engine.py    # 推理引擎
│   ├── result_verifier.py     # 结果验证
│   └── __init__.py
├── tools/                  # 工具系统
│   ├── base.py            # 工具基类
│   ├── registry.py        # 工具注册中心
│   ├── dashboard_tools.py # 看板工具
│   ├── analysis_tools.py  # 分析工具
│   └── __init__.py
├── services/
│   ├── agent_service.py   # Agent 服务
│   ├── session.py         # 会话管理
│   └── dashboard.py       # 看板客户端
└── routers/
    └── chat.py            # API 路由
```

## API 接口

### 非流式对话
```
POST /api/agent/chat
Content-Type: application/json
X-User-Name: username

{
    "question": "我的整改完成率是多少？",
    "llmConfig": {
        "provider": "openai",
        "model": "gpt-4o-mini"
    }
}
```

### 流式对话
```
POST /api/agent/chat/stream
Content-Type: application/json
X-User-Name: username

{
    "question": "分析我的整改趋势"
}
```

响应格式 (SSE):
```
data: {"type": "start", ...}
data: {"type": "step", "step_type": "thought", ...}
data: {"type": "step", "step_type": "tool_exec", "tool_name": "...", ...}
data: {"type": "final", "answer": "...", "confidence": 0.92}
data: [DONE]
```

## 配置

环境变量 (`.env`):
```env
# LLM 提供商: mock | openai | azure
LLM_PROVIDER=mock

# OpenAI
OPENAI_API_KEY=your-key
OPENAI_MODEL=gpt-4o-mini

# Azure OpenAI
AZURE_OPENAI_API_KEY=your-key
AZURE_OPENAI_ENDPOINT=https://...
AZURE_OPENAI_DEPLOYMENT=gpt-4o
```

## 扩展开发

### 添加新工具

```python
# app/tools/my_tools.py
from app.tools import BaseTool, ToolMetadata, ToolCategory, ToolResult, register_tool

@register_tool
class MyTool(BaseTool):
    @property
    def metadata(self) -> ToolMetadata:
        return ToolMetadata(
            name="my_tool",
            description="我的自定义工具",
            category=ToolCategory.ANALYSIS,
            parameters={
                "param1": {"type": "string", "description": "参数1"}
            },
            required_params=["param1"],
        )

    async def _execute(self, **kwargs) -> ToolResult:
        param1 = kwargs.get("param1")
        # 业务逻辑
        return ToolResult.ok({"result": "success"})
```

### 添加验证规则

```python
# 在初始化时添加
from app.core import ResultVerifier, VerificationRule

verifier = ResultVerifier()
verifier.add_rule(VerificationRule(
    name="business_rule",
    description="业务规则检查",
    check_fn=lambda answer, trace: (True, None),
    weight=1.5,
    critical=True,
))
```

## 调试与监控

### 获取推理轨迹
```python
trace = await agent_service.get_reasoning_trace(
    question="...",
    history=[...],
    dashboard={...}
)
print(trace.to_dict())  # 完整推理步骤
```

### 获取验证结果
```python
result = await agent_service.get_verification_result(
    answer="...",
    question="...",
    history=[...],
    dashboard={...}
)
print(result.to_dict())  # 验证详情
```

## 版本信息

- **Version**: 2.0.0
- **Framework**: LangChain Agent
- **Pattern**: ReAct (Reasoning + Acting)
