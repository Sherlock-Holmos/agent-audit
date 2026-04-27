"""工具基类定义 - 高可扩展的工具系统"""

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any, Callable, Coroutine, Optional
from enum import Enum
import asyncio


class ToolCategory(Enum):
    """工具分类"""
    DATA_QUERY = "data_query"      # 数据查询类
    ANALYSIS = "analysis"          # 分析类
    CALCULATION = "calculation"    # 计算类
    VERIFICATION = "verification"  # 验证类
    EXTERNAL = "external"        # 外部服务类


@dataclass
class ToolResult:
    """工具执行结果"""
    success: bool
    data: Any = None
    error: Optional[str] = None
    metadata: dict = field(default_factory=dict)

    @classmethod
    def ok(cls, data: Any, metadata: dict = None) -> "ToolResult":
        return cls(success=True, data=data, metadata=metadata or {})

    @classmethod
    def fail(cls, error: str, metadata: dict = None) -> "ToolResult":
        return cls(success=False, error=error, metadata=metadata or {})


@dataclass
class ToolMetadata:
    """工具元数据"""
    name: str
    description: str
    category: ToolCategory
    parameters: dict[str, Any]  # JSON Schema 格式
    required_params: list[str]
    return_schema: Optional[dict] = None
    examples: list[dict] = field(default_factory=list)
    timeout_seconds: float = 30.0
    retry_times: int = 1


class BaseTool(ABC):
    """工具基类 - 所有工具必须继承"""

    def __init__(self):
        self._metadata: Optional[ToolMetadata] = None
        self._pre_hooks: list[Callable] = []
        self._post_hooks: list[Callable] = []

    @property
    @abstractmethod
    def metadata(self) -> ToolMetadata:
        """返回工具元数据"""
        pass

    @abstractmethod
    async def _execute(self, **kwargs) -> ToolResult:
        """实际执行逻辑，子类实现"""
        pass

    def add_pre_hook(self, hook: Callable[[dict], Coroutine]) -> "BaseTool":
        """添加前置钩子"""
        self._pre_hooks.append(hook)
        return self

    def add_post_hook(self, hook: Callable[[ToolResult], Coroutine]) -> "BaseTool":
        """添加后置钩子"""
        self._post_hooks.append(hook)
        return self

    async def execute(self, **kwargs) -> ToolResult:
        """执行工具，带钩子和错误处理"""
        # 前置钩子
        for hook in self._pre_hooks:
            try:
                await hook(kwargs)
            except Exception as e:
                return ToolResult.fail(f"Pre-hook failed: {e}")

        # 执行主体（带重试）
        result = None
        for attempt in range(self.metadata.retry_times):
            try:
                result = await asyncio.wait_for(
                    self._execute(**kwargs),
                    timeout=self.metadata.timeout_seconds
                )
                if result.success:
                    break
            except asyncio.TimeoutError:
                result = ToolResult.fail(f"Tool execution timeout after {self.metadata.timeout_seconds}s")
            except Exception as e:
                result = ToolResult.fail(f"Tool execution error: {e}")

        # 后置钩子
        for hook in self._post_hooks:
            try:
                await hook(result)
            except Exception as e:
                return ToolResult.fail(f"Post-hook failed: {e}")

        return result or ToolResult.fail("Unknown execution error")

    def to_langchain_tool(self) -> dict:
        """转换为 LangChain 工具格式"""
        return {
            "type": "function",
            "function": {
                "name": self.metadata.name,
                "description": self.metadata.description,
                "parameters": {
                    "type": "object",
                    "properties": self.metadata.parameters,
                    "required": self.metadata.required_params,
                },
            },
        }

    def validate_params(self, params: dict) -> tuple[bool, Optional[str]]:
        """验证参数"""
        for required in self.metadata.required_params:
            if required not in params:
                return False, f"Missing required parameter: {required}"
        return True, None
