# Dokumentation: ASN.1 Parser Architektur

Diese Dokumentation beschreibt die Implementierungsdetails des Parsers für die Phase 2 des Projekts.

## Komponenten-Beschreibung

### 1. Lexer (`ASN1Lexer`)
Der Lexer ist die erste Stufe der Verarbeitung. Er nimmt den rohen String-Input entgegen und führt eine Zerlegung (Tokenisierung) durch.
- **Eingabe:** `String` (Inhalt der ASN.1 Datei)
- **Ausgabe:** `List<Token>`
- **Logik:** Der Lexer scannt den Text Zeichen für Zeichen. Er erkennt Schlüsselwörter, Bezeichner, Operatoren und Literale. Wichtig ist, dass der Lexer die Position (Zeile/Spalte) jedes Tokens speichert, um Fehlermeldungen später präzise zu machen.

### 2. Token (`Token` Record)
Ein Token ist die kleinste Informationseinheit des Lexers.
- **Struktur:**
  - `TokenType type`: Der Typ des Tokens (z.B. `IDENTIFIER`, `ASSIGN`, `LBRACE`).
  - `String value`: Der eigentliche Textwert des Tokens.
  - `int line`: Die Zeilennummer im Originaltext.
  - `int column`: Die Spaltenposition im Originaltext.

### 3. Parser (`ASN1Parser`)
Der Parser übernimmt die strukturelle Analyse. Er verwendet das **Recursive Descent** Verfahren.
- **Eingabe:** `List<Token>` (vom Lexer)
- **Ausgabe:** `ASN1Document` (oder eine Exception)
- **Logik:** Der Parser folgt der Grammatik von ASN.1. Für jedes Konstrukt (z.B. ein Block) hat der Parser eine entsprechende Methode. Wenn ein Block gefunden wird, ruft der Parser sich selbst rekursiv auf, um die Kinder des Knotens zu sammeln. Am Ende wird ein `ASN1Node.internal(...)` erstellt.

### 4. Exception (`ASN1ParseException`)
Wenn der Parser auf ein Token stößt, das nicht zur Grammatik passt, wird diese Exception geworfen.
- **Eigenschaften:** Sie enthält eine aussagekräftige Fehlermeldung sowie die Position (`line`, `column`), damit die GUI den Fehler exakt markieren kann.

### 5. BER/DER-Decoder (`ASN1BerDecoder`)
Der BER/DER-Decoder verarbeitet binäre ASN.1-Daten.
- **Eingabe:** `byte[]` aus DER/BER/CRMF/PEM-decodierten Daten
- **Ausgabe:** `ASN1Node` bzw. über den Service ein `ASN1Document`
- **Logik:** Liest Tag-Length-Value-Strukturen rekursiv und baut daraus einen generischen ASN.1-Baum.
- **Unterstützung:** Primitive und konstruktive Tags, kurze/lange Längenform, indefinite length für konstruktive BER-Strukturen.
- **Anzeige:** Bekannte OIDs werden mit sprechenden Namen ergänzt, ohne den Decoder an ein festes Schema wie RFC5280 zu koppeln.

#### Offset-Tracking für Byte-Highlighting

Jeder `ASN1Node` enthält `offset` (Start-Byte) und `length` (Byte-Länge) für die exakte Hex-Editor-Markierung.

**Berechnung im Decoder:**

| Methode | Beschreibung |
|---------|-------------|
| `readTlvWithOffset()` | Berechnet `headerBytes = pos - startPos` **nach** `readLength()`, dann `valueAbsOffset = absOffsetStart + headerBytes` |
| `readChildren()` | Für definite Length: `childAbsOffset = valueOffset + pos` (pos startet bei 0 im swapeten data-Array) |
| `readChildrenUntilEoc()` | Für indefinite Length: `childAbsOffset = pos` (pos ist bereits absolute Position im Originaldaten-Array) |

**Wichtig:** `readChildren()` und `readChildrenUntilEoc()` unterscheiden sich im Offset-Verfahren:

- `readChildren()`: Tauscht `data`-Array aus, `pos` beginnt bei 0 → `childAbsOffset = valueOffset + pos`
- `readChildrenUntilEoc()`: Verwendet denselben `data`-Array → `childAbsOffset = pos` (bereits absolut)

**Beispiel: TLV-Offsets in `TEST.crmf`**

Für den UTF8String mit Value "SM-Test-PKI-DE":

| Byte-Offset | Hex | Bedeutung |
|-------------|-----|-----------|
| 69 | `0x0C` | Tag: UTF8String |
| 70 | `0x0E` | Länge: 14 |
| 71–84 | `53 4D 2D 54 65 73 74 2D 50 4B 49 2D 44 45` | Value: "SM-Test-PKI-DE" |

