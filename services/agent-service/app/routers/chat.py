import time
import logging
import json

from fastapi import APIRouter, Header, HTTPException, status
from pydantic import BaseModel, field_validator
from fastapi.responses import StreamingResponse

from app.metrics import (
    chat_requests_total,
    chat_rate_limited_total,
    chat_duration_seconds,
)
from app.services.session import session_service
from app.services.dashboard import fetch_dashboard
from app.services.agent_impl import AgentServiceImpl
from app.services.iagent import IAgentService

router = APIRouter()
logger = logging.getLogger(__name__)

# 单例 agent 服务
_agent_service: IAgentService = AgentServiceImpl()


class ChatRequest(BaseModel):
    question: str

    @field_validator("question")
    @classmethod
    def question_not_blank(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("问题不能为空")
        return v.strip()


async def _prepare_chat_context(x_user_name: str | None):
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
    return username, history, dashboard


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
    username, history, dashboard = await _prepare_chat_context(x_user_name)

    answer = await _agent_service.run_agent(payload.question, history, dashboard)
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


@router.post("/api/agent/chat/stream", summary="多轮对话问答（SSE 流式）")
async def chat_stream(
    payload: ChatRequest,
    x_user_name: str | None = Header(default=None, alias="X-User-Name"),
):
    t_start = time.perf_counter()
    chat_requests_total.inc()
    username, history, dashboard = await _prepare_chat_context(x_user_name)

    async def event_gen():
        chunks: list[str] = []
        try:
            async for chunk in _agent_service.run_agent_stream(payload.question, history, dashboard):
                text = str(chunk)
                if not text:
                    continue
                chunks.append(text)
                yield f"data: {json.dumps({'type': 'chunk', 'content': text}, ensure_ascii=False)}\n\n"

            full_answer = "".join(chunks).strip()
            await session_service.append_turn(username, payload.question, full_answer)

            elapsed = time.perf_counter() - t_start
            chat_duration_seconds.observe(elapsed)

            final_payload = {
                "type": "final",
                "question": payload.question,
                "answer": full_answer,
                "confidence": 0.91,
                "historyTurns": len(history),
                "user": username,
            }
            yield f"data: {json.dumps(final_payload, ensure_ascii=False)}\n\n"
            yield "data: [DONE]\n\n"
        except Exception as exc:
            err_payload = {"type": "error", "message": str(exc)}
            yield f"data: {json.dumps(err_payload, ensure_ascii=False)}\n\n"

    return StreamingResponse(event_gen(), media_type="text/event-stream")
