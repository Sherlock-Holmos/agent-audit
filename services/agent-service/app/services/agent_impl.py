"""Agent 服务实现"""

import logging
from typing import Optional

from langchain_core.messages import AIMessage, HumanMessage
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.runnables import Runnable

from app.config import settings
from app.services.iagent import IAgentService

logger = logging.getLogger(__name__)

# ── Prompt ────────────────────────────────────────────────────────────────
_SYSTEM_TEMPLATE = """\
你是一个企业审计整改智能助手，专注于帮助审计人员分析问题、给出整改建议。

【当前系统数据】
- 整改完成率：{completed_rate}%
- 待处理疑似空值：{overdue_count}

{rag_context}\
请结合以上数据和对话历史，给出专业、简洁的整改建议。"""


# ── LLM 工厂 ──────────────────────────────────────────────────────────────
def _build_llm():
    provider = settings.llm_provider.lower()

    if provider == "azure":
        from langchain_openai import AzureChatOpenAI

        logger.info("LLM provider: Azure OpenAI (deployment=%s)", settings.azure_openai_deployment)
        return AzureChatOpenAI(
            azure_deployment=settings.azure_openai_deployment,
            azure_endpoint=settings.azure_openai_endpoint,
            api_key=settings.azure_openai_api_key,
            api_version=settings.azure_openai_api_version,
            temperature=0.3,
        )

    if provider == "openai":
        from langchain_openai import ChatOpenAI

        logger.info("LLM provider: OpenAI (model=%s)", settings.openai_model)
        return ChatOpenAI(
            model=settings.openai_model,
            api_key=settings.openai_api_key,
            temperature=0.3,
        )

    # mock 模式：不调用任何外部 API，适合本地开发
    logger.warning(
        "LLM_PROVIDER=%s — running in mock mode. Set OPENAI_API_KEY or Azure vars for real LLM.",
        settings.llm_provider,
    )
    return None


# ── RAG Retriever 工厂（VECTOR_STORE_TYPE != none 时启用）─────────────────
def _build_retriever():
    vtype = settings.vector_store_type.lower()

    if vtype == "chroma":
        try:
            from langchain_chroma import Chroma
            from langchain_openai import OpenAIEmbeddings

            logger.info("RAG: connecting to Chroma at %s:%d", settings.chroma_host, settings.chroma_port)
            vectorstore = Chroma(
                collection_name=settings.vector_collection,
                embedding_function=OpenAIEmbeddings(api_key=settings.openai_api_key),
                client_settings={"host": settings.chroma_host, "port": settings.chroma_port},
            )
            return vectorstore.as_retriever(search_kwargs={"k": settings.vector_top_k})
        except Exception as exc:
            logger.error("Failed to build Chroma retriever: %s", exc)
            return None

    if vtype == "pgvector":
        try:
            from langchain_community.vectorstores import PGVector
            from langchain_openai import OpenAIEmbeddings

            logger.info("RAG: connecting to pgvector DSN=%s", settings.pgvector_dsn[:30])
            vectorstore = PGVector(
                collection_name=settings.vector_collection,
                connection_string=settings.pgvector_dsn,
                embedding_function=OpenAIEmbeddings(api_key=settings.openai_api_key),
            )
            return vectorstore.as_retriever(search_kwargs={"k": settings.vector_top_k})
        except Exception as exc:
            logger.error("Failed to build pgvector retriever: %s", exc)
            return None

    return None  # none — RAG 未启用


# ── Agent 实现 ────────────────────────────────────────────────────────────
class AgentServiceImpl(IAgentService):
    """Agent 服务实现"""

    async def run_agent(
        self,
        question: str,
        history: list[dict],
        dashboard: dict,
    ) -> str:
        """
        执行一次对话推理。

        Args:
            question: 本轮用户问题
            history:  Redis 中的历史记录列表，每条含 "q" / "a"/ "ts" 字段
            dashboard: data-service 返回的看板数据

        Returns:
            LLM 生成的回答字符串
        """
        llm = _build_llm()

        # Mock 模式：直接返回占位回答，不走 LangChain 链路
        if llm is None:
            return (
                f"【Mock 模式】当前未配置 LLM_PROVIDER，已返回占位回答。\n"
                f"整改完成率：{dashboard.get('completedRate', 'N/A')}%，"
                f"待处理疑似空值：{dashboard.get('overdueCount', 'N/A')}。\n"
                f"问题：{question}\n"
                "请设置 OPENAI_API_KEY（或 Azure OpenAI 环境变量）切换至真实 LLM。"
            )

        # ── RAG 上下文（可选）─────────────────────────────────────────────────
        rag_context = ""
        retriever = _build_retriever()
        if retriever is not None:
            try:
                docs = await retriever.ainvoke(question)
                if docs:
                    snippets = "\n---\n".join(d.page_content for d in docs)
                    rag_context = f"【知识库参考】\n{snippets}\n\n"
                    logger.info("RAG retrieved %d docs for question", len(docs))
            except Exception as exc:
                logger.warning("RAG retrieval failed, skipping: %s", exc)

        # ── 历史消息转为 LangChain Message 列表 ─────────────────────────────
        lc_history: list = []
        for turn in history:
            lc_history.append(HumanMessage(content=turn.get("q", "")))
            lc_history.append(AIMessage(content=turn.get("a", "")))

        # ── 构建 LCEL 链 ──────────────────────────────────────────────────────
        prompt = ChatPromptTemplate.from_messages(
            [
                ("system", _SYSTEM_TEMPLATE),
                MessagesPlaceholder(variable_name="history"),
                ("human", "{question}"),
            ]
        )
        chain: Runnable = prompt | llm | StrOutputParser()

        result: str = await chain.ainvoke(
            {
                "completed_rate": dashboard.get("completedRate", "N/A"),
                "overdue_count": dashboard.get("overdueCount", "N/A"),
                "rag_context": rag_context,
                "history": lc_history,
                "question": question,
            }
        )
        return result
