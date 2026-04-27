"""数据看板客户端实现"""

import logging
from typing import Any

import httpx

from app.config import settings
from app.services.idashboard import IDashboardClient

logger = logging.getLogger(__name__)

_FALLBACK = {"completedRate": "N/A", "overdueCount": "N/A", "issues": [], "tasks": []}


class DashboardClient(IDashboardClient):
    """数据看板客户端实现 - 与 data-service 通信"""

    @staticmethod
    def _unwrap_payload(raw: Any) -> dict:
        if not isinstance(raw, dict):
            return {}
        # data-service 统一返回 ApiResponse，真实载荷在 data 字段。
        payload = raw.get("data")
        return payload if isinstance(payload, dict) else raw

    async def _fetch_payload_with_retry(self, path: str, headers: dict, timeout: float) -> dict:
        url = f"{settings.data_base_url}{path}"
        for attempt in range(settings.agent_dashboard_retry_times + 1):
            try:
                async with httpx.AsyncClient(timeout=timeout) as client:
                    resp = await client.get(url, headers=headers)
                    resp.raise_for_status()
                    return self._unwrap_payload(resp.json())
            except Exception as exc:
                logger.warning("fetch %s attempt=%d failed: %s", path, attempt + 1, exc)
        return {}

    async def fetch_dashboard(self, username: str) -> dict:
        """从 data-service 获取数据看板快照，带重试和超时降级。"""
        timeout = settings.agent_dashboard_timeout_ms / 1000.0
        headers = {"X-User-Name": username}

        dashboard_data = await self._fetch_payload_with_retry("/api/data/dashboard", headers, timeout)
        snapshot_data = await self._fetch_payload_with_retry("/api/data/rectification/snapshot", headers, timeout)

        merged: dict[str, Any] = {}
        if isinstance(dashboard_data, dict):
            merged.update(dashboard_data)
        if isinstance(snapshot_data, dict):
            merged["issues"] = snapshot_data.get("issues") if isinstance(snapshot_data.get("issues"), list) else []
            merged["tasks"] = snapshot_data.get("tasks") if isinstance(snapshot_data.get("tasks"), list) else []
            merged["users"] = snapshot_data.get("users") if isinstance(snapshot_data.get("users"), list) else []

        if not merged:
            logger.error("fetch_dashboard exhausted all retries, returning fallback")
            return dict(_FALLBACK)

        merged.setdefault("completedRate", "N/A")
        merged.setdefault("overdueCount", "N/A")
        merged.setdefault("issues", [])
        merged.setdefault("tasks", [])
        merged.setdefault("users", [])
        return merged


# 单例
dashboard_client = DashboardClient()


async def fetch_dashboard(username: str) -> dict:
    """兼容性包装 - 调用单例实例"""
    return await dashboard_client.fetch_dashboard(username)
