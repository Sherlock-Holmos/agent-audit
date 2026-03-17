"""数据看板客户端实现"""

import logging

import httpx

from app.config import settings
from app.services.idashboard import IDashboardClient

logger = logging.getLogger(__name__)

_FALLBACK = {"completedRate": "N/A", "overdueCount": "N/A"}


class DashboardClient(IDashboardClient):
    """数据看板客户端实现 - 与 data-service 通信"""

    async def fetch_dashboard(self, username: str) -> dict:
        """从 data-service 获取数据看板快照，带重试和超时降级。"""
        timeout = settings.agent_dashboard_timeout_ms / 1000.0
        url = f"{settings.data_base_url}/api/data/dashboard"
        headers = {"X-User-Name": username}

        for attempt in range(settings.agent_dashboard_retry_times + 1):
            try:
                async with httpx.AsyncClient(timeout=timeout) as client:
                    resp = await client.get(url, headers=headers)
                    resp.raise_for_status()
                    return resp.json()
            except Exception as exc:
                logger.warning(
                    "fetch_dashboard attempt=%d failed: %s", attempt + 1, exc
                )

        logger.error("fetch_dashboard exhausted all retries, returning fallback")
        return _FALLBACK


# 单例
dashboard_client = DashboardClient()


async def fetch_dashboard(username: str) -> dict:
    """兼容性包装 - 调用单例实例"""
    return await dashboard_client.fetch_dashboard(username)
