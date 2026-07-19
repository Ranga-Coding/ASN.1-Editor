# ASN.1 Editor - Projektplan

## Projektziel
Erstelle eine Java GUI zur Visualisierung und Bearbeitung von ASN.1 Dateien

## Regeln
- Halte dich exakt an meine Vorgaben.
- Aktualisiere `PLAN.md` bei **jedem** Schritt: Fortschritt, Entscheidungen, Blockaden.
- Aktualisiere die Dokumentation (`*.md` in `docs/` und Kopfkommentare in Java-Dateien) bei jeder Code-Änderung.
- Schreibe Tests für neue Funktionen und aktualisiere bestehende Tests bei Änderungen.
- Baue nur das Nötigste — vermeide Over-Engineering.
- Mache keineBreaking Changes ohne Absprache.

## Technologie-Stack
- Java 21
- Maven
- JavaFX (für die GUI)

## Kern-Komponenten
- `com.asn1editor.model`: Datenmodelle für ASN.1 Strukturen (nutzt Java Records)
- `com.asn1editor.parser`: Logik zum Einlesen und Parsen von ASN.1 Dateien
- `com.asn1editor.service`: Business Logic (Validierung, Transformation)
- `com.asn1editor.ui`: GUI Komponenten (JavaFX Controller, Views)
- `com.asn1editor.io`: Dateihandling (Laden/Speichern von Dateien)

## Datenmodell
- `ASN1Node`: Repräsentation eines Elements im ASN.1 Baum
- `ASN1Document`: Das gesamte Dokument/Struktur (mehrere Roots möglich)

## Entwicklungs-Roadmap & Status

### Phase 1: Maven Setup & Projektstruktur ✅ ABGESCHLOSSEN
- [x] Maven-Projekt mit `pom.xml` konfiguriert
- [x] Package-Struktur erstellt (model, parser, service, io, ui)
- [x] Java 21 + JavaFX 21 Dependencies
- [x] Maven Wrapper installiert

### Phase 2: ASN.1 Parser Implementierung ✅ ABGESCHLOSSEN
- [x] `ASN1Lexer`: Hand-geschriebener Tokenizer für ASN.1-Code
  - Erkennt: `::=` (Definition-Operator), `IDENTIFIER`, `STRING_LITERAL`, `SEQUENCE`, `SET`, `CHOICE`
  - Block-Symbole: `{`, `}`, `|`, `,`
  - Positionserfassung (Zeile/Spalte) für präzise Fehlermeldungen
- [x] `ASN1Parser`: Recursive-Descent-Parser
  - Parst Typ-Definitionen: `MyType ::= SEQUENCE { ... }`, `CHOICE`, `SET`
  - Parst Wert-Definitionen: `MyValue ::= { field1 "value", ... }`
  - Verschachtelte Blöcke unterstützt
  - Fehlerhafte Syntax wirft `ASN1ParseException`

### Phase 3: Domain Model ✅ ABGESCHLOSSEN
- [x] `ASN1Node` (Record): Knoten im ASN.1-Baum
  - Felder: `name`, `value`, `children`, `isLeaf`
  - Factory-Methoden: `leaf()`, `internal()`
  - Immutable durch Java Record
- [x] `ASN1Document` (Record): Gesamtes Dokument mit `List<ASN1Node> roots`
  - Mehrere top-level Definitionen unterstützt
  - Convenience-Methode `root()` für Einzel-Definition-Fälle

### Phase 4: JavaFX GUI Grundgerüst ✅ ABGESCHLOSSEN
- [x] `Main.java`: JavaFX Application Entry Point
- [x] `MainController.java`: FXML Controller mit TreeView, TextArea, Statusleiste
- [x] `main.fxml`: Layout-Definition
  - MenuBar (Datei, Bearbeitung, Hilfe)
  - SplitPane (TreeView links, Tabs rechts)
  - TextArea für Detail-Anzeige und Rohdaten
  - Statusleiste
- [x] `TreeItemContent.java`: Wrapper für TreeItem-Werte (ASN1Node + Anzeigetext)

### Phase 5: Integration Parser & GUI ✅ ABGESCHLOSSEN
- [x] `ASN1Service`: Brücke zwischen Parser und UI
  - `parse(String source)` → `ASN1Document` — erkennt automatisch 3 Formate
  - `detectFormat(String source)` → `FileFormat` (ASN1_TEXT / BASE64 / BINARY)
  - `decodeBase64IfNeeded(String source)` → automatische Base64-Dekodierung
    - **PEM-Format**: `-----BEGIN ...-----` bis `-----END ...-----` → Content extrahiert
    - **Roh-Base64**: Nur Base64-Zeichen + kein `::=` → decodiert
    - **Binär-Erkennung**: Nach Decodierung → ASCII oder Hex-Darstellung (für BER/DER wie .crmf)
    - Graceful Fallback bei ungültiger Base64 → Originaltext unverändert
  - `toHexString(byte[])` / `fromHexString(String)` → Hex-Konvertierung für Binärdateien
  - `readFileBinary(Path)` → `byte[]` für Binärdateien
- [x] `ASN1FileIO`: Datei-Laden und -Speichern
  - `readFile(Path)` → String
  - `writeFile(Path, content)` → void
- [x] TreeView-Baum wird aus geparstem ASN1Document aufgebaut
- [x] Detail-Anzeige beim TreeView-Klick

### Phase 6: Editier-Funktionalität ✅ ABGESCHLOSSEN
- [x] ASN.1-Quelltext direkt bearbeiten (TextArea)
- [x] Änderungen im Modell widerspiegeln
- [x] Validierung vor dem Speichern
- [x] Undo/Redo (optional)
- [x] Bearbeitungs-Menü implementieren (`MainController`)
  - [x] `handleCut()` — Ausschneiden für `rawArea`
  - [x] `handleCopy()` — Kopieren für `rawArea`
  - [x] `handlePaste()` — Einfügen für `rawArea`

### Phase 8: BER/DER-Decodierer für Binärdateien ✅ ABGESCHLOSSEN
- [x] `ASN1BerDecoder`: Dekodiert BER/DER-Binärdaten in ASN1Node-Baum
  - TLV-Parser (Tag-Length-Value)
  - Unterstützt: Primitive/Construktive Tags, UNIVERSAL/APPLICATION/CONTEXT-SPECIFIC
  - Längendekodierung (kurze Form <127, lange Form >127)
  - Konstruktive Typen (SEQUENCE, SET, CHOICE → rekursiv)
  - Primitive Typen (INTEGER, OCTET STRING, BMPString, UTF8String, etc.)
- [x] `ASN1Service.parse()` → bei Binärdatei: BER-Decodierung + TreeView-Aufbau
- [x] GUI: "Hex-Editor" — Binärdateien hexadezimal bearbeiten
  - TextArea mit Monospace-Schrift für Hex-Anzeige
  - Format: 16 Hex-Bytes pro Zeile + ASCII-Spalte rechts
  - Buttons: "Hex anwenden" (Hex → Bytes → BER/DER → TreeView)
  - Buttons: "Hex neu laden" (Hex-Inhalt zurücksetzen)
- [x] **Robuste Fehlerbehandlung**: `BERDecodeException` mit Byte-Positionen
  - Hex-Validierung: ungültige Zeichen, ungerade Anzahl
  - BER-Decodierung: präzise Byte-Offset-Fehler
  - Spezifische Fehlerdialoge für jede Fehlerart
- [ ] **Alternative (optional):** ASN.1-Text-Übersetzung (z.B. "SEQUENCE" statt 0x30) — noch nicht implementiert, da nicht kritisch für Kernfunktionalität.

