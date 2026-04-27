import unittest

from app.routers.chat import _sanitize_llm_config


class ChatValidationTests(unittest.TestCase):
    def test_sanitize_llm_config_allows_known_fields(self):
        payload = {
            "provider": "OPENAI",
            "model": "gpt-4o-mini",
            "apiKey": "sk-test",
            "baseUrl": "https://api.example.com/v1",
            "apiVersion": "2024-08-01-preview",
        }
        result = _sanitize_llm_config(payload)
        self.assertEqual(result["provider"], "openai")
        self.assertEqual(result["model"], "gpt-4o-mini")

    def test_sanitize_llm_config_rejects_unknown_field(self):
        with self.assertRaises(ValueError):
            _sanitize_llm_config({"provider": "mock", "unexpected": "x"})

    def test_sanitize_llm_config_rejects_private_base_url(self):
        with self.assertRaises(ValueError):
            _sanitize_llm_config({"provider": "custom", "baseUrl": "http://127.0.0.1:8000/v1"})


if __name__ == "__main__":
    unittest.main()
