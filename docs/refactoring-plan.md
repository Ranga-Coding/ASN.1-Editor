# Refactoring Plan

## Goal
Extend the editor so it remains a generic ASN.1/BER parser, displays RFC5280-relevant syntax more clearly, supports binary BER/DER editing through a Hex editor, expands the decoded tree by default, remembers the last file location, provides synchronized bidirectional editing between Hex editor and TreeView, and offers a clean UI with Details and Hex editor visible simultaneously.

## Scope
- Generic BER tree display improvements for RFC5280/X.509 structures.
- Binary BER/DER visualization and editing via Hex editor.
- TreeView expansion behavior.
- File chooser default directory persistence.
- VS Code/Maven/JavaFX launch configuration.

## Affected modules
- `com.asn1editor.parser.ASN1BerDecoder`
- `com.asn1editor.ui.MainController`
- `com.asn1editor.ui.Main`
- `com.asn1editor.io.ASN1FileIO`
- `com.asn1editor.service.ASN1Service`
- `run.bat`
- Parser/UI tests where practical.

## Risks
- Full RFC5280 ASN.1 schema decoding is extensive. Current step should stay generic and add safe RFC5280 annotations/OID names without hard-coding a brittle full certificate-only parser.
- `ASN1FileIO.readFile` reads bytes using ISO-8859-1 to preserve binary data. This is appropriate for CRMF/DER byte preservation, but non-ASCII UTF-8 text may not render exactly as before.

## Current progress
- Added editable Hex editor tab for binary files.
  - Displays 16 hex bytes per line.
  - Shows printable ASCII characters beside the hex column.
  - Uses `.` for non-printable bytes.
  - Applies edited hex by decoding bytes through `ASN1BerDecoder` and refreshing the TreeView.