#### Phase 8.2: UI-Verbesserungen 🆕

**Ziel:** Hex-Editor wird zur zentralen Binärdarstellung; Details und Hex-Editor gleichzeitig sichtbar.

**Hinweis:** `rawArea` wird intern beibehalten (für Plain-text ASN.1 als Text-Ansicht), aber der "Rohdaten"-Tab entfernt.

##### Teil 1: "Rohdaten"-Tab entfernen ✅ ABGESCHLOSSEN
- [x] FXML: `<Tab text="Rohdaten">` und zugehöriges `TextArea` aus TabPane entfernt
- [x] FXML: `rootPane` ID hinzugefügt (für programmatisches Hinzufügen von rawArea)
- [x] `MainController`: `rawArea` als `private TextArea` (nicht mehr @FXML)
- [x] `MainController`: `rootPane` als `@FXML private VBox`
- [x] `MainController.initialize()`: `rawArea` programmatisch erstellt und zu `rootPane` hinzugefügt
  - `rawArea.setVisible(false)` — Standardmäßig ausgeblendet
  - Monospace-Schrift, Prompt-Text "ASN.1-Quelltext (Plain-text)"
- [x] `VBox`-Import hinzugefügt
- [x] `rawArea` wird in `loadFile()` bei Plain-text ASN.1 angezeigt, bei Binärdateien versteckt
- [x] Build: SUCCESS; Tests: 67 / 67 bestanden

##### Teil 2: Details und Hex-Editor nebeneinander (statt Tabs) ✅ ABGESCHLOSSEN
- [x] FXML: `TabPane` durch `SplitPane fx:id="rightSplitPane"` ersetzt (horizontal)
  - Linke Seite: `TextArea fx:id="detailsArea"` (Sans-Serif, 11px)
  - Rechte Seite: `TextArea fx:id="hexEditorArea"` (Monospace, 12px) mit Buttons
  - Divider-Position: 0.25 (Details ~25%, Hex-Editor ~75%)
- [x] Hex-Editor-Buttons ("Hex anwenden", "Hex neu laden", `hexStatusLabel`) in `HBox` über dem `hexEditorArea`
- [x] `MainController`: `@FXML private SplitPane rightSplitPane` hinzugefügt
- [x] FXML: `Tab`, `TabPane`-Import entfernt; `SplitPane`-Import bleibt über `javafx.scene.control.*`
- [x] Details und Hex-Editor parallel bedienbar
- [x] Build: SUCCESS; Tests: 67 / 67 bestanden

##### Teil 3: TreeView-Auswahl → Hex-Editor Byte-Markierung ✅ ABGESCHLOSSEN
- [x] **Datenmodell erweitert:** `ASN1Node` Record bekommt `offset` und `length` (neue 6-Parameter-Variante)
  - Neue Factory-Methode: `ASN1Node.internal(name, children, offset, length)`
  - `ASN1BerDecoder`: `readTlv()` trackt TLV-Byte-Offset und -Länge
  - `readChildren()` und `readChildrenUntilEoc()` akzeptieren `valueAbsOffset` für absolute Kind-Offsets
  - `ASN1Parser`-Knoten: offset = -1, length = 0 (Text-ASN.1)
- [x] **Highlight-Overlay:** `StackPane` in FXML über `hexEditorArea`
  - `Pane hexHighlightOverlay`: transparent, füllt TextArea
  - `Rectangle` mit `Color.rgb(255, 255, 0, 0.25)` (gelb, 25% transparent)
- [x] **Byte-Offset → Text-Position:** `byteOffsetToTextPos(int byteOffset)` → `int`
  - Berücksichtigt: 16 Bytes/Zeile, 3 Zeichen/Byte, ASCII-Spalte
  - Formel: `lineIndex * (ASCII_OFFSET + 49) + ASCII_OFFSET + byteInLine * 3`
- [x] **TreeView-Listener erweitert:** `showDetails()` + `highlightSelectedNode()`
  - `highlightSelectedNode()`: Node offset/length prüfen → `highlightBytes()`
  - `highlightBytes()`: Overlay leeren → Rechteck zeichnen → Hex-Editor scrollen
  - `clearHighlights()`: Overlay leeren
- [x] **Binärdateien:** TreeView-Auswahl markiert TLV-Bytes (Tag + Länge + Value) im Hex-Editor
- [x] **Plain-text ASN.1:** Keine Markierung (offset = -1), nur Detail-Anzeige
- [x] Statuszeite zeigt Byte-Range des ausgewählten Knotens: `SEQUENCE (Byte 0-42)`
- [x] Build: SUCCESS; Tests: 67 / 67 bestanden

**Abhängigkeiten:** Teil 3 hängt von Teil 1 und 2 ab.

##### Teil 4: Statusleiste aktualisieren ✅ ABGESCHLOSSEN
- [x] `updateStatusLabel(ASN1Node node)`: Aktualisiert Statusleiste mit Knotenname, Wert und Byte-Position
- [x] `highlightBytes()` in `MainController`: Nutzt `HexEditorControl.setHighlightRange(start, end)` direkt
- [x] `HexEditorControl`: Integriertes Highlight-Rendering mit `renderHighlight()` (gelb, 25% opacity)
  - X-Position-Berechnung: jedes Byte `i` beginnt bei `i * (HEX_CHARS_PER_BYTE * CHAR_WIDTH + CHAR_WIDTH)`
  - End-X: `(endByteInRow - 1) * BYTE_WIDTH + HEX_ONLY_WIDTH`
  - Y-Position: `row * LINE_HEIGHT`
- [x] `hexHighlightOverlay` (StackPane) entfernt — Highlight direkt im Canvas gerendert
- [x] `clearHighlights()`: Setzt Highlight im HexEditorControl + cursorPosition zurück
- [x] Duplicate `highlightBytes()`-Methode entfernt
- [x] Build: SUCCESS; Tests: 85 / 85 bestanden
  - BER/DER-Knoten: `Ausgewählt: name = value (Byte X-Y)`
  - Text-ASN.1-Knoten: `Ausgewählt: name = value`
  - `cursorPosition`: Zeigt Byte-Range (`Byte X/Y`)
- [x] `clearHighlights()`: Setzt `cursorPosition` zurück
- [x] Build: SUCCESS; Tests: alle bestanden

#### Phase 8.1: Bidirektionale Synchronisation Hex-Editor ↔ TreeView 🆕

**Ziel:** Hex-Editor und TreeView stets konsistent halten — Änderungen in einer Ansicht werden automatisch in der anderen reflektiert.

**Prinzip:** Der Hex-Editor zeigt **immer** den rohen Binärstrom an, niemals encodierte Textformate.

##### Teil 1: Hex-Editor zeigt Binärstrom (Base64/PEM → Hex) ✅ ABGESCHLOSSEN
- [x] Beim Laden einer Base64/PEM-Datei: Decodierung vor der Hex-Darstellung
  - `MainController.loadFile()`: Format-Erkennung → Base64/PEM erkannt → `decodeBase64IfNeeded()` aufgerufen
  - `service.decodeBase64IfNeeded()`: Public gemacht, decodiert PEM/Base64 → decodierter String
  - `service.isHexString()`: Public gemacht, erkennt ob decodiertes Ergebnis Binär ist
  - Binär: `fromHexString()` → `byte[]` → `buildHexEditor(decodedBytes)` (nicht Roh-Base64!)
  - Text: decodierter ASN.1-Text → `buildHexEditor(decodedBytes)` (ISO-8859-1/UTF-8 Bytes)
  - `rawArea` bei Binär: leer (`rawArea.clear()`)
  - `rawArea` bei Text: decodierter ASN.1-Quelltext
