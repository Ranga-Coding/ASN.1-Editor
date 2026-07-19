# Byte-Highlighting: Plan anhand eines konkreten Beispiels

## Test-ASN.1 Struktur (BER/DER kodiert)

```
MyModule DEFINITIONS ::= BEGIN
  MyRecord SEQUENCE {
    id          INTEGER,
    enabled     BOOLEAN,
    objectId    OBJECT IDENTIFIER,
    name        PrintableString,
    data        OCTET STRING
  }
  
  MySet SET {
    value       VisibleString
  }
END
```

## Kodierter Byte-Stream (16 Byte/Zeile)

```
Offset  Byte-Hex                    ASCII
------  --------------------------  ----------------
00      30 1A 31 02 01 01 01 01 FF  0.1....1..1.?.
0F      06 03 2A 86 48 13 06 48 65  ..*.H..He
1B      6C 6F 04 03 01 02 03
        LL..
```

**Zeile 0:** `30 1A 31 02 01 01 01 01 FF 06 03 2A 86 48 13 06`
**Zeile 1:** `48 65 6C 6F 04 03 01 02 03`

## TLV-Struktur (Byte-Offsets)

| Node | Name | Offset | Länge | Bytes | Beschreibung |
|------|------|--------|-------|-------|--------------|
| 0 | SEQUENCE | 0 | 28 (0x1C) | `30 1A [31 02 01 01 01 01 FF 06 03 2A 86 48 13 06 48 65 6C 6F 04 03 01 02 03]` | Root-Container |
| 1 | SEQUENCE | 3 | 25 (0x19) | `31 02 01 01 01 01 FF 06 03 2A 86 48 13 06 48 65 6C 6F 04 03 01 02 03` | Interne SEQUENCE |
| 2 | INTEGER | 5 | 3 | `02 01 01` | Wert: 1 |
| 3 | BOOLEAN | 8 | 3 | `01 01 FF` | Wert: TRUE |
| 4 | OID | 11 | 6 | `06 03 2A 86 48` | OID 1.2.840 |
| 5 | PrintableString | 17 | 10 | `13 06 48 65 6C 6F 04 03 01 02 03` | "Hello" |
| 6 | OCTET STRING | 28 | 5 | `04 03 01 02 03` | {01 02 03} |

*Hinweis: Dies ist ein vereinfachtes Beispiel. Die genauen Offsets hängen von der tatsächlichen Kodierung ab.*

## Highlight-Berechnung im Detail

### Beispiel 1: INTEGER (Offset=5, Länge=3, Bytes 5-7)

**Gewünschte Highlight-Bytes:** Byte 5, 6, 7

**renderHighlight() Logik:**
```java
highlightStart = 5, highlightEnd = 8  // exklusiv

// Berechnung der relevanten Zeilen:
lineStartRow = 5 / 16 = 0
lineEndRow = (8-1) / 16 = 7 / 16 = 0

// Für Row 0:
rowStartByte = 0 * 16 = 0
rowEndByte = min(0 + 16, 33) = 16

// Byte-Positionen relativ zur Zeile:
startByteInRow = max(0, 5 - 0) = 5
endByteInRow = min(8, 16) - 0 = 8

// X-Positionen:
BYTE_WIDTH = 2*9 + 9 = 27  (2 Hex-Zeichen + Leerzeichen)
HEX_ONLY_WIDTH = 2*9 = 18

startX = 5 * 27 = 135
endX = (8-1) * 27 + 18 = 7*27 + 18 = 189 + 18 = 207
width = 207 - 135 = 72

// Erwarteter Bereich: 3 Bytes × 27 - 9 = 72 (letztes Byte kein trailing space)
// Korrekt!
```

**X-Positions-Verifikation mit render()-Loop:**
```java
// render() für Row 0:
// i=0: x=0    → Byte 0
// i=1: x=27   → Byte 1
// i=2: x=54   → Byte 2
// i=3: x=81   → Byte 3
// i=4: x=108  → Byte 4
// i=5: x=135  → Byte 5 ← Highlight startet hier ✓
// i=6: x=162  → Byte 6
// i=7: x=189  → Byte 7 ← Highlight endet nach diesem Byte ✓
```

**Byte 7 endX = 189 + 18 = 207** → Correct! Das ist das rechte Ende von Byte 7.

---

### Beispiel 2: OID (Offset=11, Länge=6, Bytes 11-16)

**Gewünschte Highlight-Bytes:** Byte 11, 12, 13, 14, 15, 16

