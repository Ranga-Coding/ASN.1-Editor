# Refactoring Tasks

> Erstellte am: 2026-07-19
> Status: Geplant

## Übersicht

Diese Datei dokumentiert alle geplanten Refactoring-Aufgaben für den ASN.1 Editor.
Aufgaben sind nach Priorität geordnet: **P0** (sofort) > **P1** (wichtig) > **P2** (nice-to-have).
Code-Duplikation hat Vorrang vor anderen Aufgaben.

---

## P0 — Sofort (hoher Impact, geringer Aufwand)

### T0-1: Duplizierte `bytesToHex()` Methoden zusammenfassen

**Status:** 📋 Geplant
**Priorität:** 🔴 Hoch

**Problem:**
Es gibt 4 Implementierungen von Byte-Array → Hex-String Konvertierung:

| Methode | Datei | Format |
| --------- | ------- | -------- |
| `ASN1Service.toHexString()` | `service/ASN1Service.java` | `"30 80 06"` (Leerzeichen) |
| `ASN1Service.bytesToHex()` | `service/ASN1Service.java` | `"308006"` (kompakt) |
| `ASN1BerDecoder.bytesToHex()` | `parser/ASN1BerDecoder.java` | `"308006"` (kompakt) |
| `MainController.formatHexEditor()` | `ui/MainController.java` | `"30 80 06  "` (zeilenbasiert) |

**Lösung:**

1. Neue Klasse `com.asn1editor.util.HexUtils` erstellen (oder in `service/` ablegen)
2. Zwei statische Methoden:

   ```java
   HexUtils.toHexString(byte[] bytes)   // "30 80 06" (Leerzeichen getrennt)
   HexUtils.toCompactHex(byte[] bytes)  // "308006" (kompakt)
   ```

3. Alle Aufrufstellen aktualisieren:
   - `ASN1BerDecoder.bytesToHex()` → `HexUtils.toCompactHex()`
   - `ASN1Service.bytesToHex()` → `HexUtils.toCompactHex()`
   - `MainController.formatHexEditor()` → `HexUtils.toHexString()`
   - `ASN1Service.toHexString()` kann auf `HexUtils.toHexString()` delegieren

**Betroffene Dateien:**

- Neu: `com.asn1editor.util.HexUtils` (oder `com.asn1editor.service.HexUtils`)
- `src/main/java/com/asn1editor/service/ASN1Service.java`
- `src/main/java/com/asn1editor/parser/ASN1BerDecoder.java`
- `src/main/java/com/asn1editor/ui/MainController.java`

**Test:** `mvn clean test` — alle 101 Tests müssen bestehen.

---

### T0-2: Geteilten `TagInfo`-Record auslagern

**Status:** 📋 Geplant
**Priorität:** 🔴 Hoch

**Problem:**
`TagInfo` ist in zwei Dateien fast identisch definiert:

| Datei | Struktur |
|-------|----------|
| `ASN1BerDecoder.java` | `(int number, String name, boolean constructed, int tagClass)` |
| `ASN1BerEncoder.java` | `(int tagClass, int tagNumber, boolean constructed)` |

Beide haben die gleiche Semantik, aber unterschiedliche Feldnamen und Reihenfolge.

**Lösung:**

1. Neuen öffentlichen Record `com.asn1editor.parser.TagInfo` erstellen:

   ```java
   public record TagInfo(int tagClass, int tagNumber, boolean constructed, String name)
   ```

2. `ASN1BerDecoder.TagInfo` durch diesen ersetzen
3. `ASN1BerEncoder.TagInfo` durch diesen ersetzen
4. `ASN1BerDecoder.universalTagName()` anpassen (TagInfo enthält jetzt Namen)

**Betroffene Dateien:**

- Neu: `com.asn1editor.parser.TagInfo`
- `src/main/java/com/asn1editor/parser/ASN1BerDecoder.java`
- `src/main/java/com/asn1editor/parser/ASN1BerEncoder.java`

**Test:** `mvn clean test` — alle 101 Tests müssen bestehen.

---

### T0-3: `ASN1Logger` Log-Datei konfigurierbar machen

**Status:** 📋 Geplant
**Priorität:** 🔴 Hoch

**Problem:**

- Log-Datei ist immer `asn1-editor.log` im Projektverzeichnis
- Aktuell: **694 KB** (wird weiter wachsen)
- Keine Log-Rotation
- Kein konfigurierbarer Pfad

**Lösung:**

1. Log-Datei in User-Home verschieben: `~/.asn1-editor/asn1-editor.log`
2. Optional: konfigurierbarer Pfad über System Property oder Config-File
3. Log-Rotation: max 10 MB pro Datei, max 5 Dateien
4. `ASN1Logger` wird thread-safe durch `synchronized` (bereits vorhanden)

**Betroffene Dateien:**

- `src/main/java/com/asn1editor/service/ASN1Logger.java`

**Test:** Keine direkten Tests für Logger. Manuell prüfen: Log erscheint im neuen Verzeichnis.

---

## P1 — Wichtig (mittlerer Impact, mittlerer Aufwand)

