"""会话管理服务接口"""

from abc import ABC, abstractmethod


class ISessionManager(ABC):
    """会话管理器接口 - 处理限流和多轮对话历史"""

    @abstractmethod
    async def try_acquire_quota(self, username: str) -> bool:
        """
        尝试获取请求配额（限流检查）

        Args:
            username: 用户名

        Returns:
            是否获得配额（True 表示可以继续请求）
        """
        pass

    @abstractmethod
    async def get_recent_history(self, username: str) -> list[dict]:
        """
        获取用户最近的对话历史

        Args:
            username: 用户名

        Returns:
            对话历史列表，每条含 "q"、"a"、"ts" 字段
        """
        pass

    @abstractmethod
    async def append_turn(self, username: str, question: str, answer: str) -> None:
        """
        添加一轮对话到历史记录

        Args:
            username: 用户名
            question: 用户问题
            answer: AI 回答
        """
        pass

    @abstractmethod
    async def close(self) -> None:
        """关闭会话资源"""
        pass
