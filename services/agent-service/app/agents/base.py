"""Agent 基类定义"""

from abc import ABC, abstractmethod
from typing import Any, AsyncIterator, Optional
from dataclasses import dataclass

from app.core.reasoning_engine import ReasoningTrace
from app.core.result_verifier import VerificationResult


@dataclass
class AgentResponse:
    """Agent 响应"""
    answer: str
    trace: Optional[ReasoningTrace] = None
    verification: Optional[VerificationResult] = None
    metadata: dict = None
    confidence: float = 0.0

    def __post_init__(self):
        if self.metadata is None:
            self.metadata = {}


class BaseAgent(ABC):
    """Agent 基类"""

    def __init__(self, name: str = "base_agent"):
        self.name = name
        self._initialized = False

    async def initialize(self) -> None:
        """初始化Agent"""
        if not self._initialized:
            await self._setup()
            self._initialized = True

    @abstractmethod
    async def _setup(self) -> None:
        """子类实现初始化逻辑"""
        pass

    @abstractmethod
    async def run(
        self,
        query: str,
        context: dict = None,
        history: list[dict] = None,
    ) -> AgentResponse:
        """执行Agent运行"""
        pass

    @abstractmethod
    async def stream(
        self,
        query: str,
        context: dict = None,
        history: list[dict] = None,
    ) -> AsyncIterator[str | dict]:
        """流式执行"""
        pass

    async def health_check(self) -> dict:
        """健康检查"""
        return {"status": "ok", "agent": self.name}
