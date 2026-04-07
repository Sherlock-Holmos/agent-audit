import time
import logging
import json
import asyncio

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
from app.config import settings

router = APIRouter()
logger = logging.getLogger(__name__)

# 单例 agent 服务
_agent_service: IAgentService = AgentServiceImpl()


class ChatRequest(BaseModel):
    question: str
    llmConfig: dict | None = None

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


def _build_stream_error(exc: Exception) -> dict:
    if isinstance(exc, HTTPException):
        if exc.status_code == status.HTTP_429_TOO_MANY_REQUESTS:
            return {
                "type": "error",
                "code": "rate_limit",
                "message": str(exc.detail or "请求过于频繁，请稍后再试"),
                "retryable": True,
            }
        return {
            "type": "error",
            "code": "http_error",
            "message": str(exc.detail or "请求失败"),
            "retryable": False,
        }

    if isinstance(exc, TimeoutError):
        return {
            "type": "error",
            "code": "stream_timeout",
            "message": str(exc) or "流式回答超时，请缩小问题范围后重试",
            "retryable": True,
        }

    msg = str(exc) if exc else "流式响应异常"
    lower_msg = msg.lower()
    if "openai" in lower_msg or "azure" in lower_msg or "llm" in lower_msg:
        return {
            "type": "error",
            "code": "upstream_error",
            "message": msg,
            "retryable": True,
        }

    return {
        "type": "error",
        "code": "stream_error",
        "message": msg,
        "retryable": False,
    }


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

    answer = await _agent_service.run_agent(payload.question, history, dashboard, payload.llmConfig)
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

    try:
        username, history, dashboard = await _prepare_chat_context(x_user_name)
    except Exception as exc:
        error_payload = _build_stream_error(exc)

        async def precheck_failed_gen():
            yield f"data: {json.dumps(error_payload, ensure_ascii=False)}\n\n"
            yield "data: [DONE]\n\n"

        return StreamingResponse(precheck_failed_gen(), media_type="text/event-stream")

    async def event_gen():
        chunks: list[str] = []
        queue: asyncio.Queue[str] = asyncio.Queue()
        producer_done = asyncio.Event()
        producer_error: Exception | None = None

        async def produce_chunks():
            nonlocal producer_error
            try:
                async for chunk in _agent_service.run_agent_stream(payload.question, history, dashboard, payload.llmConfig):
                    text = str(chunk)
                    if text:
                        await queue.put(text)
            except Exception as exc:
                producer_error = exc
            finally:
                producer_done.set()

        producer_task = asyncio.create_task(produce_chunks())
        started_at = time.perf_counter()

        try:
            while True:
                elapsed = time.perf_counter() - started_at
                remaining = float(settings.agent_stream_max_duration_seconds) - elapsed
                if remaining <= 0:
                    raise TimeoutError("流式回答超时，请缩小问题范围后重试")

                wait_timeout = min(float(settings.agent_stream_heartbeat_seconds), remaining)

                try:
                    text = await asyncio.wait_for(queue.get(), timeout=wait_timeout)
                    chunks.append(text)
                    yield f"data: {json.dumps({'type': 'chunk', 'content': text}, ensure_ascii=False)}\n\n"
                except asyncio.TimeoutError:
                    if producer_done.is_set() and queue.empty():
                        if producer_error is not None:
                            raise producer_error
                        break
                    heartbeat_payload = {"type": "heartbeat", "ts": int(time.time())}
                    yield f"data: {json.dumps(heartbeat_payload, ensure_ascii=False)}\n\n"

            full_answer = "".join(chunks).strip()
            if not full_answer:
                raise RuntimeError("模型未返回有效内容，请检查模型配置或稍后重试")
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
            err_payload = _build_stream_error(exc)
            yield f"data: {json.dumps(err_payload, ensure_ascii=False)}\n\n"
            yield "data: [DONE]\n\n"
        finally:
            if not producer_task.done():
                producer_task.cancel()
            try:
                await producer_task
            except asyncio.CancelledError:
                pass

    return StreamingResponse(event_gen(), media_type="text/event-stream")
