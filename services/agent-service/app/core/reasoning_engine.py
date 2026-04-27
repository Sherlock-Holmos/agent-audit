"""推理引擎 - 显式推理步骤分解"""

import json
import logging
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any, Optional, Callable, Coroutine
from collections.abc import AsyncIterator

from langchain_core.messages import HumanMessage, AIMessage, SystemMessage, ToolMessage
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_openai import ChatOpenAI, AzureChatOpenAI

from app.config import settings
from app.tools.base import ToolResult
from app.tools.registry import registry

logger = logging.getLogger(__name__)


class ReasoningStepType(Enum):
    """推理步骤类型"""
    THOUGHT = "thought"           # 思考
    TOOL_SELECTION = "tool_select"  # 工具选择
    TOOL_EXECUTION = "tool_exec"     # 工具执行
    OBSERVATION = "observation"      # 观察
    REFLECTION = "reflection"        # 反思
    ANSWER = "answer"                # 最终答案


@dataclass
class ReasoningStep:
    """推理步骤"""
    step_type: ReasoningStepType
    content: str
    metadata: dict = field(default_factory=dict)
    timestamp: datetime = field(default_factory=datetime.now)
    tool_name: Optional[str] = None
    tool_input: Optional[dict] = None
    tool_output: Optional[ToolResult] = None
    is_valid: bool = True
    validation_error: Optional[str] = None

    def to_dict(self) -> dict:
        return {
            "type": self.step_type.value,
            "content": self.content,
            "timestamp": self.timestamp.isoformat(),
            "tool_name": self.tool_name,
            "tool_input": self.tool_input,
            "tool_output": self.tool_output.to_dict() if self.tool_output else None,
            "metadata": self.metadata,
            "is_valid": self.is_valid,
            "validation_error": self.validation_error,
        }


@dataclass
class ReasoningTrace:
    """完整推理轨迹"""
    query: str
    steps: list[ReasoningStep] = field(default_factory=list)
    final_answer: Optional[str] = None
    metadata: dict = field(default_factory=dict)
    created_at: datetime = field(default_factory=datetime.now)
    completed_at: Optional[datetime] = None

    def add_step(self, step: ReasoningStep) -> "ReasoningTrace":
        self.steps.append(step)
        return self

    def complete(self, answer: str) -> "ReasoningTrace":
        self.final_answer = answer
        self.completed_at = datetime.now()
        return self

    def to_dict(self) -> dict:
        return {
            "query": self.query,
            "steps": [s.to_dict() for s in self.steps],
            "final_answer": self.final_answer,
            "metadata": self.metadata,
            "created_at": self.created_at.isoformat(),
            "completed_at": self.completed_at.isoformat() if self.completed_at else None,
            "step_count": len(self.steps),
        }

    def get_step_by_type(self, step_type: ReasoningStepType) -> list[ReasoningStep]:
        """按类型获取步骤"""
        return [s for s in self.steps if s.step_type == step_type]


