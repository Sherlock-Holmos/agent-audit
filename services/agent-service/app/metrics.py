from prometheus_client import Counter, Histogram

chat_requests_total = Counter(
    "audit_agent_chat_requests_total",
    "Total number of chat requests received",
)

chat_rate_limited_total = Counter(
    "audit_agent_chat_rate_limited_total",
    "Total number of chat requests rejected by rate limiter",
)

chat_duration_seconds = Histogram(
    "audit_agent_chat_duration_seconds",
    "End-to-end latency of each chat request",
    buckets=[0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0],
)
