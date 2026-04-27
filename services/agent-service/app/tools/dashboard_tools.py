"""数据看板相关工具"""

import logging
from typing import Any

from app.tools.base import BaseTool, ToolResult, ToolMetadata, ToolCategory
from app.tools.registry import register_tool
from app.services.dashboard import dashboard_client

logger = logging.getLogger(__name__)


class FetchDashboardTool(BaseTool):
    """获取数据看板信息"""

    @property
    def metadata(self) -> ToolMetadata:
        return ToolMetadata(
            name="fetch_dashboard",
            description="获取用户的数据看板快照，包括整改完成率、逾期任务数、问题列表等",
            category=ToolCategory.DATA_QUERY,
            parameters={
                "username": {
                    "type": "string",
                    "description": "用户名，用于获取该用户的数据看板",
                }
            },
            required_params=["username"],
            timeout_seconds=5.0,
            retry_times=2,
        )

    async def _execute(self, **kwargs) -> ToolResult:
        username = kwargs.get("username")
        try:
            data = await dashboard_client.fetch_dashboard(username)
            return ToolResult.ok(data, metadata={"source": "data-service"})
        except Exception as e:
            logger.error(f"Failed to fetch dashboard for {username}: {e}")
            return ToolResult.fail(f"Dashboard fetch failed: {e}")


class QueryCompletionRateTool(BaseTool):
    """查询整改完成率"""

    @property
    def metadata(self) -> ToolMetadata:
        return ToolMetadata(
            name="query_completion_rate",
            description="查询指定用户的整改完成率百分比",
            category=ToolCategory.DATA_QUERY,
            parameters={
                "username": {"type": "string", "description": "目标用户名"},
            },
            required_params=["username"],
            timeout_seconds=3.0,
        )

    async def _execute(self, **kwargs) -> ToolResult:
        username = kwargs.get("username")
        try:
            data = await dashboard_client.fetch_dashboard(username)
            rate = data.get("completedRate", "N/A")
            return ToolResult.ok(
                {"completion_rate": rate, "username": username},
                metadata={"raw_data": data}
            )
        except Exception as e:
            return ToolResult.fail(f"Query failed: {e}")


class QueryOverdueTasksTool(BaseTool):
    """查询逾期任务"""

    @property
    def metadata(self) -> ToolMetadata:
        return ToolMetadata(
            name="query_overdue_tasks",
            description="查询逾期任务列表和统计",
            category=ToolCategory.DATA_QUERY,
            parameters={
                "username": {"type": "string", "description": "目标用户名"},
                "limit": {
                    "type": "integer",
                    "description": "返回的最大任务数",
                    "default": 10,
                },
            },
            required_params=["username"],
        )

    async def _execute(self, **kwargs) -> ToolResult:
        username = kwargs.get("username")
        limit = kwargs.get("limit", 10)
        try:
            data = await dashboard_client.fetch_dashboard(username)
            tasks = data.get("tasks", [])
            overdue = [t for t in tasks if t.get("status") == "overdue"]
            return ToolResult.ok({
                "overdue_count": len(overdue),
                "overdue_tasks": overdue[:limit],
                "total_tasks": len(tasks),
            })
        except Exception as e:
            return ToolResult.fail(f"Query failed: {e}")


class QueryIssuesTool(BaseTool):
    """查询问题列表"""

    @property
    def metadata(self) -> ToolMetadata:
        return ToolMetadata(
            name="query_issues",
            description="查询审计问题列表，支持按严重程度和状态筛选",
            category=ToolCategory.DATA_QUERY,
            parameters={
                "username": {"type": "string", "description": "目标用户名"},
                "severity": {
                    "type": "string",
                    "description": "严重程度筛选: high/medium/low",
                    "enum": ["high", "medium", "low"],
                },
                "status": {
                    "type": "string",
                    "description": "状态筛选: open/closed/pending",
                    "enum": ["open", "closed", "pending"],
                },
            },
            required_params=["username"],
        )

    async def _execute(self, **kwargs) -> ToolResult:
        username = kwargs.get("username")
        severity = kwargs.get("severity")
        status = kwargs.get("status")
        try:
            data = await dashboard_client.fetch_dashboard(username)
            issues = data.get("issues", [])

            # 筛选
            filtered = issues
            if severity:
                filtered = [i for i in filtered if i.get("severity") == severity]
            if status:
                filtered = [i for i in filtered if i.get("status") == status]

            return ToolResult.ok({
                "total_issues": len(issues),
                "filtered_count": len(filtered),
                "issues": filtered,
            })
        except Exception as e:
            return ToolResult.fail(f"Query failed: {e}")


# 注册所有看板工具
fetch_dashboard_tool = register_tool(FetchDashboardTool())
query_completion_rate_tool = register_tool(QueryCompletionRateTool())
query_overdue_tasks_tool = register_tool(QueryOverdueTasksTool())
query_issues_tool = register_tool(QueryIssuesTool())
