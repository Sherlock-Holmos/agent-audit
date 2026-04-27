import unittest

from app.services.agent_impl import _finalize_answer


class AgentFinalizeTests(unittest.TestCase):
    def test_finalize_returns_fallback_when_empty(self):
        result = _finalize_answer("", "通用问题", {"issues": [], "tasks": []})
        self.assertIn("未返回有效内容", result)

    def test_finalize_returns_direct_reply_when_question_matches_pattern(self):
        result = _finalize_answer("任意模型输出", "现在有多少任务", {"tasks": [{"status": "已完成"}, {"status": "执行中"}]})
        self.assertIn("当前共有 2 个任务", result)


if __name__ == "__main__":
    unittest.main()
