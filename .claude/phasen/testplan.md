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
| 1.1 | `docker compose ps` | Status `healthy` (G29) | ➖ kein Docker auf dem PC; nur im Homelab prüfbar |
| 1.2 | `docker stats` / Logs | zwei Uvicorn-Worker, keine Fehler | ➖ lokal ein Uvicorn-Prozess (venv, Python 3.12.13), Log ohne Fehler; Worker-Zahl nur im Container |
| 1.3 | `curl /api/health` ohne Key, `curl /api/sync` ohne Key | 200 bzw. 401 | ✅ lokal (25.11.): health 200, sync ohne/falscher Key 401, mit Key 200 |
| 1.4 | Foto-Upload eines normalen Bildes; danach eines von 5–8 MB | beide ok (read-only-Container, `/tmp` tmpfs) | ✅ lokal: PNG, 6 MB, knapp 8 MiB → 200; 9 MB → 413; `.txt` → 400; Abruf mit Key 200, ohne 401. read-only/tmpfs ➖ (kein Docker) |
| 1.5 | Rezept-Import per URL: öffentliche Seite | Rezept kommt an (recipe-scrapers 15.13.0) | ✅ lokal: zwei öffentliche Seiten, Titel/Zutaten/Schritte/Bild vorhanden |
| 1.6 | Rezept-Import mit `http://192.168.x.x` und `http://100.64.x.x` | abgelehnt (422) | ✅ lokal: 192.168.1.1, 100.64.0.1, 127.0.0.1, 169.254.169.254, `[::1]` → 422; `file://` → 422 |
| 1.7 | Test mit falschem `AI_API_KEY` (kurz in `.env`) → KI-Rezept erzeugen | App zeigt Fehler statt Hängen; Log ohne Schlüssel (502) | ✅ Server-Seite lokal: leerer und ungültiger Schlüssel → 502 „KI-Dienst hat nicht geantwortet“, Log „Status 401“ ohne Schlüsselwert. App-Anzeige noch offen (2.4) |
| 1.8 | Optional: ohne `API_KEY` starten | Log „API_KEY fehlt“, Container `unhealthy` | ✅ lokal: `RuntimeError: API_KEY fehlt …`, Prozess endet (Exit 3). `unhealthy` ➖ (kein Docker) |
| 1.9 | Testset mit 10–20 echten Bon-Fotos (`make testset DIR=…`), Bericht ansehen (G14) | Haiku-Qualität akzeptabel; sonst `AI_RECEIPT_EFFORT=high` | ➖ KI bewusst aus (Kosten); braucht echte Bon-Fotos |
| 1.10 | Vor dem Umschalten: `restore_purged_receipts.py` erst listen, dann `--apply` (G15) | Liste plausibel | ➖ nur gegen Produktiv-DB sinnvoll (Homelab) |

Zusätzlich (lokal, 25.11.): **B4-P1 bestätigt** – `/docs` und `/openapi.json` sind ohne `X-Api-Key` erreichbar (200). Automatische Tests: Server 180 passed, App 287 grün (0 Fehler).

