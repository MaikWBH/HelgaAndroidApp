"""KI-Fehlerabbildung (Phase 4, S8): Anbieterfehler werden zu 502 bzw. zu einem abgebrochenen Stream.

Kein Netz: der httpx-Client wird durch eine Attrappe ersetzt.
"""
import httpx
import pytest

from app import ai_client, settings

REQ = httpx.Request("POST", "https://ai.example/v1/x")
SECRET = "geheimer-schluessel"


def _http_error(status: int) -> httpx.HTTPStatusError:
    return httpx.HTTPStatusError("fehler", request=REQ, response=httpx.Response(status, request=REQ))


class _Resp:
    """Antwort auf post: Status (>= 400 wirft) und JSON-Body."""

    def __init__(self, status: int = 200, body=None):
        self.status = status
        self.body = body

    def raise_for_status(self):
        if self.status >= 400:
            raise _http_error(self.status)

    def json(self):
        return self.body


class _StreamBody:
    """Antwort auf stream: wirft bei Status >= 400, sonst ein Chunk und optional einen Abbruch."""

    def __init__(self, status: int = 200, fail_after_first: bool = False):
        self.status = status
        self.fail_after_first = fail_after_first

    def raise_for_status(self):
        if self.status >= 400:
            raise _http_error(self.status)

    async def aiter_lines(self):
        yield 'data: {"choices":[{"delta":{"content":"Hallo"}}]}'
        if self.fail_after_first:
            raise httpx.ReadError("abgebrochen", request=REQ)
        yield "data: [DONE]"


class _StreamCtx:
    def __init__(self, behaviour):
        self.behaviour = behaviour

    async def __aenter__(self):
        if isinstance(self.behaviour, Exception):
            raise self.behaviour
        return self.behaviour

    async def __aexit__(self, *args):
        return False


class _FakeClient:
    post_result = None    # _Resp oder Exception
    stream_result = None  # _StreamBody oder Exception

    def __init__(self, *args, **kwargs):
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    async def post(self, url, headers=None, json=None):
        if isinstance(self.post_result, Exception):
            raise self.post_result
        return self.post_result

    def stream(self, method, url, headers=None, json=None):
        return _StreamCtx(self.stream_result)


@pytest.fixture
def upstream(monkeypatch):
    fake = type("FakeClient", (_FakeClient,), {})
    monkeypatch.setattr(ai_client.httpx, "AsyncClient", fake)
    monkeypatch.setattr(settings, "AI_PROVIDER", "openai")
    monkeypatch.setattr(settings, "AI_API_KEY", SECRET)
    return fake


def test_classify_status_error_is_502_without_secret(client, upstream, caplog):
    upstream.stream_result = _StreamBody(status=500)
    with caplog.at_level("WARNING"):
        r = client.post("/api/ai/classify", json={"name": "Suppe"})
    assert r.status_code == 502
    assert r.json() == {"detail": "KI-Dienst hat nicht geantwortet"}
    assert SECRET not in r.text
    assert "Status 500" in caplog.text


def test_classify_unreachable_is_502(client, upstream):
    upstream.stream_result = httpx.ConnectError("keine Verbindung", request=REQ)
    r = client.post("/api/ai/classify", json={"name": "Suppe"})
    assert r.status_code == 502
    assert r.json()["detail"] == "KI-Dienst hat nicht geantwortet"


def test_generate_error_before_first_chunk_is_502(client, upstream):
    upstream.stream_result = _StreamBody(status=500)
    r = client.post("/api/ai/generate", json={"prompt": "Pasta"})
    assert r.status_code == 502


def test_generate_error_after_first_chunk_ends_without_done(client, upstream, caplog):
    upstream.stream_result = _StreamBody(fail_after_first=True)
    with caplog.at_level("WARNING"):
        r = client.post("/api/ai/generate", json={"prompt": "Pasta"})
    assert r.status_code == 200
    assert "data: Hallo" in r.text
    assert "[DONE]" not in r.text
    assert "KI-Stream abgebrochen" in caplog.text


def test_parse_receipt_status_error_is_502(client, upstream):
    upstream.post_result = _Resp(status=500)
    r = client.post("/api/ai/parse-receipt", json={"image_base64": "QUJD"})
    assert r.status_code == 502


def test_unexpected_response_shape_is_502(client, upstream):
    upstream.post_result = _Resp(status=200, body={})
    r = client.post("/api/ai/parse-receipt", json={"image_base64": "QUJD"})
    assert r.status_code == 502
    assert r.json()["detail"] == "KI-Dienst hat nicht geantwortet"


def test_categorize_swallows_upstream_error(client, upstream):
    upstream.post_result = _Resp(status=500)
    r = client.post("/api/ai/categorize-items", json={"names": ["Milch"]})
    assert r.status_code == 200


def test_generate_success_streams_chunks_and_done(client, upstream):
    upstream.stream_result = _StreamBody()
    r = client.post("/api/ai/generate", json={"prompt": "Pasta"})
    assert r.status_code == 200
    assert r.text == "data: Hallo\n\ndata: [DONE]\n\n"
