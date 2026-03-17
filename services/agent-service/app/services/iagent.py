"""Agent 服务的抽象基类定义 - 支持多 LLM 提供商和 RAG 扩展"""

from abc import ABC, abstractmethod
from typing import Optional


class ILLMProvider(ABC):
    """LLM 提供商接口 - 支持 OpenAI、Azure OpenAI、Mock 等"""

    @abstractmethod
    async def generate(self, prompt: str, history: list[dict]) -> str:
        """
        生成文本响应

        Args:
            prompt: 系统提示词
            history: 对话历史

        Returns:
            生成的文本响应
        """
        pass


class IRAGRetriever(ABC):
    """RAG 检索器接口 - 支持 Chroma、pgvector 等向量存储"""

    @abstractmethod
    async def retrieve(self, question: str, k: int = 3) -> list[str]:
        """
        从知识库检索相关文档

        Args:
            question: 用户问题
            k: 返回的文档数量

        Returns:
            相关文档片段列表
        """
        pass


class IAgentService(ABC):
    """Agent 服务接口"""

    @abstractmethod
    async def run_agent(
        self,
        question: str,
        history: list[dict],
        dashboard: dict,
    ) -> str:
        """
        执行一次对话推理

        Args:
            question: 用户问题
            history: 对话历史列表，每条含 "q" / "a" / "ts" 字段
            dashboard: 看板数据（来自 data-service），含 completedRate、overdueCount 等

        Returns:
            LLM 生成的回答字符串
        """
        pass
