"""SSRF-Schutz im Rezept-Import (Phase 4, S9). Kein Netz: DNS und HTTP-Client sind Attrappen.

`recipe_scrapers` ist in der Testumgebung nicht installiert; die Abrufs-Logik wird daher über
`ai._fetch_html` geprüft (dort liegt die Abbildung auf 422), nicht über den Endpunkt.
"""
import ipaddress

import httpx
import pytest
from fastapi import HTTPException

from app import ai, settings, url_guard

from conftest import run

REQ = httpx.Request("GET", "https://site.example/")


@pytest.fixture
def dns(monkeypatch):
    table = {
        "localhost": ["127.0.0.1"],
        "example.com": ["93.184.216.34"],
        "mixed.example": ["93.184.216.34", "10.0.0.5"],
    }

    def fake(host):
        if host in table:
            return table[host]
        try:
            ipaddress.ip_address(host)
            return [host]  # IP-Literal bleibt unverändert
        except ValueError:
            raise OSError("unbekannter Host")

    monkeypatch.setattr(url_guard, "_resolve", fake)


@pytest.mark.parametrize("url", [
    "http://127.0.0.1/",
    "http://[::1]/",
    "http://192.168.178.1/",
    "http://10.0.0.5/",
    "http://169.254.169.254/",
    "http://100.64.0.1/",
    "http://100.127.255.254/",
    "http://localhost/",
    "http://mixed.example/",
])
def test_blocked_addresses(dns, url):
    with pytest.raises(ValueError, match="Diese Adresse ist nicht erlaubt"):
        run(url_guard.ensure_public_http_url(url))


def test_blocked_scheme(dns):
    with pytest.raises(ValueError, match="Nur http- und https-Adressen"):
        run(url_guard.ensure_public_http_url("ftp://example.com/"))


def test_public_address_allowed(dns):
    run(url_guard.ensure_public_http_url("http://93.184.216.34/"))
    run(url_guard.ensure_public_http_url("https://example.com/rezept"))


class _Resp:
    def __init__(self, status, headers=None, body=b""):
        self.status_code = status
        self.headers = httpx.Headers(headers or {})
        self.encoding = "utf-8"
        self._body = body

    @property
    def is_redirect(self):
        return self.status_code in (301, 302, 303, 307, 308) and "location" in self.headers

    def raise_for_status(self):
        if self.status_code >= 400:
            raise httpx.HTTPStatusError("fehler", request=REQ,
                                        response=httpx.Response(self.status_code, request=REQ))

    async def aiter_bytes(self):
        for i in range(0, len(self._body), 7):
            yield self._body[i:i + 7]


class _Ctx:
    def __init__(self, resp):
        self.resp = resp

    async def __aenter__(self):
        return self.resp

    async def __aexit__(self, *args):
        return False


def _client_with(monkeypatch, pages):
    class FakeClient:
        def __init__(self, *args, **kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

        def stream(self, method, url, headers=None):
            return _Ctx(pages.get(url, _Resp(404)))

    monkeypatch.setattr(ai.httpx, "AsyncClient", FakeClient)


def _fetch_error(url):
    with pytest.raises(HTTPException) as exc:
        run(ai._fetch_html(url))
    assert exc.value.status_code == 422
    return exc.value.detail


def test_fetch_public_page(dns, monkeypatch):
    _client_with(monkeypatch, {"http://93.184.216.34/": _Resp(200, body=b"<html>ok</html>")})
    assert run(ai._fetch_html("http://93.184.216.34/")) == "<html>ok</html>"


def test_fetch_blocked_by_host(dns, monkeypatch):
    _client_with(monkeypatch, {})
    assert _fetch_error("http://192.168.178.1/") == "Diese Adresse ist nicht erlaubt"


def test_redirect_to_private_address_is_rejected(dns, monkeypatch):
    _client_with(monkeypatch, {
        "http://93.184.216.34/": _Resp(302, headers={"Location": "http://192.168.1.1/"}),
    })
    assert _fetch_error("http://93.184.216.34/") == "Diese Adresse ist nicht erlaubt"


def test_five_redirects_allowed_six_rejected(dns, monkeypatch):
    base = "http://93.184.216.34/r{}"
    pages = {base.format(i): _Resp(302, headers={"Location": base.format(i + 1)}) for i in range(5)}
    pages[base.format(5)] = _Resp(200, body=b"<html>ziel</html>")
    _client_with(monkeypatch, pages)
    assert run(ai._fetch_html(base.format(0))) == "<html>ziel</html>"

    pages = {base.format(i): _Resp(302, headers={"Location": base.format(i + 1)}) for i in range(6)}
    _client_with(monkeypatch, pages)
    assert _fetch_error(base.format(0)) == "Zu viele Weiterleitungen"


def test_page_too_large(dns, monkeypatch):
    monkeypatch.setattr(settings, "MAX_IMPORT_PAGE_BYTES", 10)
    _client_with(monkeypatch, {"http://93.184.216.34/": _Resp(200, body=b"x" * 100)})
    assert _fetch_error("http://93.184.216.34/") == "Seite ist zu groß"


def test_default_page_limit_is_five_mib():
    assert settings.MAX_IMPORT_PAGE_BYTES == 5_242_880
