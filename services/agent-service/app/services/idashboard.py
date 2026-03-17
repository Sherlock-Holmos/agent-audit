"""数据看板服务接口"""

from abc import ABC, abstractmethod


class IDashboardClient(ABC):
    """数据看板客户端接口 - 与 data-service 通信"""

    @abstractmethod
    async def fetch_dashboard(self, username: str) -> dict:
        """
        从 data-service 获取用户的数据看板快照

        Args:
            username: 用户名

        Returns:
            看板数据，包含 completedRate、overdueCount、departmentRank 等指标
        """
        pass