### T1-1: `ASN1BerDecoder.valueToInt()` korrigieren

**Status:** 📋 Geplant
**Priorität:** 🟡 Mittel

**Problem:**
`valueToInt()` in `ASN1BerDecoder.java:474` dekodiert Bytes manuell als `int`:

```java
int result = 0;
for (byte b : value) {
    result = (result << 8) | (b & 0xFF);
}
```

- Falsch für negative integers (Zweierkomplement > 2^31-1)
- Falsch für große positive integers (> 2^31-1)
- BER-Integer können beliebige Länge haben

**Lösung:**

```java
private static int valueToInt(byte[] value) {
    if (value.length == 0) return 0;
    if (value.length == 1) return value[0] & 0xFF;
    // BigInteger für korrekte Behandlung
    return new java.math.BigInteger(value).intValue();
}
```

Alternativ: `BigInteger` zurückgeben und Upstream anpassen.

**Betroffene Dateien:**

- `src/main/java/com/asn1editor/parser/ASN1BerDecoder.java`

**Test:** Vorhandene Tests prüfen. Optional: Test für große/negative Integers hinzufügen.

---

### T1-2: `MainController` aufteilen

**Status:** 📋 Geplant
**Priorität:** 🟡 Mittel

**Problem:**
`MainController` ist **~750 Zeilen** und enthält zu viele Verantwortlichkeiten:

- UI-Event-Handler (handleOpen, handleSave, etc.)
- Hex-Editor-Logik (parseHexEditorText, formatHexEditor, isHexEditablePosition)
- Datei-IO (loadFile, saveToFile, confirmSaveAfterValidation)
- Format-Detection (detectFormat Aufrufe)
- TreeView-Build (buildTreeView, buildTreeItem, expandTree)
- Hex-Sync (syncHexFromTree, rebuildTreeViewWithEncodedRoot)
- Status-Management (updateStatusLabel, updateHexStatus, clearHighlights)

**Lösung:**
In folgende Klassen aufteilen:

| Klasse | Verantwortung |
| -------- | --------------- |
| `MainController` (aktuell) | Koordiniert alles, bleibt als "Glue" |
| `FileLoader` | loadFile, saveToFile, confirmSaveAfterValidation |
| `TreeBuilder` | buildTreeView, buildTreeItem, expandTree, getTreeViewDocument |
| `HexSyncController` | syncHexFromTree, rebuildTreeViewWithEncodedRoot, scheduleAutoSync |
| `StatusManager` | updateStatusLabel, updateHexStatus, clearHighlights |

**Betroffene Dateien:**

- `src/main/java/com/asn1editor/ui/MainController.java`
- Neu: `com.asn1editor.ui.FileLoader`
- Neu: `com.asn1editor.ui.TreeBuilder`
- Neu: `com.asn1editor.ui.HexSyncController`
- Neu: `com.asn1editor.ui.StatusManager`

**Test:** `mvn clean test` — alle 101 Tests müssen bestehen.

---

### T1-3: `RFC5280_OID_NAMES` auslagern

**Status:** 📋 Geplant
**Priorität:** 🟡 Mittel

**Problem:**

- 120+ OIDs als `Map.ofEntries(...)` direkt im Code
- Schwer wartbar, schwer erweiterbar
- `ASN1BerDecoder.java` wird dadurch sehr lang

**Lösung:**

1. OID-Namen in externe Datei auslagern: `resources/oid-names.properties`

   ```properties
   2.5.4.10=organizationName (X.520 DN component)
   1.2.840.113549.1.1.11=sha256WithRSAEncryption
   ```

2. `ASN1BerDecoder` lädt OIDs zur Initialisierung
3. Optional: Hot-reload bei Dateiänderung

**Betroffene Dateien:**

- Neu: `src/main/resources/oid-names.properties`
- `src/main/java/com/asn1editor/parser/ASN1BerDecoder.java`

**Test:** `mvn clean test` — alle 101 Tests müssen bestehen.

---

### T1-4: `ASN1Service.decodeBase64IfNeeded()` JavaDoc zusammenführen

**Status:** 📋 Geplant
**Priorität:** 🟢 Gering

**Problem:**
Doppelte JavaDoc-Blöcke auf Zeilen 274-292.

**Lösung:**
Zwei JavaDoc-Blöcke zu einem zusammenführen.

**Betroffene Dateien:**

- `src/main/java/com/asn1editor/service/ASN1Service.java`

**Test:** Keine Verhaltensänderung.

---

### T1-5: `ASN1Service.bytesToHex()` / `toHexString()` Benennung klarer machen

**Status:** 📋 Geplant
**Priorität:** 🟢 Gering

**Problem:**
Zwei Methoden mit ähnlicher Funktion, unterschiedlicher Benennung:

- `toHexString(byte[])` — mit Leerzeichen
- `bytesToHex(byte[])` — kompakt

**Lösung:**

```java
// Benennung konsistent machen
public static String toHexString(byte[] bytes)      // mit Leerzeichen (public)
public static String toCompactHex(byte[] bytes)     // kompakt (static, benannt)
```