- Updated VS Code project settings, tasks and launch configuration for Maven/JavaFX startup.
- Added RFC5280/X.509-friendly OID display names for common algorithms, DN attributes, extensions, EKUs and AIA identifiers while keeping the decoder generic.
- TreeView display now shows decoded primitive `value` children directly in the left tree. OIDs are displayed by their friendly name only (e.g. `sha256WithRSAEncryption`) instead of the raw `OBJECT IDENTIFIER = 1.2.840.113549.1.1.11 (sha256WithRSAEncryption)` line.
- Details view now lists leaf metadata (`kind`, `hex`, `value`, `oid`, etc.) for selected constructed primitive nodes.
- TreeView nodes are expanded recursively after loading.
- File chooser persists the last opened/saved directory via Java Preferences and uses it as the initial directory.
- File chooser now also includes `.cer`, `.crt` and `.pem`.
- Added startup loading via first command-line argument.
- Added fallback startup loading for `Test.crmf` when present in the working directory.
- Updated `run.bat` to pass `Test.crmf`.
- Added `.crmf`, `.der`, and `.ber` to the open-file filter.
- Added `ASN1Service.parse(String, Path)` so the log shows the real filename instead of `(null)`.
- Added BER indefinite-length support for constructed TLV values (`0x80` length) terminated by End-of-Contents (`00 00`).
- Added regression tests for definite/indefinite BER decoding behavior.
- Verified `Test.crmf` manually through `ASN1Service.parse(..., Path.of("Test.crmf"))`; it decodes to root `SEQUENCE [CONSTRUCTED]`.
- **Phase 8.2 Teil 1 (2026-07-12):** Removed "Rohdaten" tab from FXML; `rawArea` created programmatically in `initialize()`. Visible only for Plain-text ASN.1 and Base64→Text files; hidden for binary files.
- **Phase 8.2 Teil 2 (2026-07-12):** Replaced `TabPane` with `SplitPane` — Details and Hex editor now visible side-by-side. Divider at 0.25 (Details smaller, Hex editor larger).
- **Phase 8.2 Teil 3 (2026-07-12):** TreeView node selection highlights corresponding TLV bytes in Hex editor. `ASN1Node` record extended with `offset` and `length` fields. `ASN1BerDecoder` tracks byte positions during TLV decoding (including recursive children). Highlight overlay uses a transparent `Pane` with yellow `Rectangle` (25% opacity). Status bar shows byte range (e.g., "SEQUENCE (Byte 0-42)").
- **Phase 8.1 Teil 3 (2026-07-13):** BER/DER-Encoder `ASN1BerEncoder` implementiert — codiert ASN1Node-Baum zurück in byte[] (TLV-Format). Unterstützt alle UNIVERSAL-, APPLICATION-, CONTEXT-SPECIFIC-Tags. Längencodierung: kurz (<127) und lang (≥127), DER-konform (minimal). MainController: `syncHexFromTree()` für automatische Synchronisation TreeView → Hex-Editor. 18 Unit-Tests (Roundtrip für alle Typen).
- **Phase 8.1 Teil 4 (2026-07-13):** Change-Tracking implementiert — `hexDirty`-Flag für Hex-Editor-Änderungen. Dirty-Tracking-Listener in `initialize()` erkennt Änderungen am Hex-Editor-Text. `updateHexStatus()` aktualisiert Statusleiste: "synchronisiert" / "Änderungen nicht gespeichert". Dirty-Flag wird nach `handleApplyHex`, `handleReloadHexFromTree`, `syncHexFromTree` zurückgesetzt.
- **Phase 8.1 Teil 5 (2026-07-13):** Persistenz für Binärdateien — `isBinaryFile`-Flag erkennt BER/DER-Dateien. `saveToFile()` schreibt `currentHexBytes` bei Binärdateien, `rawArea.getText()` bei Plain-text ASN.1. `loadFile()` setzt `isBinaryFile` basierend auf Format-Erkennung.
- **Phase 8.1 Teil 6 (2026-07-13):** Debounce für automatische Hex → TreeView-Aktualisierung — `autoSyncTimer` (300ms) löst nach Inaktivität automatisch Hex → Bytes → BER/DER → TreeView aus. `scheduleAutoSync()` setzt Timer bei jeder Änderung zurück. Statusleiste: "automatisch synchronisiert" / "Fehler!".
- **Phase 8.1 Teil 7 (2026-07-13):** Manueller Override — `handleApplyHex()` und `handleReloadHexFromTree()` rufen `autoSyncTimer.cancel()` auf, um Debounce zu überspringen. Statusleiste: "Bytes — angewendet" / "Bytes — neu geladen".
- **Phase 8.2 Teil 4 (2026-07-19):** Statusleiste mit Knotenname und Byte-Position — `updateStatusLabel(ASN1Node node)` zeigt detailliert: `Ausgewählt: name = value (Byte X-Y)`. `cursorPosition` zeigt `Byte X/Y`. Bei Text-ASN.1: `Ausgewählt: name = value`.
- **TreeView-Auswahl → Byte-Highlight (2026-07-19):** Highlight direkt in `HexEditorControl` integriert — `setHighlightRange(start, end)` zeichnet gelben Bereich (25% opacity) im Canvas. `hexHighlightOverlay` (StackPane) entfernt. `clearHighlight()` setzt Highlight zurück. X-Position-Berechnung korrigiert: jedes Byte `i` beginnt bei `i * BYTE_WIDTH`. End-X: `(endByteInRow - 1) * BYTE_WIDTH + HEX_ONLY_WIDTH`. Duplicate `highlightBytes()`-Methode entfernt.
- **Decoder-Bug (2026-07-19):** `ASN1BerDecoder.readTlvWithOffset()` berechnete `lengthBytesBeforeValue` vor `readLength()`, dadurch fehlte das Length-Byte. TLV-Längen waren immer zu klein. **Fix:** `headerBytes = pos - startPos` nach `readLength()`. `valueAbsOffset` Korrektur: `absOffsetStart + headerBytes` für Children. Tests: `HighlightDebugTest` mit SEQUENCE/INTEGER, nested SEQUENCE.
- **Highlight-End-X-Bug (2026-07-19):** `renderHighlight()` berechnete `endByteInRow` als absolute Position (`Math.min(highlightEnd, rowEndByte)`), aber `startByteInRow` war relativ. **Fix:** `endByteInRow = Math.min(highlightEnd, rowEndByte) - rowStartByte`. Tests: `HighlightPositionTest` mit 7 Testfällen für Single-Row, Multi-Row, Partial-Range, Nested-Node Highlights. Build: SUCCESS; Tests: 85/85 bestanden.
- **Offset-Drift-Bug (2026-07-19) — Phase 8.3:** `readChildrenUntilEoc()` in `ASN1BerDecoder` berechnete `childAbsOffset` falsch: `valueAbsOffset + (pos - headerOffset)` statt direkt `pos`. Bei verschachtelten indefinite-length Strukturen (z.B. CRMF/TEST.crmf) führte dies zu kumulativer Offset-Abweichung (13 Bytes). **Fix:** `childAbsOffset = pos` (da `pos` bereits absolute Position im Originaldaten-Array ist). Unterscheidung zu `readChildren()`: Tauscht data-Array + pos=0 → `valueOffset + pos`. Tests: `Utf8StringHighlightTest` (2 Tests) — UTF8String offset=69/length=16 verifiziert. Build: SUCCESS; Tests: 103/103 bestanden.
- **syncHexFromTree-Offset-Drift (2026-07-19) — Phase 8.4:** Nach dem Laden ersetzt `syncHexFromTree()` den Hex-Editor mit encodierten Bytes (`ASN1BerEncoder.encode()`). Der Encoder kodiert indefinite-length als definite-length → Root-Header `30 80` (2B) wird zu `30 82 06 5C` (4B) → alle nachfolgenden Byte-Offsets verschieben sich um +4 Bytes. TreeView-offsets (69) passen nicht mehr zu Hex-Editor (73). **Fix:** `syncHexFromTree()` decodiert die encodierten Bytes neu (`ASN1BerDecoder.decode()`) und aktualisiert den TreeView mit `rebuildTreeViewWithEncodedRoot()` → TreeView-offsets stimmen mit Hex-Editor überein. Build: SUCCESS; Tests: 101/101 bestanden.
- **Hex-Editor Interaktivität (2026-07-19) — Phase 8.5:** Hex-Editor hatte zwei Probleme: (1) Mouse-Click landete nicht präzise auf jedem einzelnen Hex-Zeichen. (2) Pfeiltasten bewegten nur den Cursor ohne Selektion. **Fix:** (1) Präziser Click-Handler: `BYTE_WIDTH` für Byte-Position + `HEX_ONLY_WIDTH / 2` für High/Low-Nibble. (2) Shift-Tracking via KEY_PRESSED/KEY_RELEASED. (3) Pfeiltasten + Shift: Selektion vom Cursor zur neuen Position. (4) HOME/END/PAGE_UP/DOWN + Shift: Selektion über Zeilen. (5) `renderSelection()`: blaue Highlight (25% opacity) für Selektionsbereich. (6) `clearSelection()` bei normaler Cursor-Bewegung. Build: SUCCESS; Tests: 101/101 bestanden.

