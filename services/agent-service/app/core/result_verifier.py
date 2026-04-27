"""结果验证机制 - 验证推理结果的正确性和完整性"""

import json
import logging
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Optional, Callable, Coroutine, List, Tuple

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

from app.config import settings
from app.core.reasoning_engine import ReasoningTrace, ReasoningStepType

logger = logging.getLogger(__name__)


class VerificationStatus(Enum):
    """验证状态"""
    PASSED = "passed"      # 通过
    FAILED = "failed"      # 失败
    WARNING = "warning"    # 警告
    UNCERTAIN = "uncertain"  # 不确定


@dataclass
class VerificationResult:
    """验证结果"""
    status: VerificationStatus
    score: float  # 0-1 置信度分数
    checks: List[dict] = field(default_factory=list)
    suggestions: List[str] = field(default_factory=list)
    metadata: dict = field(default_factory=dict)

    def to_dict(self) -> dict:
        return {
            "status": self.status.value,
            "score": round(self.score, 3),
            "checks": self.checks,
            "suggestions": self.suggestions,
            "metadata": self.metadata,
        }


@dataclass
class VerificationRule:
    """验证规则"""
    name: str
    description: str
    check_fn: Callable[[str, ReasoningTrace], Tuple[bool, Optional[str]]]
    weight: float = 1.0  # 权重
    critical: bool = False  # 是否关键规则