**Betroffene Dateien:**

- `src/main/java/com/asn1editor/service/ASN1Service.java`

**Test:** `mvn clean test` — alle 101 Tests müssen bestehen.

---

## P2 — Nice-to-have (geringer Impact, geringer Aufwand)

### T2-1: `HexEditorControl` Dummy-Methoden kennzeichnen

**Status:** 📋 Geplant
**Priorität:** 🟢 Gering

**Problem:**
6 Dummy-Methoden für FXML-Kompatibilität ohne `@Override` oder `@SuppressWarnings`:

- `setText()`
- `setTextProperty()`
- `textProperty()`
- `hexDataProperty(ObjectProperty)`
- `setMaxX()`
- `setMaxY()`

**Lösung:**
`@SuppressWarnings("unused")` oder `@Override` für bekannt-signte Methoden.

**Betroffene Dateien:**

- `src/main/java/com/asn1editor/ui/HexEditorControl.java`

---

### T2-2: `ASN1Logger` Log-Rotation hinzufügen

**Status:** 📋 Geplant
**Priorität:** 🟢 Gering (abhängig von T0-3)

**Problem:**
Log-Datei wächst unbegrenzt.

**Lösung:**
Maximale Dateigröße (10 MB) + maximale Datei-Anzahl (5).

**Betroffene Dateien:**

- `src/main/java/com/asn1editor/service/ASN1Logger.java`

---

### T2-3: ASN.1 Parser maximale Rekursionstiefe hinzufügen

**Status:** 📋 Geplant
**Priorität:** 🟢 Gering

**Problem:**
Bei sehr tief verschachtelten ASN.1-Strukturen kann `StackOverflowError` auftreten.

**Lösung:**
Maximale Tiefe (default: 1000) als konfigurierbarer Parameter.

**Betroffene Dateien:**

- `src/main/java/com/asn1editor/parser/ASN1Parser.java`

---

### T2-4: ASN.1BerDecoder Tag-Konstanten explizit machen

**Status:** 📋 Geplant
**Priorität:** 🟢 Gering

**Problem:**
Tag-Konstanten sind implizit (z.B. `TAG_BOOLEAN = 0x01` steht nicht da).

**Lösung:**
Explizite Werte im Code kommentieren:

```java
private static final int TAG_BOOLEAN = 0x01;    // BOOLEAN
private static final int TAG_INTEGER = 0x02;     // INTEGER
// ...
```

**Betroffene Dateien:**

- `src/main/java/com/asn1editor/parser/ASN1BerDecoder.java`

---

### T2-5: `ASN1Document.root()` Redundanz prüfen

**Status:** 📋 Geplant
**Priorität:** 🟢 Gering

**Problem:**
`root()` gibt nur `roots.get(0)` zurück — ähnlich zu `definitions().get(0)`.

**Lösung:**
Prüfen ob `root()` noch gebraucht wird. Falls nicht: entfernen.

**Betroffene Dateien:**

- `src/main/java/com/asn1editor/model/ASN1Document.java`

---

### T2-6: `ASN1Service.isHexString()` / `isBase64Only()` Visibility konsistent machen

**Status:** 📋 Geplant
**Priorität:** 🟢 Gering

**Problem:**
`isHexString()` ist `public static`, `isBase64Only()` ist `private` — inkonsistent.

**Lösung:**
Entweder beide `public static` oder beide `private`.

**Betroffene Dateien:**

- `src/main/java/com/asn1editor/service/ASN1Service.java`

---

### T2-7: `ASN1Parser` StackOverflow-Schutz hinzufügen

**Status:** 📋 Geplant
**Priorität:** 🟢 Gering

**Problem:**
Rekursive Parser-Methoden ohne maximale Tiefe.

**Lösung:**
Thread-local depth counter oder Parameter für aktuelle Tiefe.

**Betroffene Dateien:**

- `src/main/java/com/asn1editor/parser/ASN1Parser.java`

---

## Zusammenfassung

| Priorität | Tasks | Geschätzt |
| ----------- | ------- | ----------- |
| **P0** | T0-1, T0-2, T0-3 | ~1-2h |
| **P1** | T1-1, T1-2, T1-3 | ~3-5h |
| **P2** | T1-4 – T2-7 | ~1-2h |
| **Gesamt** | **16 Tasks** | **~5-9h** |

---

## Abhängigkeiten

```text
T0-3 → T1-4 (Log-Rotation benötigt konfigurierbaren Pfad)

T0-1, T0-2 → T1-2 (HexUtils und TagInfo als Abhängigkeiten)
```

---

## Test-Strategie

Jede P0/P1-Aufgabe wird begleitet von:

1. `mvn clean test` — alle 101 Tests müssen bestehen
2. Manuelle UI-Tests für geänderte Bereiche
3. Optional: Neue Tests für Edge-Cases

Nach Abschluss aller P0/P1-Aufgaben:

- `mvn clean package` — Build erfolgreich
- Manuelle Tests mit `Test.crmf` und weiteren BER/DER-Dateien
