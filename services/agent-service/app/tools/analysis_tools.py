"""分析类工具"""

import logging
from typing import Any
from datetime import datetime

from app.tools.base import BaseTool, ToolResult, ToolMetadata, ToolCategory
from app.tools.registry import register_tool

logger = logging.getLogger(__name__)


class AnalyzeTrendTool(BaseTool):
    """分析整改趋势"""

    @property
    def metadata(self) -> ToolMetadata:
        return ToolMetadata(
            name="analyze_trend",
            description="分析整改完成率的变化趋势，判断是上升还是下降",
            category=ToolCategory.ANALYSIS,
            parameters={
                "current_rate": {
                    "type": "number",
                    "description": "当前完成率 (0-100)",
                },
                "previous_rate": {
                    "type": "number",
                    "description": "上期完成率 (0-100)",
                },
                "time_period": {
                    "type": "string",
                    "description": "时间周期，如 'week', 'month', 'quarter'",
                    "default": "month",
                },
            },
            required_params=["current_rate", "previous_rate"],
        )

    async def _execute(self, **kwargs) -> ToolResult:
        current = float(kwargs.get("current_rate", 0))
        previous = float(kwargs.get("previous_rate", 0))
        period = kwargs.get("time_period", "month")

        delta = current - previous
        trend = "up" if delta > 0 else "down" if delta < 0 else "stable"

        analysis = {
            "trend": trend,
            "current_rate": current,
            "previous_rate": previous,
            "change_percentage": round(delta, 2),
            "period": period,
            "assessment": self._assess_trend(current, delta),
        }
        return ToolResult.ok(analysis)

    def _assess_trend(self, current: float, delta: float) -> str:
        if current >= 90:
            return "优秀"
        elif current >= 70:
            return "良好" if delta >= 0 else "需关注"
        elif current >= 50:
            return "一般" if delta >= 0 else "需改进"
        else:
            return "亟需改进"


class CalculateRiskScoreTool(BaseTool):
    """计算风险评分"""

    @property
    def metadata(self) -> ToolMetadata:
        return ToolMetadata(
            name="calculate_risk_score",
            description="基于逾期数量和完成率计算风险评分 (0-100，越高越危险)",
            category=ToolCategory.CALCULATION,
            parameters={
                "overdue_count": {
                    "type": "integer",
                    "description": "逾期任务数量",
                },
                "completion_rate": {
                    "type": "number",
                    "description": "完成率 (0-100)",
                },
                "total_tasks": {
                    "type": "integer",
                    "description": "总任务数",
                    "default": 100,
                },
            },
            required_params=["overdue_count", "completion_rate"],
        )

    async def _execute(self, **kwargs) -> ToolResult:
        overdue = int(kwargs.get("overdue_count", 0))
        completion = float(kwargs.get("completion_rate", 0))
        total = int(kwargs.get("total_tasks", 100))

        # 风险评分算法
        overdue_ratio = overdue / max(total, 1)
        incomplete_penalty = (100 - completion) * 0.5
        overdue_penalty = overdue_ratio * 100 * 0.5

        risk_score = min(100, incomplete_penalty + overdue_penalty)
        risk_level = "high" if risk_score > 70 else "medium" if risk_score > 40 else "low"

        return ToolResult.ok({
            "risk_score": round(risk_score, 2),
            "risk_level": risk_level,
            "factors": {
                "incomplete_penalty": round(incomplete_penalty, 2),
                "overdue_penalty": round(overdue_penalty, 2),
            },
        })


class SummarizeIssuesTool(BaseTool):
    """问题汇总分析"""

    @property
    def metadata(self) -> ToolMetadata:
        return ToolMetadata(
            name="summarize_issues",
            description="对问题列表进行汇总分析，按类型和严重程度统计",
            category=ToolCategory.ANALYSIS,
            parameters={
                "issues": {
                    "type": "array",
                    "description": "问题列表",
                    "items": {"type": "object"},
                },
            },
            required_params=["issues"],
        )

    async def _execute(self, **kwargs) -> ToolResult:
        issues = kwargs.get("issues", [])
        if not isinstance(issues, list):
            return ToolResult.fail("issues must be a list")

        # 按严重程度统计
        severity_count = {"high": 0, "medium": 0, "low": 0, "unknown": 0}
        # 按状态统计
        status_count = {"open": 0, "closed": 0, "pending": 0, "unknown": 0}
        # 按类型统计
        type_count = {}

        for issue in issues:
            sev = issue.get("severity", "unknown").lower()
            status = issue.get("status", "unknown").lower()
            issue_type = issue.get("type", "unknown")

            severity_count[sev] = severity_count.get(sev, 0) + 1
            status_count[status] = status_count.get(status, 0) + 1
            type_count[issue_type] = type_count.get(issue_type, 0) + 1

        summary = {
            "total": len(issues),
            "by_severity": severity_count,
            "by_status": status_count,
            "by_type": type_count,
            "critical_issues": severity_count.get("high", 0),
        }
        return ToolResult.ok(summary)


class ComparePerformanceTool(BaseTool):
    """绩效对比分析"""

    @property
    def metadata(self) -> ToolMetadata:
        return ToolMetadata(
            name="compare_performance",
            description="对比多个用户的整改绩效",
            category=ToolCategory.ANALYSIS,
            parameters={
                "user_data": {
                    "type": "array",
                    "description": "用户数据列表，每项包含 username, completion_rate, overdue_count",
                    "items": {"type": "object"},
                },
            },
            required_params=["user_data"],
        )

    async def _execute(self, **kwargs) -> ToolResult:
        user_data = kwargs.get("user_data", [])
        if not user_data or len(user_data) < 2:
            return ToolResult.fail("至少需要2个用户数据进行对比")

        # 排序
        sorted_by_rate = sorted(
            user_data,
            key=lambda x: float(x.get("completion_rate", 0)),
            reverse=True
        )
        sorted_by_overdue = sorted(
            user_data,
            key=lambda x: int(x.get("overdue_count", 0))
        )

        best = sorted_by_rate[0]
        worst = sorted_by_rate[-1]

        comparison = {
            "best_performer": {
                "username": best.get("username"),
                "completion_rate": best.get("completion_rate"),
            },
            "needs_improvement": {
                "username": worst.get("username"),
                "completion_rate": worst.get("completion_rate"),
            },
            "rankings": [
                {"rank": i+1, "username": u.get("username"), "rate": u.get("completion_rate")}
                for i, u in enumerate(sorted_by_rate)
            ],
            "average_completion_rate": round(
                sum(float(u.get("completion_rate", 0)) for u in user_data) / len(user_data), 2
            ),
        }
        return ToolResult.ok(comparison)


# 注册分析工具
analyze_trend_tool = register_tool(AnalyzeTrendTool())
calculate_risk_tool = register_tool(CalculateRiskScoreTool())
summarize_issues_tool = register_tool(SummarizeIssuesTool())
compare_performance_tool = register_tool(ComparePerformanceTool())