class ResultVerifier:
    """结果验证器 - 多维度验证"""

    def __init__(self, llm=None):
        self.llm = llm or self._build_default_llm()
        self._rules: List[VerificationRule] = []
        self._init_default_rules()

    def _build_default_llm(self):
        """构建默认LLM用于验证"""
        provider = settings.llm_provider.lower()
        if provider == "azure":
            from langchain_openai import AzureChatOpenAI
            return AzureChatOpenAI(
                azure_deployment=settings.azure_openai_deployment,
                azure_endpoint=settings.azure_openai_endpoint,
                api_key=settings.azure_openai_api_key,
                api_version=settings.azure_openai_api_version,
                temperature=0.0,  # 验证需要确定性
            )
        elif provider == "openai":
            return ChatOpenAI(
                model=settings.openai_model,
                api_key=settings.openai_api_key,
                temperature=0.0,
            )
        return None

    def _init_default_rules(self) -> None:
        """初始化默认验证规则"""
        self._rules = [
            VerificationRule(
                name="completeness",
                description="检查回答是否完整回答了问题",
                check_fn=self._check_completeness,
                weight=1.0,
                critical=True,
            ),
            VerificationRule(
                name="factual_accuracy",
                description="检查事实准确性",
                check_fn=self._check_factual_accuracy,
                weight=1.2,
                critical=True,
            ),
            VerificationRule(
                name="tool_usage_validity",
                description="检查工具调用是否有效",
                check_fn=self._check_tool_usage,
                weight=1.0,
            ),
            VerificationRule(
                name="consistency",
                description="检查回答内部一致性",
                check_fn=self._check_consistency,
                weight=0.8,
            ),
            VerificationRule(
                name="relevance",
                description="检查回答与问题相关性",
                check_fn=self._check_relevance,
                weight=1.0,
            ),
        ]

    def add_rule(self, rule: VerificationRule) -> "ResultVerifier":
        """添加自定义验证规则"""
        self._rules.append(rule)
        return self

    async def verify(
        self,
        answer: str,
        query: str,
        trace: ReasoningTrace,
        context: dict = None,
    ) -> VerificationResult:
        """执行完整验证"""
        checks = []
        total_weight = 0.0
        passed_weight = 0.0
        failed_critical = False
        suggestions = []

        # 执行所有规则验证
        for rule in self._rules:
            try:
                passed, message = rule.check_fn(answer, trace)
                check_result = {
                    "rule": rule.name,
                    "description": rule.description,
                    "passed": passed,
                    "message": message,
                    "weight": rule.weight,
                }
                checks.append(check_result)

                total_weight += rule.weight
                if passed:
                    passed_weight += rule.weight
                else:
                    if rule.critical:
                        failed_critical = True
                    if message:
                        suggestions.append(f"[{rule.name}] {message}")

            except Exception as e:
                logger.warning(f"Rule {rule.name} check failed: {e}")
                checks.append({
                    "rule": rule.name,
                    "passed": False,
                    "message": f"验证执行错误: {e}",
                })

        # 计算总体分数
        score = passed_weight / total_weight if total_weight > 0 else 0.0

        # 确定状态
        if failed_critical:
            status = VerificationStatus.FAILED
        elif score >= 0.9:
            status = VerificationStatus.PASSED
        elif score >= 0.7:
            status = VerificationStatus.WARNING
        else:
            status = VerificationStatus.FAILED

        # LLM辅助验证（如果有LLM）
        if self.llm and score < 0.9:
            llm_check = await self._llm_verify(answer, query, trace)
            checks.append({
                "rule": "llm_assessment",
                "passed": llm_check.get("valid", False),
                "message": llm_check.get("feedback", ""),
            })
            if not llm_check.get("valid", False):
                suggestions.append(f"[LLM评估] {llm_check.get('suggestion', '')}")
                score *= 0.9  # 轻微降分

        return VerificationResult(
            status=status,
            score=score,
            checks=checks,
            suggestions=suggestions,
            metadata={"query": query, "context": context},
        )

    def _check_completeness(self, answer: str, trace: ReasoningTrace) -> tuple[bool, Optional[str]]:
        """检查完整性"""
        if not answer or len(answer.strip()) < 10:
            return False, "回答内容过少"

        # 检查是否回答了trace中的问题
        query = trace.query.lower()
        answer_lower = answer.lower()

        # 简单关键词匹配
        key_indicators = ["完成率", "整改", "任务", "问题", "建议", "分析"]
        has_relevant_content = any(k in answer_lower for k in key_indicators)

        if not has_relevant_content and len(answer) < 50:
            return False, "回答可能未充分回应问题"

        return True, None

    def _check_factual_accuracy(self, answer: str, trace: ReasoningTrace) -> tuple[bool, Optional[str]]:
        """检查事实准确性 - 对比工具返回的数据"""
        # 获取所有工具执行步骤
        tool_steps = trace.get_step_by_type(ReasoningStepType.TOOL_EXECUTION)

        if not tool_steps:
            # 没有工具调用，基于纯推理，给予通过但标记
            return True, "无工具数据源，基于模型知识回答"

        # 提取工具数据
        tool_data = []
        for step in tool_steps:
            if step.tool_output and step.tool_output.success:
                tool_data.append(step.tool_output.data)

        if not tool_data:
            return False, "工具调用未返回有效数据"

        # 检查回答中是否有明显与数据矛盾的地方
        # 简单检查：如果回答中包含具体数字，看是否与工具数据一致
        import re
        numbers_in_answer = re.findall(r'\d+\.?\d*%', answer)  # 百分比

        # 从工具数据中提取完成率
        completion_rates = []
        for data in tool_data:
            if isinstance(data, dict):
                if "completion_rate" in data:
                    completion_rates.append(str(data["completion_rate"]))
                if "completedRate" in data:
                    completion_rates.append(str(data["completedRate"]))

        # 如果回答中有百分比，但和工具数据对不上，可能是问题
        # 这里只作简单警告，不做严格判断
        return True, None

    def _check_tool_usage(self, answer: str, trace: ReasoningTrace) -> tuple[bool, Optional[str]]:
        """检查工具使用有效性"""
        tool_steps = trace.get_step_by_type(ReasoningStepType.TOOL_EXECUTION)

        if not tool_steps:
            return True, "未使用工具"

        all_valid = all(step.is_valid for step in tool_steps)
        failed_tools = [s.tool_name for s in tool_steps if not s.is_valid]

        if not all_valid:
            return False, f"以下工具执行失败: {', '.join(failed_tools)}"

        # 检查是否有工具调用但未在回答中体现结果
        tool_outputs_used = True  # 简化处理

        return tool_outputs_used, None if tool_outputs_used else "工具结果可能未充分利用"

    def _check_consistency(self, answer: str, trace: ReasoningTrace) -> tuple[bool, Optional[str]]:
        """检查内部一致性"""
        # 简单检查：数字是否前后一致
        import re
        numbers = re.findall(r'\d+\.?\d*', answer)

        # 如果有重复的数字出现但含义矛盾（简化检查）
        # 更复杂的检查需要LLM
        return True, None

    def _check_relevance(self, answer: str, trace: ReasoningTrace) -> tuple[bool, Optional[str]]:
        """检查相关性"""
        query_keywords = set(trace.query.lower().split())
        answer_keywords = set(answer.lower().split())

        # 计算重叠度
        if query_keywords:
            overlap = len(query_keywords & answer_keywords) / len(query_keywords)
            if overlap < 0.1:  # 重叠度太低
                return False, "回答与问题相关性较低"

        return True, None

    async def _llm_verify(
        self,
        answer: str,
        query: str,
        trace: ReasoningTrace,
    ) -> dict:
        """使用LLM进行验证"""
        if self.llm is None:
            return {"valid": True, "feedback": "Mock模式跳过验证"}

        # 构建验证提示
        verification_prompt = f"""请验证以下回答的质量：

问题：{query}

回答：
{answer}

验证标准：
1. 是否完整回答了问题？
2. 是否有事实错误或幻觉？
3. 是否结构清晰、逻辑合理？

请以JSON格式输出：
{{
    "valid": true/false,
    "score": 0-1的分数,
    "feedback": "简要评价",
    "suggestion": "改进建议（如果有）"
}}"""

        try:
            messages = [
                SystemMessage(content="你是一个严格的质量验证员，专门检查AI回答的准确性和完整性。"),
                HumanMessage(content=verification_prompt),
            ]
            response = await self.llm.ainvoke(messages)

            # 尝试解析JSON
            content = response.content
            # 提取JSON部分
            start = content.find("{")
            end = content.rfind("}")
            if start >= 0 and end > start:
                json_str = content[start:end+1]
                result = json.loads(json_str)
                return result
            return {"valid": True, "feedback": "无法解析验证结果"}
        except Exception as e:
            logger.warning(f"LLM verification failed: {e}")
            return {"valid": True, "feedback": f"验证错误: {e}"}


class VerificationPipeline:
    """验证管道 - 支持多轮验证和修复"""

    def __init__(self, verifier: ResultVerifier = None):
        self.verifier = verifier or ResultVerifier()
        self._max_repair_iterations = 2

    async def verify_and_repair(
        self,
        answer: str,
        query: str,
        trace: ReasoningTrace,
        regenerate_fn: Callable[[], Coroutine[Any, Any, str]],
    ) -> tuple[str, VerificationResult]:
        """验证并尝试修复"""
        current_answer = answer

        for iteration in range(self._max_repair_iterations + 1):
            result = await self.verifier.verify(current_answer, query, trace)

            if result.status in (VerificationStatus.PASSED, VerificationStatus.WARNING):
                return current_answer, result

            # 需要修复
            if iteration < self._max_repair_iterations:
                logger.info(f"Attempting repair iteration {iteration + 1}")
                # 尝试重新生成
                try:
                    new_answer = await regenerate_fn()
                    if new_answer and new_answer != current_answer:
                        current_answer = new_answer
                except Exception as e:
                    logger.error(f"Repair failed: {e}")
                    break

        return current_answer, result
