import time
import logging

from fastapi import APIRouter, Header, HTTPException, status
from pydantic import BaseModel, field_validator

from app.metrics import (
    chat_requests_total,
    chat_rate_limited_total,
    chat_duration_seconds,
)
from app.services.session import session_service
from app.services.dashboard import fetch_dashboard
from app.services.agent import run_agent

router = APIRouter()
logger = logging.getLogger(__name__)


class ChatRequest(BaseModel):
    question: str

    @field_validator("question")
    @classmethod
    def question_not_blank(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("问题不能为空")
        return v.strip()


@router.post("/api/agent/chat", summary="多轮对话问答")
async def chat(
    payload: ChatRequest,
    x_user_name: str | None = Header(default=None, alias="X-User-Name"),
):
    """
    发起一次审计整改问答请求。

    - 携带 **X-User-Name** 请求头时按用户区分会话和限流配额。
    - 每用户每分钟最多 `AGENT_RATE_LIMIT_PER_MINUTE`（默认 30）次。
    - 自动携带最近 N 轮会话上下文传入 LLM。
    """
    t_start = time.perf_counter()
    chat_requests_total.inc()

    username = (x_user_name or "").strip() or "anonymous"

    if not await session_service.try_acquire_quota(username):
        chat_rate_limited_total.inc()
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="请求过于频繁，请稍后再试",
            headers={"Retry-After": "60"},
        )

    history = await session_service.get_recent_history(username)
    dashboard = await fetch_dashboard(username)

    logger.info(
        "chat user=%s history_turns=%d dashboard_rate=%s",
        username,
        len(history),
        dashboard.get("completedRate", "N/A"),
    )

    answer = await run_agent(payload.question, history, dashboard)
    await session_service.append_turn(username, payload.question, answer)

    elapsed = time.perf_counter() - t_start
    chat_duration_seconds.observe(elapsed)

    return {
        "question": payload.question,
        "answer": answer,
        "confidence": 0.91,
        "historyTurns": len(history),
        "user": username,
    }