Node: `offset=69, length=16` (Tag + Length + Value).

**Bekannte Korrektur (Phase 8.3):**

Bug in `readChildrenUntilEoc()`: Die Formel `childAbsOffset = valueAbsOffset + (pos - headerOffset)` führte bei verschachtelten indefinite-length Strukturen zu falschen Offsets. Gefixt zu `childAbsOffset = pos`.

### 5a. BER/DER-Encoder (`ASN1BerEncoder`)
Der BER/DER-Encoder ist das Gegenstück zum Decoder und codiert einen `ASN1Node`-Baum zurück in einen binären BER/DER-Stream.
- **Eingabe:** `ASN1Node` (ein einzelner Baum-Wurzelknoten)
- **Ausgabe:** `byte[]` (TLV-Format)
- **Logik:** Rekursive Codierung aller Knoten als TLV-Strukturen.
  - Konstruktive Typen (SEQUENCE, SET): Kinder rekursiv codieren → Gesamtlänge bestimmen → TLV-wrap.
  - Primitive Typen: Hex-Wert aus `node.hex()` decodieren → TLV-wrap.
  - NULL: Leere Value-Bytes.
  - OIDs: Hex-Wert direkt verwenden (bereits BER-kodiert).
- **Längencodierung:** Kurz (<127) und lang (≥127), DER-konform (minimal).
- **Tag-Encoding:** UNIVERSAL (0x00-0x1F), APPLICATION (0x40+), CONTEXT-SPECIFIC (0x80+), PRIVATE (0xC0+).
- **Verwendung:** `MainController.syncHexFromTree()` — automatische Synchronisation von TreeView → Hex-Editor.

### 6. Service (`ASN1Service`)
Der Service dient als Brücke zwischen der Logik und der Benutzeroberfläche.
- **Verantwortung:** Er kapselt Textparser, Format-Erkennung, Base64/PEM-Dekodierung und BER/DER-Dekodierung. Die UI ruft `asn1Service.parse(text)` bzw. `asn1Service.parse(text, path)` auf.
- **Fehlerbehandlung:** Der Service fängt die `ASN1ParseException` ab oder reicht sie so weiter, dass die UI sie in einem Dialog oder direkt im Editor anzeigen kann.
- **Dateiformat-Erkennung:** `detectFormat()` erkennt Plain-text ASN.1, Base64/PEM, und Binär (DER/BER).
- **Base64-Dekodierung:** `decodeBase64IfNeeded()` decodiert rohen Base64-Content und PEM-formatierte Dateien.
- **Hex-Konvertierung:** `toHexString(byte[])` und `fromHexString(String)` unterstützen die Binär-/Hex-Bearbeitung.

### 7. GUI Hex-Editor (`MainController`)
Der Hex-Editor ist Teil der JavaFX-Oberfläche.
- **Darstellung:** 16 Hex-Bytes pro Zeile, rechts daneben ASCII-Spalte.
- **Bearbeitung:** Beim Anwenden wird nur die linke Hex-Spalte ausgewertet; die ASCII-Spalte dient der Orientierung.
- **Aktualisierung:** Geänderte Bytes werden über `ASN1BerDecoder` neu decodiert und im TreeView angezeigt.
- **Bidirektionale Synchronisation:** TreeView-Änderungen werden über `ASN1BerEncoder` zurück in Hex-Bytes codiert und im Hex-Editor aktualisiert.

### 8. Logger (`ASN1Logger`)
Schreibt Logeinträge in `asn1-editor.log`.
- **Inhalt:** Datei geladen (Format, Größe), Base64-Dekodierung, Parsing-Erfolg/Fehler, Hex-Snippets von Binärdateien
- **Ausgabe:** Datei + Warnungen/Errors auf `System.err`

## Unterstützte Dateiformate

### 1. Plain-text ASN.1 (BER-Textformat)
Klartext ASN.1, wie es in RFC 2252/2279 beschrieben ist:
```
MySequence ::= SEQUENCE {
    id INTEGER
    name CHOICE { null }
}
```

### 2. Base64-kodierte ASN.1
#### PEM-Format
```
-----BEGIN CERTIFICATE-----
MIIBkTCB+wIJALRiMLAh080LMA0GCSqGSIb3DQEBBQUAMBkxFzAVBgNVBAMTDkxv
-----END CERTIFICATE-----
```
- PEM-Header (`-----BEGIN ...-----`) und Footer (`-----END ...-----`) werden erkannt
- Base64-Content zwischen Header/Footer extrahiert und decodiert
- Newlines im Base64-Content werden ignoriert

