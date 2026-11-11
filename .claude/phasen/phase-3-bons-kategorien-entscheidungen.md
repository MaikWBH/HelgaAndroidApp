# Phase 3 — Bons, Kategorien, Ausgaben-Auswertung (Entscheidungen aus grilling, 2026-11-11)

Status: **Feinplan geschrieben** in [phase-3a](phase-3a-bons-server-scan.md) und [phase-3b](phase-3b-auswertung-gang-kategorien.md). Dieses Dokument bleibt die Entscheidungsgrundlage.
(Ausdefinieren). Skills: android-clean-architecture, fastapi-templates (Server), mobile-android-design (Diagramm-UI),
code-review (Abschluss). Modelle werden bei Phasenstart (Gate 0) abgefragt.

## Ziel (Nutzerwunsch)
Bon-Scan weiter verbessern: Datum, Markt, Artikel, Preis (plus Zusatzfelder) zuverlässig auslesen und speichern;
Verlauf und Diagramme der Ausgaben; Online-Artikeldaten für genauere Artikelzuordnung; Ablage der Bons **nur auf dem
Server**, damit das Handy nicht zugemüllt wird; Scan ohne Server wird angezeigt und zwischengepuffert.

## Entscheidungen
1. **Einordnung:** eigene Phase nach Phase 2 (nach Phase 2 eingefügt, alle Folgephasen rücken nach).
2. **Datenhaltung:** Server = vollständiges Archiv (Bons + Fotos). Handy hält: Warteschlange noch nicht hochgeladener
   Bons, Bons der **letzten 3 Monate** (Details offline) und **Monatsaggregate für die gesamte Zeit** (Diagramme offline).
   Ältere Bons werden bei Bedarf vom Server geladen.
3. **Zusätzliche Felder** (neben Datum, Markt, Artikel, Menge, Preis, Summe): Uhrzeit, Filiale/Adresse, Rabatte (je Position
   und je Bon), Pfand als eigenes Feld, Einheit (kg/Stk). **Nicht:** Zahlart, MwSt-Klasse.
4. **Offline-Scan:** Foto in Warteschlange puffern, Bon sofort mit **vorläufigen** lokalen ML-Kit-Werten und Hinweis
   „vorläufig" anzeigen; sobald der Server erreichbar ist, wertet die KI aus und ersetzt die Werte (WorkManager).
5. **Artikelzuordnung:** Server normalisiert Bon-Text per KI, sucht in Open Food Facts (Textsuche) und schlägt Treffer zur
   Bestätigung vor; **zusätzlich lernendes Produkt-Lexikon je Markt** (Kürzel → Artikel), das **automatisch bei jeder
   Korrektur im Review** lernt, einsehbar und löschbar.
6. **KI-Modell:** für alle Bon-bezogenen KI-Aufrufe auf dem Server **Haiku 5.5** (`claude-haiku-5-5`), Kostenziel. Qualität per
   Testset absichern: Nutzer legt 10–20 Bon-Fotos **außerhalb des Repos** ab, ein Skript vergleicht Haiku 5.5 mit dem
   bisherigen Modell (Datum, Markt, Summe, Positionen). **Kein** automatischer Wechsel auf ein größeres Modell.
7. **Diagramme (alle vier):** Ausgaben je Monat/Woche als Balken (mit Budgetlinie), Anteil je Markt, Ausgaben je Kategorie,
   Top-Artikel und Preisentwicklung. **Technik: Eigenbau mit Compose Canvas**, keine Chart-Bibliothek.
8. **Kategorien:** eine **feste gemeinsame Kategorienliste** (ca. 15 Einträge, Schlüssel + deutscher Name). Dieselbe Liste
   dient (a) der KI beim Auslesen, (b) der Auswertung „je Kategorie", (c) den Gängen der Märkte. Korrektur im Review lernt mit.
9. **Gang ↔ Kategorie:** `StoreAisle` bekommt einen **Kategorie-Schlüssel** (Room-Migration + Sync-DTO + Server-Spalte). Gangnamen
   und Reihenfolge bleiben je Markt frei. Der Standard-Gangsatz wird an den Katalog angepasst.
10. **Auto-Zuordnung Artikel → Gang** (Hybrid, Reihenfolge): gelerntes Lexikon (`AisleProductKey`) → **lokale Stichwortliste**
    (ca. 300 Begriffe, offline) → **Server-KI (Haiku)** nur für Unbekanntes; Ergebnis wird gelernt. Auftrag des Nutzers:
    prüfen, ob die automatische Zuordnung vernünftig funktioniert (Testfälle dafür schreiben).

## Standardannahmen für Stufe A (nicht abgefragt, zur Freigabe vorlegen)
- Das Bonfoto wird vor dem Upload verkleinert und die lokale Kopie in `filesDir/receipts/` **nach erfolgreichem Upload
  gelöscht** (heute bleibt sie verwaist liegen); Kamera-Temp-Datei in `cacheDir/receipts/` wird nach dem Speichern gelöscht.
- Bons verlassen die Sync-Tabellen (`SYNC_TABLES`) und bekommen eigene Endpunkte (Liste nach Zeitraum, Detail, Aggregate,
  Upload/Re-Parse). Bestehende Bons bleiben erhalten (Server hat sie bereits) und werden migriert.
- Neue Server-Endpunkte und Parser bekommen Tests (heute keine Tests für `parse_receipt`, `reconcile_receipt`, Upload).
- Die ungenutzten Server-Tabellen `ingredient_product_mappings`, `product_prices`, `product_purchases` prüfen: nutzen oder entfernen.
- Phase-1-Regeln bleiben: ViewModels reden nur mit Repositories, `ArchitectureRulesTest` bleibt grün.

## Ist-Zustand (Faktenbasis, Stand 2026-11-11)
- Server-Parsing: `server/app/ai.py` (`RECEIPT_PARSE_USER`, Vision-Modell `AI_VISION_MODEL`, Default `claude-sonnet-4-6`),
  Rückgabe `store_name`, `purchase_date`, `total_amount`, `confidence`, `items[]` (`name`, `quantity`, `unit_price`, `total_price`).
  Prompt ignoriert Uhrzeit, Adresse, Zahlart, MwSt; Rabatte/Pfand haben kein Feld.
- App: `ReceiptRepository.scanReceipt` → KI, bei jeder Exception stiller Fallback auf ML Kit (`ReceiptScanner`); Menge dort fest 1.0;
  `storeId` bleibt automatisch leer; Konfidenz wird nicht gespeichert; Währung fest `EUR`.
- Persistenz: Room `receipts`/`receipt_items`; Retention 3 Monate per Soft-Delete (`purgeOldReceipts`); jedes Gerät pullt alle Bons.
  `ImageUploadWorker` lädt hoch, löscht aber die lokale Datei nicht.
- Auswertung: `CostOverviewScreen` (Karten/Listen nach Markt und Datum, Budget), `ProductPriceDetailScreen` (Canvas-Linie), keine Chart-Bibliothek.
- OFF: Server `off.py` (`/api/off/search`, `/api/off/lookup-barcode`); App nutzt nur Barcode-Lookup; Bon-Artikel haben keine OFF-Verbindung.
- Gänge: `StoreRepository` (10 Standardgänge, `AisleProductEntity`, `AisleProductKey.normalize`); Zuordnung nur gelernt, keine Tests;
  Server-Endpunkt `/api/suggestions/aisles` wird von der App nicht aufgerufen und vergleicht ohne Normalisierung.
- Offene Lücke im Plan: `bons-kosten/plan.md` sagt „Server-Pflicht akzeptabel", der Code hat aber bereits den ML-Kit-Fallback.