## 2 · Upgrade und Grundfunktionen (Phase 1, Regression)
| # | Schritt | Erwartung | Ergebnis |
|---|---------|-----------|----------|
| 2.1 | App über bestehende Installation aktualisieren | Start ohne Absturz, Migration 36→38 ok, Bons und Märkte noch da | ➖ 25.11.: Debug-Build hat Suffix `.debug` (`com.helga.android.debug`) und läuft neben der echten App `com.helga.android`; Frischinstallation, keine Migration. Migration nur per Release-Update mit Ihren Daten prüfbar |
| 2.2 | Rezepte: Liste, Detail, Kochansicht, „Gekocht“, Daumen hoch/runter | wie vorher | ✅ Sync-Seite (25.11., Debug-Frischinstall): Rezept „test“ (1 Zutat, 1 Schritt), `recipe_history cooked=1`, `recipe_feedback liked=-1`, `rating=1` kamen am Server an; rating passt zur Formel in `RecipeRepository`. Kein Absturz im Logcat. Bedienung/Optik laut Nutzer: nichts aufgefallen |
| 2.3 | Wochenplan: Plan erzeugen (KI + manuell), Rezept tauschen, Einkaufsliste exportieren | wie vorher | ✅ manueller Teil vom Nutzer durchgeführt (Wochenplan-Tage und Einkaufsliste „test“ am Server), nichts gemeldet; KI-Plan ➖ KI aus |
| 2.4 | KI: Rezept erzeugen und abwandeln (Streaming), Import per URL | wie vorher | Import per URL ✅ (BBC „Easy pancakes“: 6 Zutaten, Schritte, Bild; Detailansicht zeigt alles; Portionen „12“). Beim Import ruft die App danach `/api/ai/classify` auf → 502 (KI aus); Rezept wurde trotzdem gespeichert, kein Absturz. KI erzeugen/abwandeln (Streaming) ➖ KI aus. Der erste Import-Versuch (422) war ein Kopierfehler des Nutzers |
| 2.5 | Sync: auf Gerät A ändern → Gerät B zeigt es (Rezept, Liste, Gang-Kategorie) (G16) | kommt an | ✅ Richtung Handy→Server: Rezepte, Einkaufsliste „test“ mit 2 Einträgen, Historie, Feedback, 14 Wochenplan-Tage am Server. Server→Gerät B ➖ (nur ein Gerät). Beifang: der erste Sync lief doppelt parallel (zwei Verbindungen), danach nur noch einfach |
| 2.6 | Bon auf A ändern/löschen → B | kommt an | |