class ReasoningEngine:
    """推理引擎 - 显式步骤分解"""

    def __init__(self, llm=None):
        self.llm = llm or self._build_default_llm()
        self._step_hooks: list[Callable[[ReasoningStep], Coroutine]] = []
        self._max_iterations = 10

    def _build_default_llm(self):
        """构建默认 LLM"""
        provider = settings.llm_provider.lower()
        if provider == "azure":
            return AzureChatOpenAI(
                azure_deployment=settings.azure_openai_deployment,
                azure_endpoint=settings.azure_openai_endpoint,
                api_key=settings.azure_openai_api_key,
                api_version=settings.azure_openai_api_version,
                temperature=0.2,
            )
        elif provider == "openai":
            return ChatOpenAI(
                model=settings.openai_model,
                api_key=settings.openai_api_key,
                temperature=0.2,
            )
        else:
            return None

    def add_step_hook(self, hook: Callable[[ReasoningStep], Coroutine]) -> "ReasoningEngine":
        """添加步骤钩子"""
        self._step_hooks.append(hook)
        return self

    async def _notify_step(self, step: ReasoningStep) -> None:
        """通知所有步骤钩子"""
        for hook in self._step_hooks:
            try:
                await hook(step)
            except Exception as e:
                logger.warning(f"Step hook failed: {e}")

    def _build_system_prompt(self) -> str:
        """构建系统提示词"""
        tools_desc = self._build_tools_description()
        return f"""你是一个企业审计整改智能助手，使用ReAct模式进行推理。

可用工具：
{tools_desc}

推理规则：
1. 首先分析问题，明确需要什么信息
2. 选择合适的工具获取信息
3. 观察工具返回结果
4. 如有必要，进行反思并继续下一步
5. 最后给出结论性回答

输出格式要求：
- Thought: [你的思考过程]
- Action: 工具名称
- Action Input: {{"参数名": "参数值"}}
- 或直接输出最终答案

重要：
- 每个推理步骤必须明确
- 如果工具执行失败，说明原因并尝试替代方案
- 如果信息不足，明确指出需要什么额外信息"""

    def _build_tools_description(self) -> str:
        """构建工具描述"""
        tools = registry.list_all()
        descriptions = []
        for tool in tools:
            params = ", ".join(tool["parameters"].keys())
            descriptions.append(f"- {tool['name']}: {tool['description']} (参数: {params})")
        return "\n".join(descriptions) if descriptions else "无可用工具"

    async def reason(
        self,
        query: str,
        context: dict = None,
        history: list[dict] = None,
        stream: bool = False,
    ) -> ReasoningTrace | AsyncIterator[ReasoningStep]:
        """执行推理 - 显式步骤分解"""
        trace = ReasoningTrace(query=query, metadata={"context": context or {}})

        if stream:
            return self._reason_stream(trace, query, context, history)
        else:
            return await self._reason_sync(trace, query, context, history)

    async def _reason_sync(
        self,
        trace: ReasoningTrace,
        query: str,
        context: dict,
        history: list[dict],
    ) -> ReasoningTrace:
        """同步推理"""
        messages = self._build_messages(query, context, history)
        iterations = 0

        while iterations < self._max_iterations:
            iterations += 1

            # 1. 思考步骤
            thought_step = await self._generate_thought(messages, trace)
            trace.add_step(thought_step)
            await self._notify_step(thought_step)

            if thought_step.metadata.get("is_final_answer"):
                trace.complete(thought_step.content)
                return trace

            # 2. 工具选择和执行
            if thought_step.metadata.get("tool_name"):
                tool_step = await self._execute_tool(
                    thought_step.metadata["tool_name"],
                    thought_step.metadata.get("tool_input", {}),
                )
                trace.add_step(tool_step)
                await self._notify_step(tool_step)

                # 更新消息用于下一轮
                messages.append(AIMessage(content=f"工具 {tool_step.tool_name} 执行结果: {json.dumps(tool_step.tool_output.to_dict() if tool_step.tool_output else {}, ensure_ascii=False)}"))

                # 3. 观察/反思步骤
                obs_step = await self._generate_observation(tool_step, trace)
                trace.add_step(obs_step)
                await self._notify_step(obs_step)

                if obs_step.metadata.get("is_final_answer"):
                    trace.complete(obs_step.content)
                    return trace
            else:
                # 无工具调用，直接给出答案
                trace.complete(thought_step.content)
                return trace

        # 达到最大迭代次数
        trace.complete("[达到最大推理轮次，请简化问题重试]")
        return trace

    async def _reason_stream(
        self,
        trace: ReasoningTrace,
        query: str,
        context: dict,
        history: list[dict],
    ) -> AsyncIterator[ReasoningStep]:
        """流式推理"""
        messages = self._build_messages(query, context, history)
        iterations = 0

        while iterations < self._max_iterations:
            iterations += 1

            # 思考
            thought_step = await self._generate_thought(messages, trace)
            trace.add_step(thought_step)
            yield thought_step

            if thought_step.metadata.get("is_final_answer"):
                trace.complete(thought_step.content)
                yield ReasoningStep(
                    step_type=ReasoningStepType.ANSWER,
                    content=thought_step.content,
                    metadata={"trace": trace.to_dict()},
                )
                return

            # 工具执行
            if thought_step.metadata.get("tool_name"):
                tool_step = await self._execute_tool(
                    thought_step.metadata["tool_name"],
                    thought_step.metadata.get("tool_input", {}),
                )
                trace.add_step(tool_step)
                yield tool_step

                # 观察
                obs_step = await self._generate_observation(tool_step, trace)
                trace.add_step(obs_step)
                yield obs_step

                if obs_step.metadata.get("is_final_answer"):
                    trace.complete(obs_step.content)
                    yield ReasoningStep(
                        step_type=ReasoningStepType.ANSWER,
                        content=obs_step.content,
                        metadata={"trace": trace.to_dict()},
                    )
                    return

                messages.append(AIMessage(content=f"观察: {obs_step.content}"))
            else:
                trace.complete(thought_step.content)
                yield ReasoningStep(
                    step_type=ReasoningStepType.ANSWER,
                    content=thought_step.content,
                    metadata={"trace": trace.to_dict()},
                )
                return

        trace.complete("[达到最大推理轮次]")
        yield ReasoningStep(
            step_type=ReasoningStepType.ANSWER,
            content="[达到最大推理轮次，请简化问题重试]",
            metadata={"trace": trace.to_dict()},
        )

    def _build_messages(
        self,
        query: str,
        context: dict,
        history: list[dict],
    ) -> list:
        """构建消息列表"""
        messages = [SystemMessage(content=self._build_system_prompt())]

        # 添加上下文信息
        if context:
            ctx_parts = []
            if "username" in context:
                ctx_parts.append(f"当前用户: {context['username']}")
            if "dashboard" in context:
                dash = context["dashboard"]
                ctx_parts.append(f"完成率: {dash.get('completedRate', 'N/A')}%, 逾期任务: {dash.get('overdueCount', 'N/A')}")
            if ctx_parts:
                messages.append(SystemMessage(content="【上下文】\n" + "\n".join(ctx_parts)))

        # 添加历史对话
        if history:
            for turn in history[-5:]:  # 最近5轮
                messages.append(HumanMessage(content=turn.get("q", "")))
                messages.append(AIMessage(content=turn.get("a", "")))

        messages.append(HumanMessage(content=query))
        return messages

    async def _generate_thought(
        self,
        messages: list,
        trace: ReasoningTrace,
    ) -> ReasoningStep:
        """生成思考步骤"""
        if self.llm is None:
            return ReasoningStep(
                step_type=ReasoningStepType.THOUGHT,
                content="【Mock模式】未配置LLM，直接返回答案。",
                metadata={"is_final_answer": True, "mock": True},
            )

        try:
            response = await self.llm.ainvoke(messages)
            content = response.content

            # 解析是否包含工具调用
            tool_name = None
            tool_input = {}
            is_final = True

            # 简单的 Action 解析
            if "Action:" in content:
                lines = content.split("\n")
                for i, line in enumerate(lines):
                    if line.startswith("Action:"):
                        tool_name = line.replace("Action:", "").strip()
                        is_final = False
                        # 查找 Action Input
                        for j in range(i+1, len(lines)):
                            if lines[j].startswith("Action Input:"):
                                try:
                                    input_str = lines[j].replace("Action Input:", "").strip()
                                    tool_input = json.loads(input_str)
                                except:
                                    tool_input = {"raw": input_str}
                                break
                        break

            return ReasoningStep(
                step_type=ReasoningStepType.THOUGHT,
                content=content,
                metadata={
                    "is_final_answer": is_final,
                    "tool_name": tool_name,
                    "tool_input": tool_input,
                },
            )
        except Exception as e:
            logger.error(f"Thought generation failed: {e}")
            return ReasoningStep(
                step_type=ReasoningStepType.THOUGHT,
                content=f"推理生成失败: {e}",
                is_valid=False,
                validation_error=str(e),
            )

    async def _execute_tool(self, tool_name: str, tool_input: dict) -> ReasoningStep:
        """执行工具"""
        tool = registry.get(tool_name)

        if tool is None:
            return ReasoningStep(
                step_type=ReasoningStepType.TOOL_EXECUTION,
                content=f"工具 '{tool_name}' 不存在",
                tool_name=tool_name,
                tool_input=tool_input,
                is_valid=False,
                validation_error=f"Tool not found: {tool_name}",
            )

        # 参数验证
        valid, error = tool.validate_params(tool_input)
        if not valid:
            return ReasoningStep(
                step_type=ReasoningStepType.TOOL_EXECUTION,
                content=f"参数验证失败: {error}",
                tool_name=tool_name,
                tool_input=tool_input,
                is_valid=False,
                validation_error=error,
            )

        # 执行
        result = await tool.execute(**tool_input)

        return ReasoningStep(
            step_type=ReasoningStepType.TOOL_EXECUTION,
            content=f"工具执行{'成功' if result.success else '失败'}",
            tool_name=tool_name,
            tool_input=tool_input,
            tool_output=result,
            is_valid=result.success,
            validation_error=result.error if not result.success else None,
        )

    async def _generate_observation(
        self,
        tool_step: ReasoningStep,
        trace: ReasoningTrace,
    ) -> ReasoningStep:
        """生成观察/反思步骤"""
        if not tool_step.is_valid:
            return ReasoningStep(
                step_type=ReasoningStepType.OBSERVATION,
                content=f"观察到工具执行失败: {tool_step.validation_error}",
                metadata={"needs_retry": True, "is_final_answer": False},
            )

        output = tool_step.tool_output
        if output and output.data:
            content = f"获得数据: {json.dumps(output.data, ensure_ascii=False)[:200]}..."
        else:
            content = "工具返回空结果"

        return ReasoningStep(
            step_type=ReasoningStepType.OBSERVATION,
            content=content,
            metadata={
                "has_data": output is not None and output.data is not None,
                "is_final_answer": False,
            },
        )
