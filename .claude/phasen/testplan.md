# Testplan — Handy und Server (Phasen 1–6)

Bündelt alle offenen Geräte-/Server-Prüfungen. Reihenfolge = Reihenfolge der Durchführung. Bezüge: G01–G29 =
Abhakliste in `phase-6-daten/review-bericht.md`; „V…/B…“ = Befund im selben Bericht (hier gezielt provozieren).
Ergebnis je Zeile: ✅ ok · ❌ Fehler (kurz notieren) · ➖ nicht testbar.

## 0 · Vorbereitung (einmalig)
| # | Schritt | Erwartung | Ergebnis |
|---|---------|-----------|----------|
| 0.1 | Server-Backup: `make backup` (DB + Bilder) | „Backup erstellt“ | |
| 0.2 | Prod-`.env`: `API_KEY` gesetzt, Datenordner gehört UID 1000, Homelab-Override überschreibt weder `read_only`/`tmpfs` noch Healthcheck (`docker compose config`) | alles wie beschrieben (G28) | |
| 0.3 | Reihenfolge: **erst Server ausrollen, dann App** auf beiden Handys (G22) | – | |
| 0.4 | Ein Handy mit bestehenden Daten (Bons, Märkte) für den Upgrade-Test behalten, kein Neuinstall | – | |

## 1 · Server (nach Deploy)
| # | Schritt | Erwartung | Ergebnis |
|---|---------|-----------|----------|
| 1.1 | `docker compose ps` | Status `healthy` (G29) | |
| 1.2 | `docker stats` / Logs | zwei Uvicorn-Worker, keine Fehler | |
| 1.3 | `curl /api/health` ohne Key, `curl /api/sync` ohne Key | 200 bzw. 401 | |
| 1.4 | Foto-Upload eines normalen Bildes; danach eines von 5–8 MB | beide ok (read-only-Container, `/tmp` tmpfs) | |
| 1.5 | Rezept-Import per URL: öffentliche Seite | Rezept kommt an (recipe-scrapers 15.13.0) | |
| 1.6 | Rezept-Import mit `http://192.168.x.x` und `http://100.64.x.x` | abgelehnt (422) | |
| 1.7 | Test mit falschem `AI_API_KEY` (kurz in `.env`) → KI-Rezept erzeugen | App zeigt Fehler statt Hängen; Log ohne Schlüssel (502) | |
| 1.8 | Optional: ohne `API_KEY` starten | Log „API_KEY fehlt“, Container `unhealthy` | |
| 1.9 | Testset mit 10–20 echten Bon-Fotos (`make testset DIR=…`), Bericht ansehen (G14) | Haiku-Qualität akzeptabel; sonst `AI_RECEIPT_EFFORT=high` | |
| 1.10 | Vor dem Umschalten: `restore_purged_receipts.py` erst listen, dann `--apply` (G15) | Liste plausibel | |

## 2 · Upgrade und Grundfunktionen (Phase 1, Regression)
| # | Schritt | Erwartung | Ergebnis |
|---|---------|-----------|----------|
| 2.1 | App über bestehende Installation aktualisieren | Start ohne Absturz, Migration 36→38 ok, Bons und Märkte noch da | |
| 2.2 | Rezepte: Liste, Detail, Kochansicht, „Gekocht“, Daumen hoch/runter | wie vorher | |
| 2.3 | Wochenplan: Plan erzeugen (KI + manuell), Rezept tauschen, Einkaufsliste exportieren | wie vorher | |
| 2.4 | KI: Rezept erzeugen und abwandeln (Streaming), Import per URL | wie vorher | |
| 2.5 | Sync: auf Gerät A ändern → Gerät B zeigt es (Rezept, Liste, Gang-Kategorie) (G16) | kommt an | |
| 2.6 | Bon auf A ändern/löschen → B | kommt an | |

## 3 · Design (Phase 2)
| # | Schritt | Erwartung | G |
|---|---------|-----------|---|
| 3.1 | Alle 6 Akzentfarben hell/dunkel: FAB, Leiste, Chips, Button-Text lesbar, kein Lila außer bei Lila | ok | G01 |
| 3.2 | Android 12+: Neuinstallation → Wallpaper-Farben; Auswahl Grün → grün | ok | G02 |
| 3.3 | TalkBack: Farbnamen und „ausgewählt“; Option „System“ fehlt unter Android 12 | ok | G03 |
| 3.4 | Kaltstart im Dark Mode ohne weißes Aufblitzen; Statusleisten-Icons passend | ok | G04 |
| 3.5 | Nichts hinter der Navigationsleiste (FAB „Scannen“, QuickAdd, letzter Eintrag) | ok | G05 |
| 3.6 | Kostenübersicht: Titel und Zurück sichtbar | ok | G06 |
| 3.7 | Start ohne Aufblitzen; Share-URL öffnet Import; Drehen öffnet ihn nicht erneut; Abmelden → Onboarding | ok | G07 |
| 3.8 | Android 14+: Predictive Back, Sheets/Dialoge zuerst | ok | G08 |
| 3.9 | Tastatur verdeckt keine Felder (QuickAdd, Rezeptformular, Einstellungen, Onboarding) | ok | G09 |
| 3.10 | Querformat/Tablet ≥ 600 dp: Rail, keine Rail auf Detail; ≥ 840 dp Inhalt begrenzt | ok | G10 |
| 3.11 | TalkBack Kochansicht: Zeilen als Kontrollkästchen; **Timer-Chips einzeln erreichbar?** (B10-S4) | ok / Befund | G11 |
| 3.12 | Slot-Chip gut zu treffen; Marker-Chips lesbar | ok | G12 |
| 3.13 | Drehen mit offenem Dialog/Sheet und Text (Einkaufsliste, Wochenplan, Einstellungen, Rezeptdetail, Bon-Review); **auch Budget-Dialog** (B7-S3) | erhalten / Befund | G13 |

