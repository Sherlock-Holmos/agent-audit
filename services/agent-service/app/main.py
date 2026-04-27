import json
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.routers import chat, health
from app.services.session import session_service
from app.services.agent_service import agent_service

# ── 结构化 JSON 日志 ───────────────────────────────────────────────────
class _JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        log = {
            "ts": self.formatTime(record, "%Y-%m-%dT%H:%M:%S"),
            "level": record.levelname,
            "app": "agent-service",
            "logger": record.name,
            "msg": record.getMessage(),
        }
        if record.exc_info:
            log["exc"] = self.formatException(record.exc_info)
        return json.dumps(log, ensure_ascii=False)


_handler = logging.StreamHandler()
_handler.setFormatter(_JsonFormatter())
logging.root.setLevel(logging.INFO)
logging.root.handlers = [_handler]

logger = logging.getLogger(__name__)


# ── 生命周期 ──────────────────────────────────────────────────────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("agent-service starting up")
    # 初始化Agent服务
    await agent_service._ensure_initialized()
    logger.info(f"Agent service initialized with {len(agent_service._agent._reasoning_engine._step_hooks if hasattr(agent_service._agent._reasoning_engine, '_step_hooks') else 0)} hooks")
    yield
    await session_service.close()
    logger.info("agent-service shut down")


# ── 应用实例 ──────────────────────────────────────────────────────────────
app = FastAPI(
    title="Agent Service",
    version="2.0.0",
    lifespan=lifespan,
    docs_url="/api/agent/docs",
    openapi_url="/api/agent/openapi.json",
)

app.include_router(chat.router)
app.include_router(health.router)
