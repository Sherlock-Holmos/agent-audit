"""ReAct Agent 实现 - 显式推理步骤 + 动态工具选择 + 结果验证"""

import json
import logging
from typing import AsyncIterator, Optional, Any
from collections.abc import AsyncGenerator

from app.agents.base import BaseAgent, AgentResponse
from app.core.reasoning_engine import ReasoningEngine, ReasoningTrace, ReasoningStep, ReasoningStepType
from app.core.result_verifier import ResultVerifier, VerificationPipeline
from app.tools.registry import registry

logger = logging.getLogger(__name__)


class ReActAgent(BaseAgent):
    """
    ReAct Agent - 显式推理步骤分解

    特性：
    1. 显式推理步骤 - Thought -> Action -> Observation -> ... -> Answer
    2. 动态工具选择 - 根据上下文自动选择合适工具
    3. 结果验证 - 多维度验证回答质量
    4. 流式输出 - 支持逐步推理过程展示
    """

    def __init__(
        self,
        name: str = "react_agent",
        llm=None,
        max_iterations: int = 10,
        enable_verification: bool = True,
        enable_repair: bool = True,
    ):
        super().__init__(name)
        self.llm = llm
        self.max_iterations = max_iterations
        self.enable_verification = enable_verification
        self.enable_repair = enable_repair

        self._reasoning_engine: Optional[ReasoningEngine] = None
        self._verifier: Optional[ResultVerifier] = None
        self._verification_pipeline: Optional[VerificationPipeline] = None

    async def _setup(self) -> None:
        """初始化组件"""
        logger.info(f"Initializing {self.name}...")

        # 初始化推理引擎
        self._reasoning_engine = ReasoningEngine(llm=self.llm)
        self._reasoning_engine._max_iterations = self.max_iterations

        # 初始化验证器
        if self.enable_verification:
            self._verifier = ResultVerifier(llm=self.llm)
            if self.enable_repair:
                self._verification_pipeline = VerificationPipeline(self._verifier)

        logger.info(f"{self.name} initialized with {len(registry.list_tools())} tools")

    async def run(
        self,
        query: str,
        context: dict = None,
        history: list[dict] = None,
    ) -> AgentResponse:
        """执行推理并返回答案"""
        await self.initialize()

        context = context or {}
        logger.info(f"[{self.name}] Processing query: {query[:50]}...")

        # 1. 执行推理
        trace = await self._reasoning_engine.reason(
            query=query,
            context=context,
            history=history,
            stream=False,
        )

        if not isinstance(trace, ReasoningTrace):
            trace = ReasoningTrace(query=query)
            trace.complete(str(trace))

        answer = trace.final_answer or "[未能生成有效回答]"

        # 2. 验证结果（如果启用）
        verification = None
        if self.enable_verification and self._verifier:
            if self.enable_repair and self._verification_pipeline:
                answer, verification = await self._verification_pipeline.verify_and_repair(
                    answer=answer,
                    query=query,
                    trace=trace,
                    regenerate_fn=lambda: self._regenerate(query, context, history),
                )
            else:
                verification = await self._verifier.verify(answer, query, trace, context)

        # 3. 计算置信度
        confidence = self._calculate_confidence(trace, verification)

        # 更新最终答案到trace
        trace.complete(answer)

        return AgentResponse(
            answer=answer,
            trace=trace,
            verification=verification,
            confidence=confidence,
            metadata={
                "step_count": len(trace.steps),
                "tools_used": [s.tool_name for s in trace.steps if s.tool_name],
                "iterations": len(trace.get_step_by_type(ReasoningStepType.THOUGHT)),
            },
        )

    async def stream(
        self,
        query: str,
        context: dict = None,
        history: list[dict] = None,
    ) -> AsyncIterator[str | dict]:
        """流式执行 - 输出推理过程"""
        await self.initialize()

        context = context or {}
        collected_answer_parts = []
        final_answer = ""

        # 发送开始标记
        yield {"type": "start", "agent": self.name, "query": query}

        # 执行流式推理
        step_iterator = await self._reasoning_engine.reason(
            query=query,
            context=context,
            history=history,
            stream=True,
        )

        async for step in step_iterator:
            # 发送推理步骤
            step_data = {
                "type": "step",
                "step_type": step.step_type.value,
                "content": step.content,
                "timestamp": step.timestamp.isoformat(),
            }

            if step.tool_name:
                step_data["tool_name"] = step.tool_name
                step_data["tool_input"] = step.tool_input
                step_data["tool_success"] = step.is_valid

            yield step_data

            # 收集最终答案
            if step.step_type == ReasoningStepType.ANSWER:
                final_answer = step.content
                break

        # 验证（如果启用且不是mock模式）
        verification_data = None
        if self.enable_verification and self._verifier and final_answer:
            # 重建trace用于验证
            trace = ReasoningTrace(query=query)
            trace.complete(final_answer)

            verification = await self._verifier.verify(final_answer, query, trace, context)
            verification_data = verification.to_dict()

            yield {
                "type": "verification",
                "verification": verification_data,
            }

        # 发送最终结果
        yield {
            "type": "final",
            "answer": final_answer,
            "confidence": self._calculate_confidence(None, None),
            "verification": verification_data,
        }

        yield {"type": "done"}

    async def _regenerate(
        self,
        query: str,
        context: dict,
        history: list[dict],
    ) -> str:
        """重新生成答案（用于修复）"""
        logger.info("Regenerating answer with additional context...")
        # 添加上下文提示重新推理
        enhanced_context = {
            **context,
            "regenerate": True,
            "hint": "请确保回答准确、完整，并基于可用数据。",
        }
        trace = await self._reasoning_engine.reason(
            query=query,
            context=enhanced_context,
            history=history,
            stream=False,
        )
        return trace.final_answer if isinstance(trace, ReasoningTrace) else str(trace)

    def _calculate_confidence(
        self,
        trace: Optional[ReasoningTrace],
        verification: Optional[Any],
    ) -> float:
        """计算置信度分数"""
        score = 0.5  # 基础分

        if trace:
            # 有推理步骤增加置信度
            step_count = len(trace.steps)
            if step_count > 0:
                score += 0.1 * min(step_count / 3, 0.3)  # 最多加0.3

            # 工具执行成功率
            tool_steps = trace.get_step_by_type(ReasoningStepType.TOOL_EXECUTION)
            if tool_steps:
                success_rate = sum(1 for s in tool_steps if s.is_valid) / len(tool_steps)
                score += 0.2 * success_rate

        if verification:
            # 验证分数
            score = score * 0.5 + verification.score * 0.5

        return round(min(score, 1.0), 3)

    async def get_available_tools(self) -> list[dict]:
        """获取可用工具列表"""
        return registry.list_all()

    async def execute_tool_directly(
        self,
        tool_name: str,
        params: dict,
    ) -> dict:
        """直接执行工具（用于测试或特定场景）"""
        tool = registry.get(tool_name)
        if not tool:
            return {"success": False, "error": f"Tool '{tool_name}' not found"}

        result = await tool.execute(**params)
        return result.to_dict()


class StreamingReActAgent(ReActAgent):
    """增强版流式 ReAct Agent"""

    async def stream_sse(
        self,
        query: str,
        context: dict = None,
        history: list[dict] = None,
    ) -> AsyncGenerator[str, None]:
        """生成 SSE 格式的流式输出"""
        async for event in self.stream(query, context, history):
            if isinstance(event, dict):
                yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
            else:
                yield f"data: {json.dumps({'type': 'chunk', 'content': str(event)}, ensure_ascii=False)}\n\n"

        yield "data: [DONE]\n\n"
