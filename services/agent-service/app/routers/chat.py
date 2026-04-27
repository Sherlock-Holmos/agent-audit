"""Chat Router - LangChain Agent based implementation

提供多轮对话问答接口，支持显式推理步骤和流式输出。
"""

import time
import logging
import json
import asyncio
import ipaddress
from urllib.parse import urlparse
from typing import AsyncIterator

from fastapi import APIRouter, Header, HTTPException, status
from pydantic import BaseModel, field_validator
from fastapi.responses import StreamingResponse

from app.services.session import session_service
from app.services.dashboard import dashboard_client
from app.services.agent_service import agent_service
from app.config import settings

router = APIRouter()
logger = logging.getLogger(__name__)

_ALLOWED_LLM_CONFIG_KEYS = {"provider", "model", "apiKey", "baseUrl", "apiVersion"}
_ALLOWED_PROVIDER_VALUES = {"mock", "openai", "azure", "custom"}


def _is_private_host(hostname: str) -> bool:
    host = (hostname or "").strip().lower()
    if not host:
        return False
    if host in {"localhost", "127.0.0.1", "::1", "0.0.0.0"} or host.endswith(".local"):
        return True
    try:
        ip_obj = ipaddress.ip_address(host)
        return (
            ip_obj.is_private
            or ip_obj.is_loopback
            or ip_obj.is_link_local
            or ip_obj.is_reserved
            or ip_obj.is_multicast
        )
    except ValueError:
        return False


def _sanitize_llm_config(conf: dict | None) -> dict | None:
    if conf is None:
        return None
    if not isinstance(conf, dict):
        raise ValueError("llmConfig 必须为对象")

    unknown_keys = [key for key in conf.keys() if key not in _ALLOWED_LLM_CONFIG_KEYS]
    if unknown_keys:
        raise ValueError(f"llmConfig 包含不支持的字段: {', '.join(sorted(unknown_keys))}")

    sanitized: dict[str, str] = {}
    for key in _ALLOWED_LLM_CONFIG_KEYS:
        val = conf.get(key)
        if val is None:
            continue
        if not isinstance(val, str):
            raise ValueError(f"llmConfig.{key} 必须为字符串")
        text = val.strip()
        if not text:
            continue
        if len(text) > 512:
            raise ValueError(f"llmConfig.{key} 长度超限")
        sanitized[key] = text

    provider = sanitized.get("provider")
    if provider:
        provider = provider.lower()
        if provider not in _ALLOWED_PROVIDER_VALUES:
            raise ValueError("llmConfig.provider 仅支持 mock/openai/azure/custom")
        sanitized["provider"] = provider

    base_url = sanitized.get("baseUrl")
    if base_url:
        parsed = urlparse(base_url)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise ValueError("llmConfig.baseUrl 必须是合法的 http/https 地址")
        if not settings.agent_allow_private_base_url and _is_private_host(parsed.hostname):
            raise ValueError("llmConfig.baseUrl 不允许使用内网或本地地址")

    return sanitized or None


class ChatRequest(BaseModel):
    question: str
    llmConfig: dict | None = None

    @field_validator("question")
    @classmethod
    def question_not_blank(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("问题不能为空")
        return v.strip()

    @field_validator("llmConfig")
    @classmethod
    def validate_llm_config(cls, value: dict | None):
        return _sanitize_llm_config(value)


async def _prepare_chat_context(x_user_name: str | None):
    """准备对话上下文"""
    username = (x_user_name or "").strip() or "anonymous"

    # 限流检查
    if not await session_service.try_acquire_quota(username):
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="请求过于频繁，请稍后再试",
            headers={"Retry-After": "60"},
        )

    history = await session_service.get_recent_history(username)
    dashboard = await dashboard_client.fetch_dashboard(username)
    if isinstance(dashboard, dict):
        dashboard["currentUser"] = username

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
    if "account balance is insufficient" in lower_msg or "code\': 30001" in lower_msg or '"code": 30001' in lower_msg or "code: 30001" in lower_msg:
        return {
            "type": "error",
            "code": "insufficient_balance",
            "message": msg,
            "retryable": False,
        }

    if "model does not exist" in lower_msg or "code\': 20012" in lower_msg or '"code": 20012' in lower_msg or "code: 20012" in lower_msg:
        return {
            "type": "error",
            "code": "model_not_found",
            "message": msg,
            "retryable": False,
        }

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
    username, history, dashboard = await _prepare_chat_context(x_user_name)

    answer = await agent_service.run_agent(payload.question, history, dashboard, payload.llmConfig)
    await session_service.append_turn(username, payload.question, answer)

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
    try:
        username, history, dashboard = await _prepare_chat_context(x_user_name)
    except Exception as exc:
        error_payload = _build_stream_error(exc)

        async def precheck_failed_gen():
            yield f"data: {json.dumps(error_payload, ensure_ascii=False)}\n\n"
            yield "data: [DONE]\n\n"

        return StreamingResponse(
            precheck_failed_gen(),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache, no-transform",
                "Connection": "keep-alive",
                "X-Accel-Buffering": "no",
            },
        )

    async def event_gen():
        started_at = time.perf_counter()
        chunks: list[str] = []
        queue: asyncio.Queue[str] = asyncio.Queue()
        producer_done = asyncio.Event()
        producer_error: Exception | None = None

        async def produce_chunks():
            nonlocal producer_error
            try:
                async for chunk in agent_service.run_agent_stream(payload.question, history, dashboard, payload.llmConfig):
                    text = str(chunk)
                    if text:
                        await queue.put(text)
            except Exception as exc:
                producer_error = exc
            finally:
                producer_done.set()

        producer_task = asyncio.create_task(produce_chunks())
        try:
            while True:
                if producer_done.is_set() and queue.empty():
                    if producer_error is not None:
                        raise producer_error
                    break

                elapsed = time.perf_counter() - started_at
                remaining = float(settings.agent_stream_max_duration_seconds) - elapsed
                if remaining <= 0:
                    raise TimeoutError("流式回答超时，请缩小问题范围后重试")

                wait_timeout = min(float(settings.agent_stream_heartbeat_seconds), remaining)
                queue_get_task = asyncio.create_task(queue.get())
                producer_done_task = asyncio.create_task(producer_done.wait())

                try:
                    done, pending = await asyncio.wait(
                        {queue_get_task, producer_done_task},
                        timeout=wait_timeout,
                        return_when=asyncio.FIRST_COMPLETED,
                    )

                    if queue_get_task in done:
                        text = queue_get_task.result()
                        chunks.append(text)
                        yield f"data: {json.dumps({'type': 'chunk', 'content': text}, ensure_ascii=False)}\n\n"
                    elif producer_done_task in done:
                        if producer_error is not None:
                            raise producer_error
                        if queue.empty():
                            break
                    else:
                        heartbeat_payload = {"type": "heartbeat", "ts": int(time.time())}
                        yield f"data: {json.dumps(heartbeat_payload, ensure_ascii=False)}\n\n"
                finally:
                    for task in (queue_get_task, producer_done_task):
                        if not task.done():
                            task.cancel()
                    for task in (queue_get_task, producer_done_task):
                        try:
                            await task
                        except asyncio.CancelledError:
                            pass

            raw_answer = "".join(chunks).strip()
            if not raw_answer:
                raise RuntimeError("模型未返回有效内容，请检查模型配置或稍后重试")
            full_answer = raw_answer  # Agent已处理答案格式化
            await session_service.append_turn(username, payload.question, full_answer)

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

    return StreamingResponse(
        event_gen(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache, no-transform",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