- [x] Bei Plain-text ASN.1: Konvertierung Text → Bytes (ISO-8859-1) für Hex-Darstellung
  - `currentHexBytes = rawText.getBytes(ISO-8859-1)` → verlustfreie Byte-Repräsentation
  - `buildHexEditor(currentHexBytes)` zeigt ISO-8859-1 Bytes als Hex
- [x] `ASN1Service.decodeBase64IfNeeded()`: Public, mit Javadoc
- [x] `ASN1Service.isHexString()`: Public, mit Javadoc
- [x] Build: SUCCESS; Tests: alle bestanden

##### Teil 2: Hex-Änderung → TreeView (Hex → BER/DER → Tree) ✅ ABGESCHLOSSEN
- [x] "Hex anwenden": Hex → Bytes → `ASN1BerDecoder.decode(byte[])` → TreeView aktualisieren
  - [x] Neue Exception `BERDecodeException` mit Byte-Offset-Informationen
  - [x] `ASN1BerDecoder`: Alle Fehler werfen `BERDecodeException` mit präzisen Byte-Positionen
  - [x] Hex-Validierung: ungültige Zeichen → präzise Fehlermeldung mit Position (Zeile + Zeichen)
  - [x] Hex-Validierung: ungerade Anzahl → Fehlermeldung mit Zeilennummer
  - [x] BER-Decodierung: ungültige TLV-Struktur → Fehler mit Byte-Offset
  - [x] `MainController.handleApplyHex()`: Spezifische Fehlerbehandlung
    - `BERDecodeException` → "BER/DER-Decodierung fehlgeschlagen" mit Byte-Position
    - `IllegalArgumentException` → "Hex-Editor" mit Zeichendetails
    - Sonstige Exception → generischer Fehlerdialog
  - [x] `ASN1Service.parse()`: Fängt `BERDecodeException` statt `ASN1ParseException`
  - [x] Tests aktualisiert: `ASN1BerDecoderTest.decode_primitiveIndefiniteLength_throws`
  - [x] Build: SUCCESS; Tests: 67 / 67 bestanden

##### Teil 3: TreeView-Änderung → Hex-Editor (Tree → BER/DER → Hex) ✅ ABGESCHLOSSEN
- [x] **Neuer BER/DER-Encoder:** `ASN1BerEncoder` — codiert `ASN1Node`-Baum zurück in `byte[]`
  - Repräsentiert jeden Knoten als TLV (Tag-Length-Value)
  - Unterstützt: UNIVERSAL-, APPLICATION-, CONTEXT-SPECIFIC-Tags
  - Längencodierung: kurze Form (<127), lange Form (≥127), DER-konform (minimal)
  - OIDs: decodierte Form → Byte-Sequenz (BER-konform, aus hex-Feld)
  - NULL-Typ: leere Value-Bytes
  - Primitive Typen: Wert aus hex-Feld rekonstruiert
- [x] `ASN1BerEncoder.encode(ASN1Node)` → `byte[]`
- [x] **MainController-Integration:**
  - `syncHexFromTree()`: Codiert TreeView-Baum → Hex-Editor aktualisieren
  - `containsBerNodes()`: Erkennt BER/DER-dekodierte Knoten (offset >= 0)
  - `getTreeViewDocument()`: Extrahiert ASN1Document aus TreeView
  - Automatische Synchronisation nach `buildTreeView()` bei BER/DER-Bäumen
  - Statusleiste: "Bytes — synchronisiert" / "Fehler!"
  - Encoding-Fehler → Fehlerdialog
- [x] **Tests:** 18 Unit-Tests (Roundtrip für alle Typen, Long-Length, APPLICATION, CONTEXT, RFC5280-Struktur)
- [x] **Gesamt: 85 Tests, alle erfolgreich**

##### Teil 4: Change-Tracking und Konsistenz ✅ ABGESCHLOSSEN
- [x] `hexDirty`: Dirty-Flag für Hex-Editor-Änderungen
- [x] `updateHexStatus()`: Statusleiste aktualisiert basierend auf Dirty-Flag
  - "Bytes — synchronisiert" wenn hexDirty = false
  - "Bytes — Änderungen nicht gespeichert" wenn hexDirty = true
- [x] Dirty-Tracking-Listener in `initialize()`: Erkennt Änderungen am Hex-Editor-Text
- [x] Dirty-Flag zurückgesetzt nach `handleApplyHex`, `handleReloadHexFromTree`, `syncHexFromTree`
- [x] `buildHexEditor()` setzt dirty = false (da es den Editor mit aktuellen Bytes überschreibt)

### Phase 7: Unit Tests & Refactoring ✅ ABGESCHLOSSEN
- [x] Lexer-Tests (7 Tests)
- [x] Parser-Tests (12 Tests)
- [x] Model-Tests (8 Tests)
- [x] Service-Tests (35 Tests) — ASN1Service:
  - `decodeBase64IfNeeded`: PEM, PEM-multiline, PEM-invalid, roher Base64, roher Base64→Binär, Invalid-Padding, Plain-text, Empty
  - `detectFormat`: ASN1_TEXT, BASE64, BINARY, PEM, High-Byte, Empty
  - `toHexString`/`fromHexString`: Simple, Empty, Binary, BER-TAG, Roundtrip, Lowercase, No-spaces
  - `parse`: Plain-text, Base64→Text, PEM→Text, PEM-multiline, Roh-Binär, Base64→Binär, Invalid
- [x] **Gesamt: 67 Tests, alle erfolgreich**
- [ ] GUI-Integrationstests (optional)
- [ ] Code-Refactoring basierend auf Feedback

## Design-Prinzipien
- Dependency Injection für Services
- Single Responsibility Principle (Parser von GUI trennen!)
- Immutabilität: Nutze Records für die Modell-Daten, um Seiteneffekte zu minimieren
- Separation of Concerns: UI darf nicht direkt die Parser-Logik aufrufen, sondern nutzt Services

## Unterstützte Formate
- **Plain-text ASN.1** (BER-Textformat, z.B. RFC 2252/2279 Beispiele)
- **Base64-kodierte ASN.1** (automatische Erkennung und Dekodierung)
  - PEM-Format: `-----BEGIN ...-----` / `-----END ...-----` → Content extrahiert und decodiert
  - Roh-Base64 ohne Header → direkt decodiert
  - Decodierte Binärdaten (BER/DER, wie `.crmf`) → Hex-Darstellung
- **Binäre ASN.1 (DER/BER)** — wird als Hex-String erkannt, ASN.1-Struktur wird decodiert und im TreeView angezeigt; Bearbeitung über Hex-Editor mit 16 Bytes pro Zeile + ASCII-Spalte
- **Logging**: `asn1-editor.log` — Dateiformat, Dekodierung, Parsing-Status, Binär-Hex

## Aktueller Status (2026-07-13)
- **Phasen 1-7: Fertig**
- **Phase 8: In Arbeit** (BER/DER-Decodierer, TreeView-Integration, Hex-Editor, Fehlerbehandlung)
  - `ASN1BerDecoder`: `buildNode()` + `readLength()` in `Decoder`-Klasse verschoben
  - `TagInfo`-Record (Tag-Number, Name, Constructed-Flag, Class)
  - `BERDecodeException` (neue Exception für BER-Fehler mit Byte-Position)
  - `ASN1Service.processedSource`-Initialisierung behoben
  - Robuste Fehlerbehandlung: Hex-Validierung + BER-Decodierungsfehler