### VS Code Debugging Setup (2026-07-12)
- **Problem:** VS Code Java Extension cannot launch JavaFX applications with correct `--module-path` → `Fehler: JavaFX-Runtime-Komponenten fehlen`.
- **Solution:** App must be started via Maven (`mvn javafx:run` for normal launch, or direct `java` with `-agentlib:jdwp` for debugging).
- **Debug workflow (2 steps):**
  1. Run `.vscode/start-debug.bat` — starts JVM with `-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005`.
  2. Press F5 in VS Code → "ASN.1 Editor (Attach Debug)" → Debugger attaches, GUI opens, breakpoints work.
- **Key files:**
  - `.vscode/start-debug.bat`: Direct Java launch with debug agent.
  - `.vscode/launch.json`: Attach configuration for port 5005.
  - `.vscode/tasks.json`: `run: javafx` (normal), `run: javafx debug` (background start).
  - `pom.xml`: Added `exec-maven-plugin` for `mvn exec:java` with VM args.
- **Why this approach:** VS Code Java Extension does not support JavaFX module path natively; attach-to-debug-agent is the most reliable method.

## Next step
Refactor the UI to show Details and Hex editor side-by-side (replace TabPane with SplitPane), implement byte-range highlighting in the Hex editor when a TreeView node is selected, and ensure base64/PEM files are properly decoded before display in the Hex editor.