#### Roh-Base64
```
U0FtcGxlVmFsdWUgOj0gU0VRVUVOQ0UgewogIG5hbWUgQ0hPSUNFIHsgbnVsbCAgfQogIGlkIElOVEVHRVIKfQ==
```
- Enthält nur Base64-Zeichen `[A-Za-z0-9+/=]`
- Kein ASN.1 `::=`-Operator vorhanden
- Wird direkt Base64-decodiert

### 3. Binäre ASN.1 (DER/BER)
Binärdateien, die keine lesbaren ASCII-Zeichen enthalten.
- Erkennung: Null-Bytes (`\0`) oder hohe Bytes (`>= 128`)
- Wird byte-erhaltend gelesen und über `ASN1BerDecoder` als BER/DER-TLV-Struktur decodiert
- Die GUI zeigt die Struktur links im TreeView an
- Zusätzlich steht ein Hex-Editor zur Verfügung:
  - 16 Hex-Bytes pro Zeile
  - rechts daneben ASCII-Darstellung
  - nicht druckbare Zeichen werden als `.` angezeigt
  - beim Anwenden werden die Hex-Bytes wieder zu Binärdaten konvertiert, BER/DER-decodiert und im TreeView angezeigt

## Datenfluss

### Plain-text ASN.1
```
Input String → ASN1Lexer → List<Token> → ASN1Parser → ASN1Document → ASN1Service → UI
```

### Base64/PEM-kodierte ASN.1
```
Input String → detectFormat(BASE64)
             → extractPemContent() (optional, bei PEM)
             → decodeBase64()
             → ASN1Lexer → List<Token> → ASN1Parser → ASN1Document → ASN1Service → UI
```

### Binäre ASN.1 (DER/BER)
```
Input String/Bytes → detectFormat(BINARY)
                   → byte-erhaltende Verarbeitung (ISO-8859-1)
                   → ASN1BerDecoder → ASN1Document → TreeView
                   → Hex-Editor-Anzeige (16 Bytes/Zeile + ASCII-Spalte)
                   → Node offset/length → Byte-Highlighting im Hex-Editor
```

### Hex-Editor Bearbeitungsfluss
```
Hex-Editor Text → linke Hex-Spalte extrahieren
                → Bytes erzeugen
                → ASN1BerDecoder
                → ASN1Document
                → TreeView aktualisieren
```

## Base64-Erkennung (`ASN1Service.decodeBase64IfNeeded`)

Die Heuristik prüft in dieser Reihenfolge:

1. **PEM-Format**: Enthält `-----BEGIN` und `-----END` → Base64-Content zwischen Header/Footer extrahiert
2. **Nur Base64-Zeichen**: Der gesamte Inhalt (ohne Whitespace) muss aus `[A-Za-z0-9+/=]` bestehen
3. **Kein ASN.1 `::=`-Operator**: Ein reiner Base64-Inhalt enthält keine ASN.1-Definitionen

Wenn alle Kriterien erfüllt sind, wird der Inhalt mit `Base64.getDecoder().decode()` decodiert.
Whitespace/Zeilenumbrüche werden vor dem Decodieren entfernt.
Bei ungültiger Base64-Daten oder wenn der `::=`-Operator vorhanden ist, wird der Originaltext unverändert zurückgegeben.

## Dateiformat-Erkennung (`ASN1Service.detectFormat`)

Die Heuristik prüft in dieser Reihenfolge:

| Priorität | Kriterium | Ergebnis |
|-----------|-----------|----------|
| 1 | `-----BEGIN ...-----` und `-----END ...-----` vorhanden | `BASE64` |
| 2 | `::=` vorhanden | `ASN1_TEXT` |
| 3 | Nur Base64-Zeichen + kein `::=` | `BASE64` |
| 4 | Null-Bytes oder hohe Bytes (>= 128) im Input | `BINARY` |
| 5 | Sonst | `ASN1_TEXT` |

## Logging (`asn1-editor.log`)

Jede parse-Operation wird protokolliert:

```
[2026-07-10 17:11:31.455] [INFO]  Datei geladen: test.pem               Format: BASE64     Größe: 99 bytes
[2026-07-10 17:11:31.456] [INFO]  Base64 decodiert: 99 → 33 Zeichen
[2026-07-10 17:11:31.456] [INFO]  Parsing erfolgreich: Einzelne Definition: type:MySequence
```

Fehler werden als `[WARN]` oder `[ERROR]` protokolliert und auch auf `System.err` ausgegeben.