- **Phase 8.1 Teil 3: Fertig** (BER/DER-Encoder)
  - `ASN1BerEncoder`: Codiert ASN1Node-Baum zurück in byte[] (TLV-Format)
  - Unterstützt: UNIVERSAL-, APPLICATION-, CONTEXT-SPECIFIC-Tags
  - Längencodierung: kurze Form (<127), lange Form (≥127), DER-konform
  - OIDs: aus hex-Feld rekonstruiert
  - NULL-Typ: leere Value-Bytes
  - MainController: `syncHexFromTree()` für automatische Synchronisation
  - 18 Unit-Tests (Roundtrip für alle Typen)
- **Phase 8.1 Teil 4: Fertig** (Change-Tracking und Konsistenz)
  - `hexDirty`: Dirty-Flag für Hex-Editor-Änderungen
  - `updateHexStatus()`: Statusleiste zeigt "synchronisiert" / "Änderungen nicht gespeichert"
  - Dirty-Tracking-Listener in `initialize()`
  - Dirty-Flag wird nach `handleApplyHex`, `handleReloadHexFromTree`, `syncHexFromTree` zurückgesetzt
- **Phase 8.1 Teil 5: Fertig** (Persistenz und Dateihandling)
  - `isBinaryFile`: Flag für Binärdatei-Erkennung (BER/DER)
  - `loadFile()`: Setzt `isBinaryFile = true` bei BASE64→Binär, `false` bei Plain-text
  - `saveToFile()`: Schreibt `currentHexBytes` bei Binärdateien, `rawArea.getText()` bei Text
  - `showBinaryHex()`: Setzt `isBinaryFile = true`
  - IOException-Handling für `Files.write()`
- **Phase 8.1 Teil 6: Fertig** (Debounce für automatische Hex → TreeView-Aktualisierung)
  - `autoSyncTimer`: Timer für automatische Synchronisation (300ms Debounce)
  - `scheduleAutoSync()`: Plant Sync nach 300ms Inaktivität, setzt bestehenden Timer zurück
  - `executeAutoSync()`: Führt Hex → Bytes → BER/DER → TreeView aus
  - Dirty-Listener ruft `scheduleAutoSync()` bei jeder Hex-Änderung auf
  - Statusleiste: "Bytes — automatisch synchronisiert" / "Fehler!"
  - Timer in `initialize()` initialisiert (Daemon-Thread)
- **Phase 8.1 Teil 7: Fertig** (Manueller Override — "Hex anwenden" bleibt als Fallback)
  - `handleApplyHex()`: Fügt `autoSyncTimer.cancel()` am Anfang hinzu (Debounce überspringen)
  - `handleReloadHexFromTree()`: Fügt `autoSyncTimer.cancel()` hinzu
  - Statusleiste: "Bytes — angewendet" / "Bytes — neu geladen"
- **Build: SUCCESS** (`mvn clean test` läuft durch)
- **Tests: 85 / 85 bestanden** (inkl. 18 neue Encoder-Tests)
- **Code-Größe**: ~17 Java-Quelldateien (inkl. `ASN1BerEncoder`, `ASN1EncodeException`), ~1 FXML-Layout

### Update 2026-07-12 — VS-Code-Startkonfiguration korrigiert
- FXML-Import für `Button` ergänzt, damit der neue Hex-Editor-Tab zur Laufzeit korrekt geladen werden kann.
- `.vscode/settings.json` ergänzt:
  - Maven-Pfad explizit auf `c:\Tools\apache-maven-3.9.9\bin\mvn.cmd` gesetzt.
  - Maven-Import für Java aktiviert.
  - Build-Konfiguration auf automatische Aktualisierung gesetzt.
- `.vscode/launch.json` korrigiert:
  - `preLaunchTask` startet jetzt nur noch `mvn: compile` statt die GUI bereits vor F5 zu starten.
  - JavaFX `--module-path` und `--add-modules` für F5 ergänzt.
  - Startargument `Test.crmf` gesetzt.
- `.vscode/tasks.json` bereinigt:
  - `mvn: compile`
  - `mvn: test`
  - `run: javafx`
- Verifikation: `mvn clean test -q` erfolgreich; `mvn javafx:run` startet ohne direkten Build-/FXML-Fehler.

### Update 2026-07-12 — Hex-Editor für Binärdateien
- Neuer Tab "Hex-Editor" in der rechten Panel-Leiste (neben "Details" und "Rohdaten").
- `hexEditorArea`: TextArea mit Monospace-Schrift (12pt) für Hex-Darstellung.
- Buttons im Hex-Editor-Tab:
  - **"Hex anwenden"** — liest Hex-Inhalt, konvertiert zu Bytes, dekodiert BER/DER, aktualisiert TreeView
  - **"Hex neu laden"** — setzt Hex-Anzeige auf aktuellen Dateistand zurück
- `hexStatusLabel`: Zeigt Byte-Anzahl und Status ("Bytes — angewendet" / "Bytes — neu geladen" / "Fehler!")
- Hex-Editor-Format angepasst: 16 Hex-Bytes pro Zeile, danach durch Leerzeichen getrennt die ASCII-Darstellung; nicht druckbare Zeichen werden als `.` angezeigt.
- `handleApplyHex()` wertet nur die linke Hex-Spalte aus, ignoriert die ASCII-Spalte und formatiert nach erfolgreicher Dekodierung neu.
- `currentHexBytes`: Speichert Original-Bytes für Reload-Funktion
- `loadFile(...)`: Lädt jetzt auch Hex-Inhalt in den Hex-Editor
- `showBinaryHex(...)`: Füllt ebenfalls den Hex-Editor
- Verifikation: `powershell.exe -NoProfile -Command "& 'c:\Tools\apache-maven-3.9.9\bin\mvn.cmd' clean compile && test -q"` erfolgreich.

### Update 2026-07-11 — Test.crmf automatisch laden
- `run.bat` startet die Anwendung jetzt mit `Test.crmf` als Startargument.
- `Main` lädt die erste angegebene Datei beim Start; wenn kein Argument übergeben wird, wird `Test.crmf` im Arbeitsverzeichnis geladen, falls vorhanden.
- Der Datei-Öffnen-Dialog unterstützt nun `.crmf`, `.der` und `.ber`.
- `ASN1Service.parse(String, Path)` loggt den echten Dateinamen statt `(null)`.
- `ASN1FileIO.readFile` liest byte-erhaltend mit ISO-8859-1, damit CRMF/DER-Binärdaten erkannt werden können.
- BER-Indefinite-Length (`0x80`) für konstruktive Typen wird unterstützt und bis zum End-of-Contents-Marker (`00 00`) gelesen.
- Regressionstests für einfache, verschachtelte und ungültige indefinite BER-Längen ergänzt.
- Verifikation: `powershell.exe -NoProfile -Command "& 'c:\\Tools\\apache-maven-3.9.9\\bin\\mvn.cmd' test -q"` erfolgreich.
- `Test.crmf` manuell über `ASN1Service.parse(..., Path.of("Test.crmf"))` geprüft: Root `SEQUENCE [CONSTRUCTED]` wird dekodiert.

### Update 2026-07-11 — RFC5280-Anzeige, TreeView und letzter Dateipfad
- Der generische BER/DER-Decoder bleibt schema-unabhängig, zeigt aber bekannte RFC5280/X.509-OIDs mit Namen an.
  - Beispiele: `sha256WithRSAEncryption`, `rsaEncryption`, `commonName`, `basicConstraints`, `keyUsage`, `subjectAltName`, `authorityInfoAccess`, `serverAuth`, `clientAuth`.
