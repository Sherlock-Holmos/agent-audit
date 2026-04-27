"""Agent Service - 基于 LangChain Agent 框架的实现

特性：
1. 显式推理步骤分解（ReAct 模式）
2. 动态工具选择与调用
3. 推理结果验证机制
4. 高可扩展性 - 插件化工具系统
"""

import logging
from typing import AsyncIterator

from app.agents import ReActAgent, StreamingReActAgent, AgentResponse
from app.core import ReasoningTrace, VerificationResult
from app.services.iagent import IAgentService

logger = logging.getLogger(__name__)


class AgentService(IAgentService):
    """Agent 服务实现 - LangChain ReAct Agent"""

    def __init__(self):
        # 主 Agent 实例
        self._agent = ReActAgent(
            name="audit_agent",
            max_iterations=8,
            enable_verification=True,
            enable_repair=True,
        )
        # 流式 Agent 实例
        self._streaming_agent = StreamingReActAgent(
            name="audit_agent_stream",
            max_iterations=8,
            enable_verification=True,
            enable_repair=False,  # 流式模式下不做修复
        )
        self._initialized = False

    async def _ensure_initialized(self):
        """确保 Agent 已初始化"""
        if not self._initialized:
            await self._agent.initialize()
            await self._streaming_agent.initialize()
            self._initialized = True

    async def run_agent(
        self,
        question: str,
        history: list[dict],
        dashboard: dict,
        llm_config: dict | None = None,
    ) -> str:
        """
        执行一次对话推理（非流式）

        Returns:
            经推理和验证后的回答字符串
        """
        await self._ensure_initialized()

        context = self._build_context(dashboard, llm_config)

        logger.info(f"[AgentService] Processing question: {question[:50]}...")

        try:
            response = await self._agent.run(
                query=question,
                context=context,
                history=history,
            )

            # 记录推理统计
            if response.metadata:
                logger.info(
                    f"[AgentService] Completed: steps={response.metadata.get('step_count', 0)}, "
                    f"confidence={response.confidence}, "
                    f"tools={response.metadata.get('tools_used', [])}"
                )

            return response.answer

        except Exception as e:
            logger.error(f"[AgentService] Agent execution failed: {e}", exc_info=True)
            return self._build_error_response(question, dashboard, str(e))

    async def run_agent_stream(
        self,
        question: str,
        history: list[dict],
        dashboard: dict,
        llm_config: dict | None = None,
    ) -> AsyncIterator[str]:
        """
        流式执行对话推理

        Yields:
            推理步骤和最终答案的分片
        """
        await self._ensure_initialized()

        context = self._build_context(dashboard, llm_config)

        logger.info(f"[AgentService] Starting stream for: {question[:50]}...")

        try:
            async for event in self._streaming_agent.stream(
                query=question,
                context=context,
                history=history,
            ):
                # 流式事件处理
                if isinstance(event, dict):
                    if event.get("type") == "step":
                        step_type = event.get("step_type", "")
                        content = event.get("content", "")

                        # 只输出关键步骤的内容，避免过多中间信息
                        if step_type in ["answer", "observation"]:
                            yield content
                        elif step_type == "tool_exec":
                            # 工具执行结果简要提示
                            tool_name = event.get("tool_name", "")
                            if tool_name:
                                yield f"[{tool_name}] "

                    elif event.get("type") == "final":
                        answer = event.get("answer", "")
                        if answer:
                            yield answer

                elif isinstance(event, str):
                    yield event

        except Exception as e:
            logger.error(f"[AgentService] Stream execution failed: {e}", exc_info=True)
            yield self._build_error_response(question, dashboard, str(e))

    def _build_context(self, dashboard: dict, llm_config: dict | None) -> dict:
        """构建 Agent 上下文"""
        context = {
            "dashboard": dashboard,
            "username": dashboard.get("currentUser", "anonymous"),
            "llm_config": llm_config or {},
        }
        return context

    def _build_error_response(
        self,
        question: str,
        dashboard: dict,
        error: str,
    ) -> str:
        """构建错误响应"""
        completed_rate = dashboard.get("completedRate", "N/A")
        overdue_count = dashboard.get("overdueCount", "N/A")

        return (
            f"抱歉，处理您的问题时出现了错误。\n"
            f"\n当前数据：完成率 {completed_rate}%，逾期任务 {overdue_count}\n"
            f"\n请稍后重试或联系管理员。"
        )

    async def get_reasoning_trace(
        self,
        question: str,
        history: list[dict],
        dashboard: dict,
    ) -> ReasoningTrace:
        """
        获取完整推理轨迹（用于调试和分析）

        Returns:
            ReasoningTrace 对象包含完整推理步骤
        """
        await self._ensure_initialized()

        context = self._build_context(dashboard, None)

        response = await self._agent.run(
            query=question,
            context=context,
            history=history,
        )

        return response.trace

    async def get_verification_result(
        self,
        answer: str,
        question: str,
        history: list[dict],
        dashboard: dict,
    ) -> VerificationResult:
        """
        获取验证结果（用于质量监控）

        Returns:
            VerificationResult 验证结果对象
        """
        await self._ensure_initialized()

        from app.core import ResultVerifier

        verifier = ResultVerifier()

        # 构建临时 trace
        trace = ReasoningTrace(query=question)
        trace.complete(answer)

        context = self._build_context(dashboard, None)

        return await verifier.verify(answer, question, trace, context)


# 单例
agent_service = AgentService()