## 4 · Bons (Phase 3a)
| # | Schritt | Erwartung | G |
|---|---------|-----------|---|
| 4.1 | Scan online | Werte kommen vom Server, Status „Bitte prüfen“, Foto verkleinert | G18 |
| 4.2 | Flugmodus: Bon scannen | sofort vorläufiger Bon; nach Netz KI-Werte; danach keine Datei in `files/receipts/` | G17 |
| 4.3 | **V3 provozieren:** Scan starten, währenddessen Sync auslösen (Pull-to-Refresh) | Foto bleibt erhalten | V3 |
| 4.4 | Review: Uhrzeit, Adresse, Rabatt, Pfand-Chip, Kategorien, „Produkt zuordnen“, TalkBack | ok | G18 |
| 4.5 | **Kopf-Dialog:** ungültigen Betrag/Rabatt eingeben (z. B. „abc“) | Fehler/Sperre? (B8-P1: wird sonst 0,00) | B8-P1 |
| 4.6 | Korrektur bestätigen, gleichen Markt erneut scannen | Korrektur wird übernommen (Lexikon) | G18 |
| 4.7 | Bon-Liste: Monatsgruppen, Chips, „Ältere Bons laden“ (offline: Meldung), Leerzustand; **nur ältere Bons auf Server → Leerzustand zeigt Button?** (B8-P2) | ok / Befund | G19 |
| 4.8 | Bon-Detail: vorläufiger Bon mit lokalem Foto, nicht bearbeitbar; **„Neu auslesen“ bei vorläufigem Bon?** (B8-P5); altes Foto vom Server | ok / Befund | G20 |
| 4.9 | **REPLACE/CASCADE prüfen (B1-Anm):** Bon mit Positionen öffnen → Markt ändern / mit Einkaufsliste verknüpfen → Positionen noch da? | Positionen bleiben | B1-Anm |
| 4.10 | Lexikon: Eintrag nach Korrektur sichtbar, Löschen wirkt beim nächsten Scan (kein Undo: B8-S1) | ok | G21 |

## 5 · Auswertung und Gänge (Phase 3b)
| # | Schritt | Erwartung | G |
|---|---------|-----------|---|
| 5.1 | Kostenübersicht: vier Diagramme hell/dunkel/alle Akzente, Budgetlinie auf Höhe | ok | G23 |
| 5.2 | Monat/Woche und Zeitraum-Chips wechseln; Flugmodus → Diagramme bleiben | ok | G23 |
| 5.3 | Leerer Zustand: kurz „Noch keine Auswertung“-Aufblitzen? (B7-P2) | Befund notieren | B7 |
| 5.4 | Top-Artikel antippen → Preisverlauf; TalkBack liest Diagrammbeschreibung | ok | G23 |
| 5.5 | Artikel > 3 Monate nicht gekauft: „Top-Artikel → Gesamt“ zeigt Preisentwicklung mit Hinweis | ok | G24 |
| 5.6 | Neuer Markt mit Vorlage: 15 Gänge; Kategorie ändern; Ziehen; **Drehen während des Ziehens** (B6-S4); TalkBack | ok / Befund | G25 |
| 5.7 | Einkaufsliste: „Tomaten“, „Milch“, „Spülmittel“, „Bier“ landen im passenden Gang; Handkorrektur wird gelernt | ok | G26 |
| 5.8 | Server-KI: „Geschenkpapier“/„Leinsamen“ → nach Sekunden im Gang, danach sofort; Flugmodus: sofort kein Gang; **Artikel während des KI-Laufs hinzufügen** (V6/B6-P1) | ok / Befund | G27 |

## 6 · Abschluss
Alle ❌ mit Kurzbeschreibung sammeln und mir geben; daraus entstehen die Fix-Aufträge zusammen mit der Liste „An den Nutzer zur Entscheidung“
(`phase-6-daten/review-bericht.md`).