- OIDs im linken Baum werden jetzt im Klartext angezeigt, z.B. `sha256WithRSAEncryption` statt `OBJECT IDENTIFIER = 1.2.840.113549.1.1.11 (sha256WithRSAEncryption)`.
- Die Detailansicht zeigt Leaf-Metadaten wie `kind`, `hex`, `value` und `oid` direkt an.
- Der ASN.1-Baum wird nach dem Laden rekursiv aufgeklappt.
- Der zuletzt genutzte Ordner wird über Java Preferences gespeichert und beim Öffnen/Speichern als Default verwendet.
- FileChooser unterstützt zusätzlich `.cer`, `.crt` und `.pem`.
- Regressionstest für RFC5280-OID-Anzeige ergänzt.
- Verifikation: `powershell.exe -NoProfile -Command "& 'c:\\Tools\\apache-maven-3.9.9\\bin\\mvn.cmd' test -q"` erfolgreich.

### Update 2026-07-11 — Erweiterte OID-Namenszuordnung und Klartextanzeige
- **OID-Namenszuordnung erweitert**: Von ~30 auf ~120 Einträge.
  - X.520/X.501 DN-Attribute (2.5.4.x) mit Kategorie: `organizationName (X.520 DN component)`
  - BSI TR-03109/PKIMessage OIDs: `bsiCertReqMsgs`, `bsiCertRepMsgs`, etc.
  - PKCS#1 RSA-Algorithmen, PKCS#7/CMS Types, PKCS#9 Attribute
  - X.509 Extensions (2.5.29.x), Internet/PKIX (1.3.6.1.5.5.7.x)
  - EC- und Kurven-OIDs, DSA, SHA-Hash-OIDs, BSI-Algorithmen
- **formatOidValue()**: Bekannte OIDs werden formatiert als `oid (name)`, z.B. `"2.5.4.10 (organizationName (X.520 DN component))"`.
- **formatOidDisplay()**: Extrahiert den Namen aus dem value-Feld unter Berücksichtigung verschachtelter Klammern.
  - Bekannte OIDs: `"2.5.4.10 organizationName (X.520 DN component)"`
  - Unbekannte OIDs: nur der rohe OID-String (z.B. `"0.4.0.127.0.7.4.1.1.1"`)
- Verifikation: `mvn clean test` — alle Tests bestanden.

## Nächste Schritte — Phase 8.1 Teil 3: BER/DER-Encoder

**Ziel:** Bidirektionale Synchronisation — TreeView-Änderungen in Hex-Editor spiegeln.

### Nächste Aufgabe

| # | Aufgabe | Aufwand |
|---|---------|---------|
| 1 | `ASN1BerEncoder`: Codiert ASN1Node-Baum zurück in byte[] (TLV-Format) | Mittel |
| 2 | Unterstützung: UNIVERSAL-, APPLICATION-, CONTEXT-SPECIFIC-Tags | Klein |
| 3 | Längencodierung: kurze Form (<127), lange Form (≥127), indefinite Form | Klein |
| 4 | OIDs: decodierte Form → Byte-Sequenz (BER-konform) | Klein |
| 5 | MainController: TreeView-Änderung → Hex-Editor aktualisieren | Klein |

Danach weiter mit **Phase 8.1 Teil 10**: HexEditor — Breitenproblem beheben (nur 4 Bytes/Zeile).

### Offenes Problem: HexEditor zu schmal

**Status:** Behoben (CHAR_WIDTH von 7 auf 9 erhöht), aber Breite noch nicht optimal.

**Problem:** Der HexEditor zeigt nur ~4 Bytes pro Zeile statt 16.

**Ursache:** `CHAR_WIDTH` war auf 7 Pixel gesetzt, Consolas 14px benötigt ~9px pro Zeichen. Die Spaltenbreiten (`HEX_COLUMN_WIDTH`, `ASCII_COLUMN_WIDTH`) wurden falsch berechnet.

**Durchgeführte Korrektur:**
- `CHAR_WIDTH` von 7 auf 9 erhöht
- Spaltenbreiten neu berechnet: `HEX_COLUMN_WIDTH = 16 * (2*9 + 9) - 9 = 351px`
- Kompiliert und getestet

**Nächste Schritte:**
- Runtime-Testing der Breite
- Bei Bedarf: `CHAR_WIDTH` anpassen oder ScrollPane horizontal scrollbar machen
- FXML: `prefWidth`/`maxWidth` setzen falls nötig

---

Danach weiter mit **Phase 8.2**: UI-Verbesserungen (TreeView-Highlighting, Suchfunktion).

### Status Phase 8.1 Teil 9: ✅ Abgeschlossen
- `HexEditorControl.java` erstellt — Canvas-basierter Hex-Editor
- FXML: `TextArea` → `HexEditorControl`
- MainController: Alle TextArea-Aufrufe ersetzt
- 85/85 Tests bestanden
- Features: Überschreiben, Cursor-Sprung, Backspace/Delete, Pfeiltasten, Byte-Highlighting, Cursor-Overlay

---

### Phase 8.1 Teil 9: Klassischer Hex-Editor (state of the art)

**Ziel:** Einen komfortablen, klassischen Hex-Editor implementieren, der sich wie etablierte Tools (HxD, Hex Fiend, VS Code Hex Editor) verhält.

#### Anforderungen

| Nr. | Anforderung | Beschreibung |
|-----|-------------|-------------|
| 1 | **Überschreiben statt Einfügen** | Eingabe überschreibt das Zeichen an der Cursor-Position |
| 2 | **Cursor-Sprung** | Nach jeder Eingabe springt der Cursor zum nächsten Hex-Zeichen |
| 3 | **Backspace/Delete** | Löscht vorheriges/nächstes Hex-Zeichen |
| 4 | **Pfeiltasten** | Bewegen den Cursor zwischen Byte-Positionen |
| 5 | **Maus-Klick** | Positioniert Cursor an nächster gültiger Hex-Position |
| 6 | **ASCII-Spalte schreibgeschützt** | Keine Eingabe in ASCII-Bereich möglich |
| 7 | **Leerzeichen geschützt** | Trennzeichen zwischen Bytes nicht editierbar |
| 8 | **Scroll-Position erhalten** | Editor springt nicht nach oben bei Änderungen |
| 9 | **Hex-Validierung** | Nur 0-9, A-F werden akzeptiert |
| 10 | **Byte-Markierung** | Cursor-Byte wird visuell hervorgehoben |

#### Architektur

```
HexEditorControl (Custom JavaFX Control)
├── ScrollPane (Container)
│   ├── HexEditorCanvas (Custom Node)
│   │   ├── Hex-Zeichen (Text-Rendering)
│   │   ├── ASCII-Zeichen (Text-Rendering, read-only)
│   │   ├── Separator-Leerzeichen
│   │   └── Cursor-Indikator (Blinkender Block)
│   └── HighlightOverlay (Byte-Markierung)
├── Eingabe-Verarbeitung
│   ├── KeyTyped-Event → Nur 0-9, A-F
│   ├── KeyPressed-Event → Backspace/Delete/Pfeiltasten
│   └── MouseEvent → Cursor-Positionierung
└── State
    ├── byteOffset → Aktuelles Byte (0..data.length-1)
    ├── charInByte → 0 oder 1 (erstes/zweites Hex-Zeichen)
    ├── scrollPosition → Vertikale Scroll-Position
    └── data → Aktuelle Byte-Daten
```

#### Implementierungsplan

