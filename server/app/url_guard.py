"""SSRF-Schutz für den Rezept-Import (Phase 4, S9).

Der Server lädt Rezeptseiten nur von öffentlichen Adressen. `ensure_public_http_url` wirft ValueError
mit einer deutschen Meldung, wenn Schema, Host oder eine aufgelöste Adresse nicht erlaubt ist.

Bekannte Grenze: DNS-Rebinding zwischen Prüfung und Abruf ist nicht ausgeschlossen, weil der Abruf den
Host erneut auflöst. Vollständig schließen würde heißen, den Abruf an die geprüfte IP-Adresse zu binden.
"""
import asyncio
import ipaddress
import socket
from urllib.parse import urlparse


def _resolve(host: str) -> list[str]:
    """Löst einen Host zu allen IP-Adressen auf (blockierend, daher über asyncio.to_thread).

    Eigene Funktion, damit Tests die Auflösung ersetzen können.
    """
    return [info[4][0] for info in socket.getaddrinfo(host, None)]


def _is_forbidden(ip: ipaddress._BaseAddress) -> bool:
    """Gesperrt ist jede nicht global routbare Adresse, u. a. 100.64.0.0/10 (CGNAT, Tailscale)."""
    if ip.version == 6 and ip.ipv4_mapped is not None:
        ip = ip.ipv4_mapped
    return (ip.is_private or ip.is_loopback or ip.is_link_local
            or ip.is_reserved or ip.is_multicast or ip.is_unspecified
            or not ip.is_global)


async def ensure_public_http_url(url: str) -> None:
    parsed = urlparse(url)
    if parsed.scheme not in ("http", "https"):
        raise ValueError("Nur http- und https-Adressen sind erlaubt")
    host = parsed.hostname
    if not host:
        raise ValueError("Ungültige Adresse")
    try:
        addresses = await asyncio.to_thread(_resolve, host)
    except OSError as exc:
        raise ValueError("Seite nicht erreichbar") from exc
    if not addresses:
        raise ValueError("Seite nicht erreichbar")
    for raw in addresses:
        try:
            ip = ipaddress.ip_address(raw.split("%", 1)[0])
        except ValueError as exc:
            raise ValueError("Diese Adresse ist nicht erlaubt") from exc
        if _is_forbidden(ip):
            raise ValueError("Diese Adresse ist nicht erlaubt")