```java
highlightStart = 11, highlightEnd = 17

// Row 0:
rowStartByte = 0
rowEndByte = 16
startByteInRow = 11 - 0 = 11
endByteInRow = min(17, 16) - 0 = 16

startX = 11 * 27 = 297
endX = (16-1) * 27 + 18 = 15*27 + 18 = 405 + 18 = 423
width = 423 - 297 = 126

// Check: 5 Bytes (11-15) × 27 - 9 = 126 ✓ (Byte 16 ist in Zeile 1!)
```

**Problem!** highlightEnd=17, aber rowEndByte=16 (max Byte in Zeile 0).
endByteInRow = min(17, 16) = 16. Das heisst nur Bytes 11-15 werden in Zeile 0 highlighted, nicht Byte 16!

**Aber:** lineEndRow = (17-1)/16 = 16/16 = 1. Also gibt es auch Row 1!

```java
// Row 1:
rowStartByte = 16
rowEndByte = min(16 + 16, 33) = 32
startByteInRow = max(0, 11 - 16) = 0
endByteInRow = min(17, 32) - 16 = 1

startX = 0 * 27 = 0
endX = (1-1) * 27 + 18 = 0 + 18 = 18
width = 18

// Row 1 highlight: nur Byte 16 (1 Byte × 18 Pixel) ✓
```

**Gesamt-Highlight für OID:**
- Row 0: Bytes 11-15, X=[297, 423]
- Row 1: Byte 16, X=[0, 18]

---

### Beispiel 3: PrintableString (Offset=17, Länge=10, Bytes 17-26)

**Gewünschte Highlight-Bytes:** Byte 17 bis 26 (10 Bytes)

```java
highlightStart = 17, highlightEnd = 27

// Berechnung der relevanten Zeilen:
lineStartRow = 17 / 16 = 1
lineEndRow = (27-1) / 16 = 26 / 16 = 1

// Nur Row 1 (kein Zeilenumbruch innerhalb des Highlights)
```

**Row 1:**
```java
rowStartByte = 16
rowEndByte = min(16 + 16, 33) = 32

startByteInRow = max(0, 17 - 16) = 1
endByteInRow = min(27, 32) - 16 = 11

startX = 1 * 27 = 27
endX = (11-1) * 27 + 18 = 10*27 + 18 = 270 + 18 = 288
width = 288 - 27 = 261

// Check: 10 Bytes × 27 - 9 = 261 ✓
```

**Bytes in Row 1:**
```
Offset  Byte  Highlight?
------  ----  ----------
 16     48    Nein (vor dem Highlight)
 17     65    JA ← startByteInRow=1, startX=27
 18     6C    JA
 19     6F    JA
 20     04    JA
 21     03    JA
 22     01    JA
 23     02    JA
 24     03    JA
 25     --    JA
 26     --    JA ← endByteInRow=11, endX=288
 27     --    Nein (nach dem Highlight)
```

---

## Algorithmus-Zusammenfassung

### renderHighlight() Berechnungsschritte:

1. **Zeilen-Bereich bestimmen:**
   ```java
   lineStartRow = highlightStart / BYTES_PER_ROW
   lineEndRow = (highlightEnd - 1) / BYTES_PER_ROW
   ```

2. **Für jede Zeile:**
   ```java
   rowStartByte = row * BYTES_PER_ROW
   rowEndByte = min(rowStartByte + BYTES_PER_ROW, data.length)
   
   // Relative Byte-Positionen innerhalb der Zeile
   startByteInRow = max(0, highlightStart - rowStartByte)
   endByteInRow = min(highlightEnd, rowEndByte) - rowStartByte
   
   // X-Positionen (Pixel)
   startX = startByteInRow * BYTE_WIDTH
   endX = (endByteInRow - 1) * BYTE_WIDTH + HEX_ONLY_WIDTH
   highlightWidth = endX - startX
   ```

3. **Konstanten:**
   - `BYTES_PER_ROW = 16`
   - `BYTE_WIDTH = 27` (2 Hex-Zeichen × 9px + 1 Leerzeichen × 9px)
   - `HEX_ONLY_WIDTH = 18` (2 Hex-Zeichen × 9px)

---

## Wichtige Punkte

1. **`highlightEnd` ist EXKLUSIV** (wie in Java-SubList): Bytes [start, end)
2. **`endByteInRow` ist RELATIV zur Zeile**, nicht absolut
3. **`endX`** berechnet das rechte Pixel-Ende des **letzten** Bytes in der Zeile
4. **Mehrzeilige Highlights** werden in mehreren Canvas-Rechtecken gezeichnet
5. **Keine Lücke** zwischen den Zeilen-Rechtecken, da:
   - Zeile 0 endet bei `rowEndByte=16`, `endByteInRow = 16-0 = 16`
   - Zeile 1 beginnt bei `rowStartByte=16`, `startByteInRow = highlightStart-16`