| Schritt | Aufgabe | Dateo | Aufwand |
|---------|---------|-------|--------|
| 1 | `HexEditorControl.java` erstellen | `src/main/java/com/asn1editor/ui/HexEditorControl.java` | Groß |
| 2 | Custom Rendering mit `Canvas` oder `Text`-Nodes | `HexEditorControl.java` | Groß |
| 3 | Eingabe-Validierung (nur Hex-Zeichen) | `HexEditorControl.java` | Klein |
| 4 | Cursor-Logik (Überschreiben, Springen) | `HexEditorControl.java` | Mittel |
| 5 | Scroll-Position erhalten | `HexEditorControl.java` | Klein |
| 6 | Byte-Markierung (Highlight) | `HexEditorControl.java` | Klein |
| 7 | Integration in `MainController` | `MainController.java` | Mittel |
| 8 | FXML-Anpassung | `main.fxml` | Klein |
| 9 | Testing | — | Klein |

#### Technische Entscheidungen

- **Rendering:** `Canvas` für performantes Rendering großer Dateien
- **Cursor-Rendering:** Eigener Overlay-Canvas über dem Text-Canvas
- **Scroll-Verhalten:** `VScrollBar.value` wird vor/nach Textänderung gespeichert
- **Event-Handling:** Direkte Event-Filter im Control, keine TextArea
- **Keine External-Libs:** Reine JavaFX-Implementierung

### Status Phase 8.2: ✅ Alle Teile abgeschlossen
- Teil 1: "Rohdaten"-Tab entfernt ✅
- Teil 2: Details + Hex-Editor nebeneinander ✅
- Teil 3: TreeView-Auswahl → Hex-Editor Byte-Markierung ✅

### Update 2026-07-12 — Validierung vor dem Speichern
- `MainController.saveToFile(...)` validiert den aktuellen `rawArea`-Inhalt vor dem Schreiben über `ASN1Service.parse(...)`.
- Bei Validierungsfehler wird ein Bestätigungsdialog angezeigt; der Benutzer kann bewusst trotzdem speichern oder abbrechen.
- `handleSaveAs()` setzt `currentFile` nur noch, wenn tatsächlich gespeichert wurde.
- Verifikation: `powershell.exe -NoProfile -Command "& 'c:\Tools\apache-maven-3.9.9\bin\mvn.cmd' test -q"` erfolgreich.

### Update 2026-07-12 — TreeView nach Bearbeitung aktualisieren
- Menüpunkt `Bearbeitung > Aktualisieren` ergänzt.
- `MainController.handleRefresh()` parst den aktuellen `rawArea`-Inhalt und baut den TreeView bei Erfolg neu auf.
- Bei Parsingfehler bleibt der bestehende TreeView unverändert; Fehlerdialog und Statusleistenmeldung werden angezeigt.
- Verifikation: `powershell.exe -NoProfile -Command "& 'c:\Tools\apache-maven-3.9.9\bin\mvn.cmd' test -q"` erfolgreich.

### Update 2026-07-12 — FXML-Import für StackPane/Pane behoben
- Fehler `StackPane is not a valid type` beim Start der GUI behoben.
- `<?import javafx.scene.layout.StackPane?>` und `<?import javafx.scene.layout.Pane?>` in `main.fxml` hinzugefügt.
- `mvn clean compile`: SUCCESS
- `mvn javafx:run`: GUI startet ohne FXML-Fehler.

### Update 2026-07-12 — Teil 3 (Phase 8.2): TreeView-Auswahl → Hex-Editor Byte-Markierung
- **Datenmodell:** `ASN1Node` Record um `offset` und `length` erweitert (6 Parameter).
  - Neue Factory: `ASN1Node.internal(name, children, offset, length)`
  - `ASN1BerDecoder`: `readTlvWithOffset()` trackt TLV-Byte-Positionen.
  - `readChildren()` und `readChildrenUntilEoc()` akzeptieren `valueAbsOffset` für absolute Kind-Offsets.
- **Highlight-Overlay:** `StackPane` mit transparentem `Pane hexHighlightOverlay` über `hexEditorArea`.
  - Markierung: `Rectangle` mit gelber Farbe (25% transparent).
  - `byteOffsetToTextPos()`: Konvertiert Byte-Offset in Text-Position (16 Bytes/Zeile).
  - `highlightSelectedNode()`: Prüft Node offset/length → markiert im Hex-Editor.
  - Statuszeite zeigt Byte-Range: `SEQUENCE (Byte 0-42)`.
- **TreeView-Listener:** Erweitert um `highlightSelectedNode()` + `clearHighlights()`.
- FXML: `StackPane` um `hexEditorArea` herum, `Pane hexHighlightOverlay` als Overlay.
- Build: SUCCESS; Tests: 67 / 67 bestanden.

### Update 2026-07-12 — Teil 2 (Phase 8.2): Details + Hex-Editor nebeneinander
- **Änderung:** `TabPane` durch `SplitPane` ersetzt — Details und Hex-Editor gleichzeitig sichtbar.
- FXML: `TabPane` → `SplitPane fx:id="rightSplitPane"` mit Divider bei 0.25
- Linke Seite: `detailsArea` (Sans-Serif, 11px); Rechte Seite: Hex-Editor mit Buttons
- `MainController`: `@FXML private SplitPane rightSplitPane` hinzugefügt
- FXML: `Tab`, `TabPane`-Import entfernt
- Build: SUCCESS; Tests: 67 / 67 bestanden.

### Update 2026-07-12 — Teil 1 (Phase 8.2): Rohdaten-Tab entfernt
- **Änderung:** Tab "Rohdaten" aus FXML entfernt, `rawArea` programmatisch erstellt.
- `rawArea` wird in `initialize()` als neues `TextArea` erstellt und zu `rootPane` hinzugefügt.
- `rawArea.setVisible(true)` nur bei Plain-text ASN.1 und Base64→ASN.1-Text.
- `rawArea.setVisible(false)` bei Binärdateien (BASE64→BER/DER).
- FXML: `rootPane` ID hinzugefügt; `VBox`-Import in `MainController` ergänzt.
- Build: SUCCESS; Tests: 67 / 67 bestanden.

### Update 2026-07-12 — VS Code Debugging-Setup (JavaFX + Attach)
- **Problem:** VS Code Java Extension startet JavaFX-Apps nicht mit korrektem `--module-path` → `Fehler: JavaFX-Runtime-Komponenten fehlen`.
- **Lösung:** App-Start über Maven (`mvn javafx:run` oder `mvn exec:java` mit Debug-Agent).
- **Neue Dateien:**
  - `.vscode/start-debug.bat`: Startet Java direkt mit `-agentlib:jdwp=...suspend=y,address=5005`.
  - `.vscode/start-debug-vscode.bat`: Startet Java im Hintergrund und öffnet VS Code.
- **VS Code Configs:**
  - `.vscode/launch.json`: Attach-Konfiguration an Port 5005; Compound-Config `ASN.1 Editor (Debug — F5)`.
  - `.vscode/tasks.json`: Tasks `run: javafx` (normal), `run: javafx debug` (mit Debug-Agent, Hintergrund).
  - `.vscode/settings.json`: JavaFX-Module und Classpath korrekt konfiguriert.
- **Debugging-Ablauf (2 Schritte):**
  1. `start-debug.bat` ausführen → JVM startet, wartet auf Debugger an Port 5005.
  2. In VS Code F5 drücken → Debugger attacht, GUI öffnet sich, Breakpoints funktionieren.
- **Begründung:** VS Code Java Extension unterstützt JavaFX-Debugging nicht nativ; der Attach-Ansatz ist der zuverlässigste Weg.