## 3 · Design (Phase 2)
| # | Schritt | Erwartung | G | Ergebnis |
|---|---------|-----------|---|----------|
| 3.1 | Alle 6 Akzentfarben hell/dunkel: FAB, Leiste, Chips, Button-Text lesbar; Lila nur als Tertiärfarbe (Frühstücks-Slot, Warn-Chips Bon, Diagramme) bei Grün, Blau und Petrol, sonst kein Lila außer beim Akzent Lila (Wortlaut korrigiert in Phase 7) | ok | G01 | ✅/⚠️ 12 Kombinationen (Hell/Dunkel × 6 Farben) per Screenshot: FAB, Leiste, Chips, Button-Text lesbar. Befunde: **F4** (Wochenplan-Symbole kontrastarm), **F5** (Lila als Tertiärfarbe bei Grün/Blau/Petrol; Orange hell: weiß auf #E65100 = 3,8:1) |
| 3.2 | Android 12+: Neuinstallation → Wallpaper-Farben; Auswahl Grün → grün | ok | G02 | ✅ teilweise: bei Neuinstallation ist „System (Wallpaper)“ vorgewählt, Auswahl Grün färbt grün. Ob die Wallpaper-Farbe selbst stimmt, nicht unterscheidbar |
| 3.3 | TalkBack: Farbnamen und „ausgewählt“; Option „System“ fehlt unter Android 12 | ok | G03 | ✅ teilweise: Farbnamen und „ausgewählt“ stehen im Zugänglichkeitsbaum (RadioButton „Grün“ checked); echte TalkBack-Ansage ungeprüft. „System“-Option unter Android 12 ➖ (Gerät: Android 16) |
| 3.4 | Kaltstart im Dark Mode ohne weißes Aufblitzen; Statusleisten-Icons passend | ok | G04 | ✅ Kaltstart im Dark Mode (Aufnahme, 15 Bilder/s): Splash dunkel, kein weißes Aufblitzen, Statusleisten-Icons hell auf dunkel. Beifang **F6** (16-KB-Dialog, nur Debug) |
| 3.5 | Nichts hinter der Navigationsleiste (FAB „Scannen“, QuickAdd, letzter Eintrag) | ok | G05 | ✅ Rezepte (FAB), Einkaufen (QuickAdd endet bei y=2070, Leiste ab 2091), Bons (FAB „Scannen“) frei über der Gestenleiste. „Letzter Eintrag“ nicht geprüft (Listen kurz) |
| 3.6 | Kostenübersicht: Titel und Zurück sichtbar | ok | G06 | ✅ Titel „Kostenübersicht“ und Zurück-Pfeil sichtbar |
| 3.7 | Start ohne Aufblitzen; Share-URL öffnet Import; Drehen öffnet ihn nicht erneut; Abmelden → Onboarding | ok | G07 | ✅ Share-URL (Intent SEND) öffnet „Von URL importieren“ mit URL; Drehen (quer und zurück) öffnet ihn nicht erneut, ein Zurück verlässt ihn; Start ohne Aufblitzen (siehe 3.4). Abmelden → Onboarding ✅ (Dialog „Abmelden?“ bleibt beim Drehen offen; danach erscheint das Onboarding) |
| 3.8 | Android 14+: Predictive Back, Sheets/Dialoge zuerst | ok | G08 | ✅ teilweise: Manifest `enableOnBackInvokedCallback=true`; langsame Kantengeste im Rezeptdetail zeigt Rückpfeil und Vorschau der Liste. Sheets/Dialoge zuerst ungeprüft |
| 3.9 | Tastatur verdeckt keine Felder (QuickAdd, Rezeptformular, Einstellungen, Onboarding) | ok | G09 | ✅ Onboarding, QuickAdd (Feld direkt über Tastatur), Einstellungen (Server-URL, API-Schlüssel, Speichern-Button sichtbar), Rezeptformular (fokussiertes Feld direkt über der Tastatur). Nit **F8**: Label „Vorbereitung“ bricht um |
| 3.10 | Querformat/Tablet ≥ 600 dp: Rail, keine Rail auf Detail; ≥ 840 dp Inhalt begrenzt | ok | G10 | ✅ teilweise: Querformat (891 dp): Rail auf Rezepte; keine Rail in der Kochansicht (zweispaltig). Detailseite und ≥ 840 dp Inhaltsbegrenzung nicht prüfbar (Inhaltsbreite ca. 785 dp). Nit: in der Kochansicht klebt das Kontrollkästchen am Zutatentext |
| 3.11 | TalkBack Kochansicht: Zeilen als Kontrollkästchen; **Timer-Chips einzeln erreichbar?** (B10-S4) | ok / Befund | G11 | ✅ teilweise: Im Zugänglichkeitsbaum sind Schritte und Zutaten als Kontrollkästchen-Zeilen (checked) vorhanden; Timer-Chips („30 min“, „1 min“) sind eigene klickbare Knoten mit 126 px (≈ 48 dp) Höhe, in Fokus- und Listenansicht. Echte TalkBack-Navigation nicht gehört |
| 3.12 | Slot-Chip gut zu treffen; Marker-Chips lesbar | ok | G12 | ✅ teilweise: Slot-Chip „Kein Slot“ hat 126 px (≈ 48 dp) Touch-Höhe, gut zu treffen. Marker-Chips nicht prüfbar (keine Tagesmarker angelegt) |
| 3.13 | Drehen mit offenem Dialog/Sheet und Text (Einkaufsliste, Wochenplan, Einstellungen, Rezeptdetail, Bon-Review); **auch Budget-Dialog** (B7-S3) | erhalten / Befund | G13 | ❌ **F1** Budget-Dialog: bleibt offen, Eingabe „250“ geht beim Drehen verloren (B7-S3 bestätigt). ❌ **F2** Wochenplan-Notiz: Text „Rotationstest“ geht beim Drehen verloren. ✅ Einkaufs-QuickAdd behält den Text (quer und zurück). ❌ **F10** Rezeptdetail-Notiz-Editor: bleibt offen, Text geht verloren. ✅ Einstellungen (Server-URL-Text bleibt), ✅ Abmelden-Dialog bleibt. Einkaufslisten-Dialoge und Bon-Review nicht geprüft (kein Bon). **F9**: am Seitenende überdeckt der FAB den „Speichern“-Knopf des Notiz-Editors |

## 4 · Bons (Phase 3a)
| # | Schritt | Erwartung | G | Ergebnis |
|---|---------|-----------|---|----------|
| 4.1 | Scan online | Werte kommen vom Server, Status „Bitte prüfen“, Foto verkleinert | G18 | ➖ KI aus (Kosten). Scan über „Aus Galerie wählen“ mit Testfoto ausgeführt: Server-KI nicht erreichbar → siehe 4.2 |
| 4.2 | Flugmodus: Bon scannen | sofort vorläufiger Bon; nach Netz KI-Werte; danach keine Datei in `files/receipts/` | G17 | ✅ teilweise: Galerie-Scan, Server ohne KI: sofort „Vorläufig gespeichert“, Bon mit On-Device-OCR-Werten (Markt, 12 Artikel) angelegt. ❌ danach gehen die Werte verloren (**F13**). Flugmodus und „nach Netz KI-Werte“ ➖; Datei in `files/receipts/` nicht geprüft |
| 4.3 | **V3 provozieren:** Scan starten, währenddessen Sync auslösen (Pull-to-Refresh) | Foto bleibt erhalten | V3 | ➖ nicht provoziert (Scan und Upload dauern ca. 1 s, kein Zeitfenster für einen Sync dazwischen) |
| 4.4 | Review: Uhrzeit, Adresse, Rabatt, Pfand-Chip, Kategorien, „Produkt zuordnen“, TalkBack | ok | G18 | ✅ teilweise: Review-Ansicht zeigt Warnkarte „Einige Angaben sind unsicher“, Kopf (Markt, Adresse, Datum, Summe) und Differenz „Positionen 22,66 € · Bon 41,89 €“ in Rot. Pfand-Chip, Produkt zuordnen, TalkBack nicht geprüft |
| 4.5 | **Kopf-Dialog:** ungültigen Betrag/Rabatt eingeben (z. B. „abc“) | Fehler/Sperre? (B8-P1: wird sonst 0,00) | B8-P1 | ❌ **F12** ungültiger Betrag „1,2,3“ im Kopfdaten-Dialog wird beim Speichern stillschweigend 0,00 € (B8-P1 bestätigt) |
| 4.6 | Korrektur bestätigen, gleichen Markt erneut scannen | Korrektur wird übernommen (Lexikon) | G18 | ➖ Lexikon-Lernen nicht geprüft (kein KI-Scan, kein echter Korrekturlauf) |
| 4.7 | Bon-Liste: Monatsgruppen, Chips, „Ältere Bons laden“ (offline: Meldung), Leerzustand; **nur ältere Bons auf Server → Leerzustand zeigt Button?** (B8-P2) | ok / Befund | G19 | ✅ teilweise: Monatsgruppen, Chips („Vorläufig“) und „Ältere Bons laden“ vorhanden. ❌ **F11**: ist nur Älteres auf dem Server, zeigt die leere Liste keinen Knopf (B8-P2 bestätigt); jeder Druck lädt genau einen Monat ohne Rückmeldung (vier Drücke bis Mai). Offline-Meldung ➖ |
| 4.8 | Bon-Detail: vorläufiger Bon mit lokalem Foto, nicht bearbeitbar; **„Neu auslesen“ bei vorläufigem Bon?** (B8-P5); altes Foto vom Server | ok / Befund | G20 | ✅ altes Foto (Mai-Bon) kommt vom Server und wird angezeigt; vorläufiger Bon zeigt lokales Foto und Banner „Vorläufige Werte vom Handy“. „Neu auslesen“ bei vorläufigem Bon nicht geprüft |
| 4.9 | **REPLACE/CASCADE prüfen (B1-Anm):** Bon mit Positionen öffnen → Markt ändern / mit Einkaufsliste verknüpfen → Positionen noch da? | Positionen bleiben | B1-Anm | ❌ **F13** REPLACE + CASCADE bestätigt: nach dem Upload eines vorläufigen Bons ohne Server-Auswertung steht er mit `parseStatus=queued` und 0 Positionen in Room (Foto bleibt). ✅ normaler Speicherweg (Markt ändern, Speichern) behält alle 10 Positionen |
| 4.10 | Lexikon: Eintrag nach Korrektur sichtbar, Löschen wirkt beim nächsten Scan (kein Undo: B8-S1) | ok | G21 | ➖ Lexikon nicht geprüft |

## 5 · Auswertung und Gänge (Phase 3b)
| # | Schritt | Erwartung | G | Ergebnis |
|---|---------|-----------|---|----------|
| 5.1 | Kostenübersicht: vier Diagramme hell/dunkel/alle Akzente, Budgetlinie auf Höhe | ok | G23 | ✅ teilweise (Dunkel, Akzent Lila): vier Diagramme (Ausgaben, Märkte, Kategorien, Top-Artikel), Zahlen stimmen mit den Test-Bons überein (Märkte 95,11 €: Lidl 38,82, Rewe 36,30, Aldi 12,35, dm 7,64). Hell, andere Akzente und Budgetlinie nicht geprüft (kein Budget). Befunde **F14**, **F15** |
| 5.2 | Monat/Woche und Zeitraum-Chips wechseln; Flugmodus → Diagramme bleiben | ok | G23 | ✅ Monat/Woche und Dieser Monat/3 Monate/12 Monate/Gesamt wechseln, Werte stimmen („Gesamt“ schließt den Mai-Bon ein: Lidl 80,71 €). Flugmodus ➖ |
| 5.3 | Leerer Zustand: kurz „Noch keine Auswertung“-Aufblitzen? (B7-P2) | Befund notieren | B7 | ➖ Aufblitzen des Leerzustands nicht beurteilbar (Screenshot-Takt zu grob); Leerzustandstext „Noch keine Auswertung …“ erscheint ohne Bons |
| 5.4 | Top-Artikel antippen → Preisverlauf; TalkBack liest Diagrammbeschreibung | ok | G23 | ✅ Top-Artikel antippen → Preisverlauf (günstigster Markt, Ø/Min/Max, Marktvergleich, Einzelkäufe). Diagramme tragen im Zugänglichkeitsbaum eine Beschreibung; TalkBack-Ansage nicht gehört |
| 5.5 | Artikel > 3 Monate nicht gekauft: „Top-Artikel → Gesamt“ zeigt Preisentwicklung mit Hinweis | ok | G24 | ✅ Waschpulver (20 WL): „Preisentwicklung (alle Monate)“ 05/26 bis 11/26 und Preisverlauf 24.05. bis 15.11. mit dem über 3 Monate alten Mai-Bon |
| 5.6 | Neuer Markt mit Vorlage: 15 Gänge; Kategorie ändern; Ziehen; **Drehen während des Ziehens** (B6-S4); TalkBack | ok / Befund | G25 | ✅ teilweise: Neuer Markt mit „Standard-Gangsatz“ → Gänge mit Kategorie als Unterzeile (neun sichtbar, „Fehlende Standardgänge ergänzen“ vorhanden). Kategorie ändern, Ziehen, Drehen beim Ziehen, TalkBack nicht geprüft |
| 5.7 | Einkaufsliste: „Tomaten“, „Milch“, „Spülmittel“, „Bier“ landen im passenden Gang; Handkorrektur wird gelernt | ok | G26 | ✅ Tomaten → Obst & Gemüse, Milch → Milchprodukte & Eier, Bier → Bier, Wein & Spirituosen, Waschmittel → Haushalt & Tierbedarf, Zahnpasta → Drogerie & Baby (ohne KI). „Spülmittel“ nicht prüfbar (adb tippt keine Umlaute); Handkorrektur-Lernen nicht geprüft |
| 5.8 | Server-KI: „Geschenkpapier“/„Leinsamen“ → nach Sekunden im Gang, danach sofort; Flugmodus: sofort kein Gang; **Artikel während des KI-Laufs hinzufügen** (V6/B6-P1) | ok / Befund | G27 | ➖ KI aus: „Geschenkpapier“, „Leinsamen“ bleiben „Ohne Gang“ (Server antwortet auf `categorize-items` mit 200, kein Absturz). „Artikel während des KI-Laufs“ (V6) nicht provozierbar |

## 6 · Abschluss
Alle ❌ mit Kurzbeschreibung sammeln und mir geben; daraus entstehen die Fix-Aufträge zusammen mit der Liste „An den Nutzer zur Entscheidung“
(`phase-6-daten/review-bericht.md`).

## 7 · Befunde aus dem Testlauf vom 25.11.2026 (lokaler Server, Debug-Build, SM-A546B / Android 16)
Gefunden beim Durchlauf der Abschnitte 1–3. Keine Code-Änderung im Rahmen des Testens; Fix-Aufträge folgen nach Freigabe.
| Nr | Grad | Befund | Beleg / Fundstelle | Zuordnung |
|----|------|--------|--------------------|-----------|
| F1 | Mittel | Budget-Dialog verliert die Eingabe beim Drehen (Dialog bleibt offen, Feld leer) | Tippen „250“, Drehen quer und zurück: Feld leer | bestätigt B7-S3, Testplan 3.13 |
| F2 | Mittel | Wochenplan-Tagesnotiz geht beim Drehen verloren; am Server bleibt `note` leer | `WeekplanScreen.kt:658` `remember(day.id)` statt `rememberSaveable`, Übergabe nur bei `onDone`/`onDispose` (Z. 661, 684) | neu, Testplan 3.13 |
| F3 | Niedrig | Einstellungen zeigen „Letzter Sync: 01.01.1970 01:00“ | App speichert den Server-Zähler `server_seq` (Pull-Cursor, z. B. 17) als `lastSyncTs` (`SyncEngine.kt:94–103`, Server `sync.py:174`) und formatiert ihn als Datum | neu, Sync-Protokoll |
| F4 | Mittel | Inaktive Symbole auf der aufgeklappten Tageskarte (⚡ 👥 🚫 Schloss, „+“, 👍/👎) kontrastarm: gemessen 1,4–2,4 : 1 (Dunkel, Petrol) | `WeekplanScreen.kt:574–611, 947, 956` `onSurfaceVariant.copy(alpha = 0.4f)`; sind bedienbare Schalter | Testplan 3.1 / G01 |
| F5 | Niedrig | G01-Wortlaut „kein Lila außer bei Lila“ trifft nicht zu: Grün, Blau und Petrol nutzen Lila als Tertiärfarbe (Frühstücks-Slot, Warn-Chips Bon, Diagramme). Orange hell: weißer Text auf #E65100 = 3,8 : 1 | `Color.kt`/`Theme.kt:39–46`; `WeekplanScreen.kt:843`, `ReceiptReviewComponents.kt:396,656,680`, `ChartColors.kt:10` | Ermessensfrage (Gestaltung), Testplan 3.1 |
| F6 | Niedrig | Android 16 zeigt beim Start den Dialog „Android-App-Kompatibilität: nicht mit 16 KB kompatibel“ (nur Debug-Build): `libmlkit_google_ocr_pipeline.so`, `libbarhopper_v3.so`, `libdatastore_shared_counter.so`, `libimage_processing_util_jni.so`, `libandroidx.graphics.path.so` | Aufnahme des Kaltstarts | neu; relevant für Play-Store-Pflicht und neuere Geräte, Abhängigkeiten aktualisieren |
| F7 | Niedrig | Bestätigt B4-P1: `/docs` und `/openapi.json` ohne `X-Api-Key` erreichbar. Beifang: leerer `AI_API_KEY` wird nicht vorab abgefangen, der Server fragt trotzdem den Anbieter (401 → 502) | curl lokal | Server |
| F8 | Niedrig | Rezeptformular: Label „Vorbereitung“ bricht in der Dreier-Zeile um („Vorbereitun / g“) | Screenshot, Zeitfelder „Zeiten & Metadaten“ | Optik, Testplan 3.9 |
| F9 | Niedrig | Rezeptdetail am Seitenende: der FAB „+“ überdeckt die rechte Hälfte des „Speichern“-Knopfs im Notiz-Editor (nur die linke Hälfte bleibt antippbar) | Screenshot nach Scrollen bis zum Ende | Testplan 3.13 |
| F10 | Mittel | Rezeptdetail-Notiz-Editor („Meine Notizen“): bleibt beim Drehen offen, der eingetippte Text ist weg | Tippen „Notizdrehtest“, Drehen quer und zurück: Feld leer | Testplan 3.13; gleiches Muster wie F1, F2 (`remember` statt `rememberSaveable`) |
| F11 | Mittel | Bon-Liste: ist nur Älteres auf dem Server, zeigt die leere Liste keinen Knopf „Ältere Bons laden“; mit aktuellen Bons lädt jeder Druck genau einen Monat, leere Monate ohne jede Rückmeldung (vier Drücke bis zum Mai-Bon) | `ReceiptListScreen.kt:204–225` (Fußzeile nur in der gefüllten Liste), `ReceiptRepository.loadOlderMonth` (ein Monat je Aufruf) | bestätigt B8-P2, Testplan 4.7 |
| F12 | Mittel | Kopfdaten-Dialog: ungültiger Betrag („1,2,3“) wird beim Speichern stillschweigend 0,00 €, keine Fehlermeldung, keine Sperre | Bon-Review, Gesamtbetrag | bestätigt B8-P1, Testplan 4.5 |
| F13 | Hoch | Vorläufiger Bon verliert nach dem Upload seine On-Device-OCR-Daten, wenn der Server nicht auswerten kann (`parsed=false`): in Room `parseStatus=queued`, 0 Positionen, Markt leer, Summe 0, Datum springt auf den Aufnahmetag; am Server ebenfalls leer. Das Foto bleibt. Vorher zeigte die App „LIDL … 12 Artikel 47,94 €“, kurz danach „Unbekannter Markt 0 Artikel 0,00 €“ | `ReceiptUploader.kt:89` `upsertReceipt` (REPLACE) auf `receipts`, `receipt_items` hat `ON DELETE CASCADE`; Room-DB per `run-as` ausgelesen | bestätigt B1-Anm (REPLACE + CASCADE), Testplan 4.2, 4.9 |
| F14 | Mittel | Kostenübersicht: der Chip „Gesamt“ bricht in drei Zeilen um („Ge / sa / mt“), die Zeitraum-Chips passen nicht in die Zeile | Screenshot, Zeile „Dieser Monat · 3 Monate · 12 Monate · Gesamt“ | neu, Testplan 5.1 |
| F15 | Niedrig | Kostenübersicht, Balkendiagramm: X-Achsenbeschriftung abgeschnitten („12/2“ statt „12/25“); im Zugänglichkeitsbaum steht der volle Text | Screenshot | neu, Testplan 5.1 |
| F16 | Info | OCR-Fallback am Testbon: 12 statt 10 Positionen, Summe 47,94 € (Brutto-Spalte) statt 41,89 €, „1509“ statt „150g“, Markt = Firmenname, alle Positionen „Ohne Kategorie“ | vorläufiger Bon, ML-Kit-OCR | Qualitätshinweis zum Fallback |
| F17 | Niedrig | Bon-Liste: der FAB „Scannen“ überdeckt am Listenende den Hinweistext und teils die letzte Zeile | Screenshots der Bon-Liste | neu, Optik |
| F18 | Niedrig | Server: `PUT /api/receipts/{id}` akzeptiert IDs, die `/scan` und `/{id}/image` ablehnen (Regex 36 Zeichen Hex und Bindestrich) | Testlauf mit ID „testbon-lidl-0524“ | Server, Beifang |

## 8 · Nachtest Phase 7
Auf dem Gerät nach dem Rollout (Server zuerst, dann App). Ergebnisse in die letzte Spalte eintragen (✅ / ❌ + Kurzbeschreibung).
| Nr | Prüfung | Erwartung | Ergebnis |
|----|---------|-----------|----------|
| 1 | Rollout: `make backup`, Server zuerst, dann App (Debug `com.helga.android.debug` neben der echten App) | Server startet, App verbindet | |
| 2 | Galerie-Scan ohne KI (`AI_API_KEY=`) (4.2, 4.9) | Bon behält Markt, Positionen, Summe; nach Sync am Server ebenfalls | |
| 3 | Drehen mit Budget-Dialog, Wochenplan-Notiz, Rezept-Notiz, QuickEmoji-/Marker-Dialog, Einkaufs-Bearbeiten (3.13) | Text bleibt erhalten | |
| 4 | Gang ziehen und dabei drehen (5.6) | keine Zeile bleibt „gezogen" stehen | |
| 5 | Bon-Kopfdialog „1,2,3" und „1.234,56" (4.5) | „1,2,3" → Fehler, Speichern gesperrt; „1.234,56" wird 1.234,56 € | |
| 6 | Leere Bon-Liste, nur Älteres am Server; offline (4.7) | Knopf da, Snackbar „Nachgeladen aus …"; offline: Fehlermeldung | |
| 7 | Einstellungen „Letzter Sync" | zeigt die echte Uhrzeit | |
| 8 | Optik (3.1, 3.9, 5.1): Wochenplan-Symbole (≥ 3 : 1 inaktiv), Orange hell, Chip „Gesamt" einzeilig, X-Achse „12/25", FAB-Überdeckung (Notiz-Editor, Bon-Listenende), Label „Vorb." | alles lesbar, nichts überdeckt | |
| 9 | Rezeptliste/-detail Dauer | „30 Min" statt „PT30M" | |
| 10 | Kaltstart Android 16 (3.4) | kein 16-KB-Dialog; sonst: welche Bibliotheken nennt er? | |
| 11 | Markdown-Import: FAB → „Markdown importieren" → Zwischenablage enthält die Vorlage → in Gemini mit Wunsch „zwei vegetarische Abendessen" → Antwort einfügen → Vorschau → Speichern | beide in der Liste; am Server (`GET /api/sync?since=0`) vorhanden. Fehlerfälle: Text ohne Titel, leere Eingabe, 51 Rezepte, vorhandener Name (abgewählt) | |
| 12 | Server: `/docs`, `/openapi.json`; `PUT /api/receipts/testbon-lidl-0524`; `PUT …/a.b` | 404; 200; 400 | |

## 9 · Nachtest Phase 8
Auf dem Gerät und am Homelab. Ergebnisse in die letzte Spalte eintragen (✅ / ❌ + Kurzbeschreibung).
| Nr | Prüfung | Erwartung | Ergebnis |
|----|---------|-----------|----------|
| 1 | Server ohne Schlüssel: `curl -i -X POST -F file=@bon.jpg https://<server>/api/receipts/scan` | 401 sofort, Datei wird nicht gespeichert; mit Schlüssel läuft der Scan | |
| 2 | Bestandsfoto groß (> 2000 px) als Bon hochladen | Upload gelingt, Foto am Server ≤ 2000 px; bei 413 bleibt kein Bon dauerhaft „wartet auf Upload" | |
| 3 | Lexikon: Eintrag löschen, „Rückgängig" tippen; zweiter Eintrag löschen und Snackbar abwarten | erster bleibt, zweiter ist nach Ablauf gelöscht | |
| 4 | Bon-Detail mit Fehler beim Neu-Lesen: „Erneut versuchen" | liest wirklich neu (Ladeanzeige), nicht nur Meldung weg | |
| 5 | Bon-Dialoge (Kopf, Position): Tastatur | „Weiter" springt zum nächsten Feld, im letzten „Fertig" | |
| 6 | Einkaufsliste: Artikel hinzufügen, abhaken oder umbenennen, während die Gang-Zuordnung läuft | abgehakter oder umbenannter Artikel behält seinen Stand; weitere neue Artikel werden danach zugeordnet | |
| 7 | KI-Fehlertexte: Klassifizieren und Nährwerte im Flugmodus | „Keine Verbindung zum Server." statt „HTTP …" | |
| 8 | Wochenplan: Rezept zu einem Tag hinzufügen und entfernen | Wochenbilanz (Fleisch/Fisch/Vegetarisch) aktualisiert sich sofort | |
| 9 | Kostenübersicht kalt öffnen | kurz Ladeanzeige, kein aufblitzendes „Noch keine Auswertung" | |
| 10 | Kochansicht mit Timer im Schritt, TalkBack an | Schrittzeile (abhaken) und Timer-Chip sind einzeln ansteuerbar | |
