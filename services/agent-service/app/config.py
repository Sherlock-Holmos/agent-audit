from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """
    所有配置项通过同名大写环境变量注入，例如：
      redis_host  <->  REDIS_HOST
      openai_api_key  <->  OPENAI_API_KEY
    """

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # ── 运行时 ──────────────────────────────────────────────────
    port: int = 8083

    # ── Redis ────────────────────────────────────────────────────
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_timeout: float = 2.0

    # ── data-service ─────────────────────────────────────────────
    data_base_url: str = "http://localhost:8082"
    agent_dashboard_timeout_ms: int = 2500
    agent_dashboard_retry_times: int = 1

    # ── 会话 / 限流 ───────────────────────────────────────────────
    agent_session_ttl_minutes: int = 360
    agent_max_session_turns: int = 20
    agent_rate_limit_per_minute: int = 30

    # ── LLM 提供商 ───────────────────────────────────────────────
    # 可选值：mock | openai | azure
    llm_provider: str = "mock"

    # OpenAI
    openai_api_key: str = ""
    openai_model: str = "gpt-4o-mini"

    # Azure OpenAI（llm_provider=azure 时生效）
    azure_openai_api_key: str = ""
    azure_openai_endpoint: str = ""
    azure_openai_deployment: str = "gpt-4o"
    azure_openai_api_version: str = "2024-08-01-preview"

    # ── RAG / 向量数据库（预留，vector_store_type=none 时不启用）───
    # 可选值：none | chroma | pgvector
    vector_store_type: str = "none"
    chroma_host: str = "localhost"
    chroma_port: int = 8000
    # pgvector DSN 示例：postgresql+psycopg2://user:pass@host:5432/db
    pgvector_dsn: str = ""
    vector_collection: str = "audit_knowledge"
    vector_top_k: int = 4


settings = Settings()