### Update 2026-07-12 — Teil 1: Hex-Editor zeigt decodierte Binärdaten
- **Problem:** Hex-Editor zeigte Rohbytes der Datei (z.B. Base64-Text als Hex), nicht den decodierten Inhalt.
- **Lösung:** `loadFile()` erkennt Format (BASE64/PEM/ASN1_TEXT) und decodiert vor der Hex-Darstellung.
  - Base64/PEM: `decodeBase64IfNeeded()` → decodierte Bytes → `buildHexEditor(decodedBytes)`
  - Binär (BER/DER): `fromHexString()` → `byte[]` → Hex-Editor + TreeView via `ASN1BerDecoder`
  - Plain-text: ISO-8859-1 Bytes → Hex-Editor
  - `rawArea` bei Binär: leer; bei Text: decodierter ASN.1-Quelltext
- `ASN1Service.decodeBase64IfNeeded()`: Public gemacht (war package-private)
- `ASN1Service.isHexString()`: Public gemacht (war private)
- Build: SUCCESS; Tests: alle bestanden.

### Update 2026-07-12 — Undo/Redo-Menü
- Menüpunkte `Bearbeitung > Rückgängig` und `Bearbeitung > Wiederholen` ergänzt.
- `MainController.handleUndo()` und `MainController.handleRedo()` nutzen die integrierte JavaFX-TextArea-Historie von `rawArea`.
- Verifikation: `powershell.exe -NoProfile -Command "& 'c:\Tools\apache-maven-3.9.9\bin\mvn.cmd' test -q"` erfolgreich.

### Update 2026-07-19 — Phase 8.2 Teil 4: Statusleiste aktualisiert
- **`updateStatusLabel(ASN1Node node)`**: Neue Methode in `MainController`
  - BER/DER-Knoten: `Ausgewählt: commonName = "Test" (Byte 142-150)`
  - Text-ASN.1-Knoten: `Ausgewählt: commonName = "Test"`
  - `cursorPosition`: Zeigt `Byte 142/150` für BER/DER, leer für Text
- `clearHighlights()`: Setzt `cursorPosition` zurück
- **Build**: SUCCESS; **Tests**: Alle bestanden

### Update 2026-07-19 — Byte-Highlight Position korrekt
- **Problem**: TreeView-Knoten-Auswahl highlightete falsche Bytes im Hex-Editor
- **Ursache 1 (Decoder)**: `ASN1BerDecoder.readTlvWithOffset()` berechnete `lengthBytesBeforeValue` **vor** `readLength()`, dadurch fehlte das Length-Byte im Header. `ASN1Node.length()` war immer zu klein.
- **Fix 1**: `headerBytes = pos - startPos` berechnet **nach** `readLength()`. `valueAbsOffset` Korrektur für verschachtelte Knoten: `absOffsetStart + headerBytes`.
- **Ursache 2 (Highlight)**: `HexEditorControl.renderHighlight()` berechnete `endByteInRow` als **absolute** Position, aber `startByteInRow` war **relativ** zur Zeile. Inkonsistenz → falsche End-X-Position.
- **Fix 2**: `endByteInRow = Math.min(highlightEnd, rowEndByte) - rowStartByte` — jetzt konsistent relativ zur Zeile.
- **Tests**: `HighlightDebugTest` (3 Tests) verifiziert Decoder-offsets. `HighlightPositionTest` (7 Tests) verifiziert X/Y-Pixel-Positionen im Canvas für Einzel-/Mehrzeilen-Highlights.
- **Build**: SUCCESS; **Tests**: 95 / 95 bestanden

### Update 2026-07-13 — Phase 8.1 Teil 2: Robuste Fehlerbehandlung
- **`BERDecodeException`** (neue Klasse): Exception mit Byte-Offset-Informationen für BER/DER-Decodierungsfehler
  - Konstruktoren: `BERDecodeException(message, byteOffset)` und `BERDecodeException(message, byteOffset, byteLength)`
  - Format: `BER/DER-Decodierungsfehler bei Byte X[–Y]: <Nachricht>`
- **`ASN1BerDecoder`**: Alle Fehler werfen jetzt `BERDecodeException` statt `ASN1ParseException`
  - `decode()`: Leere Daten + Daten nach Root-Element
  - `readTlvWithOffset()`: Unerwartetes Ende, indefinite Länge, negative Länge
  - `readByte()`, `readBytes()`, `readLength()`: Präzise Byte-Positionen
- **`MainController.parseHexEditorText()`**: Präzisere Fehlermeldungen
  - Ungültige Zeichen → zeigt das Zeichen und seine Position (Zeile + Position)
  - Ungerade Anzahl → zeigt die Anzahl und Zeilennummer
- **`MainController.handleApplyHex()`**: Spezifische Fehlerbehandlung
  - `BERDecodeException` → Dialog "BER/DER-Decodierung fehlgeschlagen" mit Byte-Position
  - `IllegalArgumentException` → Dialog "Hex-Editor" mit Zeichendetails
  - Sonstige Exception → generischer Fehlerdialog
- **`ASN1Service.parse()`**: Fängt `BERDecodeException` statt `ASN1ParseException`
- **`ASN1BerDecoderTest`**: Test aktualisiert (`decode_primitiveIndefiniteLength_throws`)
- Build: SUCCESS; Tests: 67 / 67 bestanden

---

### Phase 8.3: UTF8String Byte-Highlighting in TEST.crmf ✅ ABGESCHLOSSEN

**Ziel:** Sicherstellen, dass die Selektion des 1. UTF8String-Knotens (Value: "SM-Test-PKI-DE") in `TEST.crmf` im Hex-Editor exakt die korrekten TLV-Bytes hervorhebt.

**Status:** ✅ Abgeschlossen — Bug in `readChildrenUntilEoc()` behoben.

#### Fehlerbeschreibung

- **Datei:** `TEST.crmf` — Base64-kodierte CRMF-Datei (1632 Bytes nach Decodierung)
- **Knoten:** 1. UTF8String mit Value "SM-Test-PKI-DE"
- **Korrekte TLV-Struktur:**

| Byte-Offset | Hex | Bedeutung |
|-------------|-----|-----------|
| 69 | `0x0C` | Tag: UTF8String |
| 70 | `0x0E` | Länge: 14 |
| 71–84 | `53 4D 2D 54 65 73 74 2D 50 4B 49 2D 44 45` | Value: "SM-Test-PKI-DE" |

- **Korrekter TLV-Bereich:** Offset 69, Länge 16 Bytes (Byte 69–84)
- **Erwarteter Highlight:** `0C 0E 53 4D 2D 54 65 73 74 2D 50 4B 49 2D 44 45` (16 Bytes, Byte 69–84)
- **Problem:** Der Decoder meldete `offset=82, length=16` für den UTF8String, der korrekte Wert ist `offset=69, length=16` (13-Byte-Drift).
- **Ursache:** `readChildrenUntilEoc()` in `ASN1BerDecoder` berechnete `childAbsOffset` falsch:
  - Alte Formel: `childAbsOffset = valueAbsOffset + (pos - headerOffset)`
  - `pos` ist die **absolute Position** im Originaldaten-Array (nicht relativ zum valueOffset)
  - Die Subtraktion von `headerOffset` führte zu einer kumulativen Offset-Abweichung bei verschachtelten indefinite-length Strukturen
  - Bei Context[0] (Byte 13) → SEQUENCE (Byte 19): `15 + (19 - 2) = 32` statt `19`
- **Fix:** In `readChildrenUntilEoc()`: `childAbsOffset = pos` (direkt verwenden, da `pos` bereits absolut ist)

---

### Phase 8.4: Offset-Drift nach syncHexFromTree() ✅ ABGESCHLOSSEN

**Ziel:** Sicherstellen, dass die Byte-Offsets im TreeView auch nach dem Hex-Editor-Roundtrip (`syncHexFromTree()`) mit den im Hex-Editor angezeigten Bytes übereinstimmen.

