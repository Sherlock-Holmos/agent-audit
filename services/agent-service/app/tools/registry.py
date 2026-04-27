"""工具注册中心 - 动态工具管理"""

import logging
from typing import Optional, Callable
from collections import defaultdict

from app.tools.base import BaseTool, ToolCategory

logger = logging.getLogger(__name__)


class ToolRegistry:
    """工具注册中心 - 单例模式"""
    _instance: Optional["ToolRegistry"] = None
    _initialized: bool = False

    def __new__(cls) -> "ToolRegistry":
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    def __init__(self):
        if ToolRegistry._initialized:
            return
        self._tools: dict[str, BaseTool] = {}
        self._by_category: dict[ToolCategory, list[str]] = defaultdict(list)
        self._factories: dict[str, Callable[[], BaseTool]] = {}
        ToolRegistry._initialized = True

    def register(self, tool: BaseTool, override: bool = False) -> "ToolRegistry":
        """注册工具"""
        name = tool.metadata.name
        if name in self._tools and not override:
            logger.warning(f"Tool '{name}' already registered, skipping")
            return self

        self._tools[name] = tool
        self._by_category[tool.metadata.category].append(name)
        logger.info(f"Registered tool: {name} (category: {tool.metadata.category.value})")
        return self

    def register_factory(self, name: str, factory: Callable[[], BaseTool]) -> "ToolRegistry":
        """注册工具工厂（延迟加载）"""
        self._factories[name] = factory
        logger.info(f"Registered tool factory: {name}")
        return self

    def get(self, name: str) -> Optional[BaseTool]:
        """获取工具"""
        # 检查已实例化
        if name in self._tools:
            return self._tools[name]

        # 尝试工厂创建
        if name in self._factories:
            tool = self._factories[name]()
            self.register(tool)
            return tool

        return None

    def get_by_category(self, category: ToolCategory) -> list[BaseTool]:
        """按分类获取工具"""
        names = self._by_category.get(category, [])
        return [self.get(name) for name in names if self.get(name)]

    def list_tools(self) -> list[str]:
        """列出所有工具名称"""
        all_names = set(self._tools.keys()) | set(self._factories.keys())
        return sorted(all_names)

    def list_all(self) -> list[dict]:
        """列出所有工具详情"""
        result = []
        all_names = set(self._tools.keys()) | set(self._factories.keys())
        for name in all_names:
            tool = self.get(name)
            if tool:
                result.append({
                    "name": tool.metadata.name,
                    "description": tool.metadata.description,
                    "category": tool.metadata.category.value,
                    "parameters": tool.metadata.parameters,
                })
        return result

    def unregister(self, name: str) -> bool:
        """注销工具"""
        if name not in self._tools:
            return False

        tool = self._tools[name]
        del self._tools[name]

        # 从分类中移除
        cat = tool.metadata.category
        if name in self._by_category[cat]:
            self._by_category[cat].remove(name)

        logger.info(f"Unregistered tool: {name}")
        return True

    def clear(self) -> None:
        """清空所有工具"""
        self._tools.clear()
        self._by_category.clear()
        self._factories.clear()
        logger.info("Tool registry cleared")

    def get_langchain_tools(self) -> list[dict]:
        """获取所有工具的 LangChain 格式"""
        return [tool.to_langchain_tool() for tool in self._tools.values()]


# 全局注册中心实例
registry = ToolRegistry()


def register_tool(tool: BaseTool) -> BaseTool:
    """装饰器风格注册工具"""
    registry.register(tool)
    return tool
