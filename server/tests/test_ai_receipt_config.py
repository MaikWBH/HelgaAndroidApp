import asyncio

from app import ai

from conftest import run


def test_receipt_model_defaults(monkeypatch):
    monkeypatch.setattr(ai, "AI_RECEIPT_MODEL", "")
    monkeypatch.setattr(ai, "AI_VISION_MODEL", "claude-sonnet-4-6")
    monkeypatch.setattr(ai, "AI_PROVIDER", "anthropic")
    assert ai._receipt_model() == "claude-haiku-5-5"
    monkeypatch.setattr(ai, "AI_PROVIDER", "openai")
    assert ai._receipt_model() == "gpt-4o-mini"


def test_receipt_model_env_override(monkeypatch):
    monkeypatch.setattr(ai, "AI_PROVIDER", "anthropic")
    monkeypatch.setattr(ai, "AI_RECEIPT_MODEL", "claude-sonnet-4-6")
    assert ai._receipt_model() == "claude-sonnet-4-6"


def test_new_models_use_effort_and_no_sampling(monkeypatch):
    monkeypatch.setattr(ai, "AI_RECEIPT_EFFORT", "medium")
    payload = ai._receipt_payload("claude-haiku-5-5", "sys", [{"type": "text", "text": "x"}])
    assert payload["output_config"] == {"effort": "medium"}
    assert "temperature" not in payload
    assert "thinking" not in payload
    assert payload["max_tokens"] == 16000


def test_older_models_use_temperature(monkeypatch):
    payload = ai._receipt_payload("claude-sonnet-4-6", "sys", [{"type": "text", "text": "x"}])
    assert payload["temperature"] == 0
    assert "output_config" not in payload
    assert payload["max_tokens"] == 16000


def test_effort_value_is_configurable(monkeypatch):
    monkeypatch.setattr(ai, "AI_RECEIPT_EFFORT", "high")
    payload = ai._receipt_payload("claude-haiku-5-5", "sys", [])
    assert payload["output_config"] == {"effort": "high"}


class _FakeResponse:
    def __init__(self, body):
        self._body = body

    def raise_for_status(self):
        pass

    def json(self):
        return self._body


class _FakeClient:
    last_payload = None

    def __init__(self, *a, **k):
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, *a):
        return False

    async def post(self, url, headers=None, json=None):
        _FakeClient.last_payload = json
        return _FakeResponse({"content": [
            {"type": "thinking", "thinking": "..."},
            {"type": "text", "text": "A"},
            {"type": "text", "text": "B"},
        ]})


def test_receipt_once_joins_text_blocks_and_sends_image(monkeypatch):
    monkeypatch.setattr(ai, "AI_PROVIDER", "anthropic")
    monkeypatch.setattr(ai, "AI_RECEIPT_MODEL", "")
    monkeypatch.setattr(ai.httpx, "AsyncClient", _FakeClient)
    out = run(ai._receipt_once("sys", "frage", image_b64="QUJD"))
    assert out == "AB"
    sent = _FakeClient.last_payload
    assert sent["model"] == "claude-haiku-5-5"
    assert [b["type"] for b in sent["messages"][0]["content"]] == ["image", "text"]
    run(ai._receipt_once("sys", "frage"))
    assert [b["type"] for b in _FakeClient.last_payload["messages"][0]["content"]] == ["text"]