#### Fehlerbeschreibung

- **Beobachtung:** Klick auf UTF8String ("SM-Test-PKI-DE") zeigt Highlight `03 55 04 0A 0C 0E 53 4D 2D 54 65 73 74 2D 50 4B` (16 Bytes, Byte 65–80)
- **Erwartet:** Highlight `0C 0E 53 4D 2D 54 65 73 74 2D 50 4B 49 2D 44 45` (Byte 69–84)
- **Ursache:** Nach dem Laden ruft `buildTreeView()` `syncHexFromTree()` auf, das den TreeView-Baum via `ASN1BerEncoder` zurück in Bytes kodiert.
- **Problem:** Der Encoder kodiert indefinite-length BER-Strukturen als definite-length → Byte-Offsets verschieben sich.
  - Original: Root-Header `30 80` (2 Bytes) → UTF8String bei **Offset 69**
  - Encodiert: Root-Header `30 82 06 5C` (4 Bytes) → UTF8String bei **Offset 73**
  - Differenz: **+4 Bytes** durch längeren Root-Header im encodierten Format
- **Folge:** TreeView zeigt Node mit `offset=69`, aber Hex-Editor zeigt encodierte Daten wo derselbe Node bei Offset 73 liegt → `setHighlightRange(69, 85)` markiert falsche Bytes.

#### Durchgeführte Korrektur

**Datei:** `MainController.java` — `syncHexFromTree()` und neue Methode `rebuildTreeViewWithEncodedRoot()`

| Vorher | Nachher |
|--------|--------|
| `syncHexFromTree()` ersetzt nur `currentHexBytes` | `syncHexFromTree()` ersetzt `currentHexBytes` + decodiert neu + ersetzt TreeView |
| TreeView-offsets blieben unverändert | TreeView-offsets werden mit `rebuildTreeViewWithEncodedRoot()` aktualisiert |

**Lösungsansatz:** Nach dem Encodieren des TreeViews wird die encodierte Byte-Array sofort mit `ASN1BerDecoder.decode()` decodiert und der TreeView damit ersetzt. Dadurch stimmen die TreeView-offsets mit den im Hex-Editor angezeigten Bytes überein.

#### Verifikation

- UTF8String-Knoten ("SM-Test-PKI-DE"): korrekte Bytes im Hex-Editor hervorgehoben
- Highlight stimmt mit den angezeigten Bytes überein
- Build: SUCCESS; Tests: **109 / 109** bestanden ✓

---

### Phase 8.5: Hex-Editor Interaktivität verbessern 🆕

**Ziel:** Den Hex-Editor nutzbare machen — jedes Hex-Zeichen soll direkt anklick- und editierbar sein, und der Cursor soll per Pfeiltasten navigieren + Bereiche selektieren.

#### Fehlerbeschreibung / Anforderungen

**Problem 1: Klick auf Hex-Zeichen funktioniert nicht zuverlässig**
- Der aktuelle Mouse-Click-Handler in `HexEditorControl.setupInputHandlers()` berechnet die Byte-Position über `col / 2` und `col % 2`.
- Da der Editor auf einem Canvas basiert (keine Text-Node pro Byte), ist die Click-Präzision begrenzt.
- Der Click-Handler landet nicht präzise auf jedem einzelnen Hex-Zeichen (0-9, A-F).
- Erwartet: Jedes der 2 Hex-Zeichen pro Byte ist einzeln anklickbar und editierbar.

**Problem 2: Keine Selektion per Cursor-Tasten**
- Pfeiltasten (↑, ↓, ←, →) bewegen den Cursor, selektieren aber keine Bereiche.
- Erwartet: Mit Shift + Pfeiltasten soll der Cursor den Hex-Editor navigieren und gleichzeitig einen selektierten Bereich aufbauen (wie in VS Code, IntelliJ, Sublime Text).
- Erwartet: Shift + Pfeiltasten selektiert einzelne Hex-Zeichen (nicht nur Bytes).

#### Lösungsweg

| # | Aufgabe | Beschreibung |
|---|---------|-------------|
| 1 | **Click-Präzision verbessern** | Mouse-Click-Handler soll auf jedes Hex-Zeichen einzeln treffen können. Statt `col / 2` muss die exakte X-Position jedes Zeichens berechnet werden. |
| 2 | **Selektion-Zustand hinzufügen** | `selectionStart`/`selectionEnd` Properties (optional: `selectionActive` Boolean) für Shift+Cursor-Selektion |
| 3 | **Shift+Pfeiltasten implementieren** | `SHIFT + LEFT/RIGHT` bewegt Cursor + selektiert. `SHIFT + UP/DOWN` bewegt Cursor zeilenweise + selektiert. |
| 4 | **Selektion visualisieren** | Selektierte Bytes im Hex-Editor farblich hervorheben (andere Farbe als TLV-Highlight) |
| 5 | **Selektion auf TreeView durchreichen** | Klick auf selektierten Bereich → TreeView-Auswahl (oder umgekehrt) |
| 6 | **Build + Tests** | `mvn clean test` — alle Tests müssen bestehen. |

#### Erwartetes Ergebnis

- **Klick:** Jedes Hex-Zeichen (0-9, A-F) ist einzeln mit der Maus anklickbar und editierbar
- **Selektion:** Shift + Pfeiltasten (↑, ↓, ←, →) selektieren Hex-Zeichen-Bereiche
- **Visual:** Selektierter Bereich wird farblich hervorgehoben
- **Build:** SUCCESS; Tests: alle bestanden

---

### Phase 8.5: Hex-Editor Interaktivität verbessern ✅ ABGESCHLOSSEN

**Ziel:** Den Hex-Editor nutzbare machen — jedes Hex-Zeichen soll direkt anklick- und editierbar sein, und der Cursor soll per Pfeiltasten navigieren + Bereiche selektieren.

#### Durchgeführte Korrektur

**Datei:** `HexEditorControl.java`

**Problem 1: Click-Präzision**
- Vorher: `col / 2` und `col % 2` gruppierte Click-Targets grob in 2er-Byte-Blöcken
- Nachher: Präzise X-Position-Berechnung pro Byte-Block (`BYTE_WIDTH`), dann relative Position innerhalb des Bytes (`HEX_ONLY_WIDTH / 2`)
- Ergebnis: Jedes der 2 Hex-Zeichen (High-Nibble, Low-Nibble) ist einzeln anklickbar

**Problem 2: Selektion per Shift+Pfeiltasten**
- Neue State-Felder: `selectionStart`, `selectionEnd`, `shiftDown`
- `renderSelection()`: Zeichnet Selektion blau (25% opacity)
- Shift-Tracking: `KEY_PRESSED`/`KEY_RELEASED` für SHIFT-Taste
- Pfeiltasten (↑, ↓, ←, →) + Shift: Selektion vom Cursor zur neuen Position
- HOME/END/PAGE_UP/PAGE_DOWN + Shift: Selektion über gesamte Zeilen/Bereiche
- Mouse-Click + Shift: Selektion vom Cursor zum Klick-Zeichen
- `clearSelection()`: Entfernt Selektion bei normaler Cursor-Bewegung

#### Verifikation

- Jedes Hex-Zeichen ist einzeln anklickbar und editierbar ✓
- Shift + Pfeiltasten selektieren Hex-Zeichen-Bereiche ✓
- Shift + HOME/END/PAGE_UP/DOWN selektieren große Bereiche ✓
- Selektion wird blau visualisiert (25% opacity) ✓
- Mouse-Click + Shift selektiert Bereich ✓
- Build: SUCCESS; Tests: **101 / 101** bestanden ✓
