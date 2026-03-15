import json
import logging
from datetime import datetime, timezone
from typing import Optional

import redis.asyncio as aioredis

from app.config import settings

logger = logging.getLogger(__name__)

_RATE_LIMIT_PREFIX = "agent:rl:"
_SESSION_PREFIX = "agent:session:"


def _safe_user(username: str) -> str:
    if not username or not username.strip():
        return "anonymous"
    return username.strip().lower()


class SessionService:
    """Redis 会话服务：限流配额 + 多轮对话历史。"""

    def __init__(self) -> None:
        self._redis: Optional[aioredis.Redis] = None

    async def _get_redis(self) -> aioredis.Redis:
        if self._redis is None:
            self._redis = aioredis.Redis(
                host=settings.redis_host,
                port=settings.redis_port,
                socket_timeout=settings.redis_timeout,
                decode_responses=True,
            )
        return self._redis

    async def try_acquire_quota(self, username: str) -> bool:
        """滑动窗口限流（1 分钟内最多 rate_limit_per_minute 次）。Redis 不可用时降级放行。"""
        key = _RATE_LIMIT_PREFIX + _safe_user(username)
        try:
            r = await self._get_redis()
            count = await r.incr(key)
            if count == 1:
                await r.expire(key, 60)
            return count <= settings.agent_rate_limit_per_minute
        except Exception:
            logger.warning("Redis unavailable during rate-limit check, degrading to allow")
            return True

    async def get_recent_history(self, username: str) -> list[dict]:
        """获取最近 N 轮对话历史（已按 max_session_turns 截断）。"""
        key = _SESSION_PREFIX + _safe_user(username)
        try:
            r = await self._get_redis()
            raw = await r.get(key)
            if not raw:
                return []
            return json.loads(raw)
        except Exception:
            logger.warning("Failed to read session history for user=%s", _safe_user(username))
            return []

    async def append_turn(self, username: str, question: str, answer: str) -> None:
        """追加一轮对话并写回 Redis，超出上限时滚动丢弃最旧记录。"""
        turns = await self.get_recent_history(username)
        turns.append(
            {
                "q": question,
                "a": answer,
                "ts": datetime.now(timezone.utc).isoformat(),
            }
        )
        while len(turns) > settings.agent_max_session_turns:
            turns.pop(0)

        key = _SESSION_PREFIX + _safe_user(username)
        try:
            r = await self._get_redis()
            await r.setex(
                key,
                settings.agent_session_ttl_minutes * 60,
                json.dumps(turns, ensure_ascii=False),
            )
        except Exception:
            logger.warning("Failed to write session history for user=%s", _safe_user(username))

    async def close(self) -> None:
        if self._redis is not None:
            await self._redis.aclose()
            self._redis = None


# 单例，在 main.py lifespan 中关闭
session_service = SessionService()