---

## Offset-Berechnung im Decoder

### `readChildren()` vs. `readChildrenUntilEoc()`

Beide Methoden decodieren Kind-TLVs, verwenden aber unterschiedliche Offset-Strategien:

| Methode | Daten-Kontext | pos-Start | Formel |
|---------|--------------|-----------|--------|
| `readChildren()` | data-Array getauscht (Value des Parents) | 0 | `childAbsOffset = valueOffset + pos` |
| `readChildrenUntilEoc()` | Derselbe data-Array wie Parent | aktuell (absolut) | `childAbsOffset = pos` |

### Bekannte Korrektur (Phase 8.3)

Der Bug in `readChildrenUntilEoc()` führte bei `TEST.crmf` zu `offset=82` statt `offset=69`:

- **Ursache:** `valueAbsOffset + (pos - headerOffset)` subtrahierte `headerOffset` doppelt
- **Fix:** Direkte Verwendung von `pos` als absoluten Offset
- **Prüfsumme:** Byte 69 = `0x0C` (UTF8String-Tag), Byte 70 = `0x0E` (Length), Bytes 71-84 = Value

### Decoder-Test (TEST.crmf)

```
UTF8String "SM-Test-PKI-DE":
  Tag (Byte 69)   = 0x0C
  Length (Byte 70) = 0x0E (14)
  Value (Byte 71-84) = "SM-Test-PKI-DE"
  TLV-Länge = 16 Bytes
  Highlight-Range = [69, 85) (end exklusiv)
```

### syncHexFromTree Offset-Drift (Phase 8.4)

Nach dem Laden ersetzt `syncHexFromTree()` den Hex-Editor mit encodierten Bytes.
Der Encoder wandelt indefinite-length → definite-length um, was Byte-Offsets verschiebt:

| Zustand | Root-Header | UTF8String-Offset | Ursache |
|---------|------------|-------------------|---------|
| Original | `30 80` (2 Bytes) | 69 | Indefinite length |
| Encodiert | `30 82 06 5C` (4 Bytes) | 73 | Definite length |

**Fix:** `syncHexFromTree()` decodiert encodierte Bytes neu und aktualisiert TreeView → offsets stimmen überein.

### Selektion im Hex-Editor (Phase 8.5)

Ab Phase 8.5 unterstützt der Hex-Editor eine Benutzer-Selektion:

| Aktion | Effekt |
|--------|--------|
| Mausklick auf Hex-Zeichen | Cursor auf dieses Zeichen (präzise pro Nibble) |
| Shift + Mausklick | Selektion vom Cursor zum Klick-Zeichen |
| Shift + ←/→ | Selektion pro Hex-Zeichen |
| Shift + ↑/↓ | Selektion zeilenweise |
| Shift + HOME/END | Selektion zum Zeilenanfang/-ende |
| Shift + PAGE_UP/DOWN | Selektion um 8 Zeilen |
| Normale Pfeiltasten | Cursor bewegen, Selektion löschen |
- Selektion wird **blau** (25% opacity) visualisiert
- TLV-Highlight bleibt **gelb** (25% opacity)
- Selektion und TLV-Highlight überlappen sich visuell
| Original | `30 80` (2 Bytes) | 69 | Indefinite length |
| Encodiert | `30 82 06 5C` (4 Bytes) | 73 | Definite length |

**Fix:** `syncHexFromTree()` decodiert encodierte Bytes neu und aktualisiert TreeView → offsets stimmen überein.

## Test-Struktur für Verifikation

Erstelle einen Test mit folgender Struktur:

```
Byte 0-2:   SEQUENCE Header (30 01 02)
Byte 3-5:   INTEGER (02 01 42)
Byte 6-7:   SET Header (31 01 02)
Byte 8-10:  BOOLEAN (01 01 FF)
Byte 11-16: OID (06 03 2A 86 48)
Byte 17-20: PrintableString (13 03 48 65 6C)
Byte 21-23: OCTET STRING (04 03 01 02 03)
```

Für jeden Node prüfen:
- [ ] `offset()` stimmt mit erwarteter Startposition überein
- [ ] `length()` stimmt mit erwarteter Länge überein
- [ ] `renderHighlight()` berechnet korrekte X-Positionen
- [ ] X-Positionen stimmen mit `render()` Byte-Positions-Berechnung überein
- [ ] Keine Lücken zwischen aufeinanderfolgenden Nodes
- [ ] Mehrzeilige Highlights werden korrekt berechnet
- [ ] `childAbsOffset`-Berechnung in `readChildren()` und `readChildrenUntilEoc()` korrekt
