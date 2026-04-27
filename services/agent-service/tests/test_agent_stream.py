import unittest

from app.services.agent_impl import AgentServiceImpl


class _FakeChain:
    async def astream(self, _inputs):
        for chunk in ["A", "B", "C"]:
            yield chunk


class AgentStreamTests(unittest.IsolatedAsyncioTestCase):
    async def test_run_agent_stream_yields_incremental_chunks(self):
        service = AgentServiceImpl()

        async def fake_build_context(question, history, dashboard, llm_config=None):
            return object(), _FakeChain(), {"question": question}

        service._build_context = fake_build_context  # type: ignore[method-assign]

        chunks = []
        async for item in service.run_agent_stream("测试问题", [], {"completedRate": 50, "overdueCount": 1}, None):
            chunks.append(item)

        self.assertEqual(chunks, ["A", "B", "C"])


if __name__ == "__main__":
    unittest.main()
