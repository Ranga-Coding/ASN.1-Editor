# ASN.1 Editor

A modern, cross-platform desktop editor for ASN.1 and BER/DER files. It provides a bidirectional split-view between a parsed ASN.1 tree and a hex-encoded byte editor, with real-time synchronization between both representations.

## Features

- **Bidirectional Editing** — Edit the parsed ASN.1 tree or the raw hex bytes; changes propagate to both views.
- **Automatic Format Detection** — Recognizes plain-text ASN.1 (BER), Base64-encoded ASN.1 (PEM), and binary BER/DER files on load.
- **Hex Editor** — A full-featured hex editor with:
  - Precise per-character clicking (each hex digit is individually editable).
  - Selection via **Shift + Arrow Keys** (←, →, ↑, ↓) for single characters or entire rows.
  - **Shift + Page Up/Down/Home/End** for larger selections.
  - Visual highlighting of TLV byte ranges selected in the tree.
- **Tree View** — A fully expandable ASN.1 tree with decoded values, OID names, and metadata for each node.
- **Details Panel** — Shows metadata (kind, hex value, decoded string, OID) for the currently selected tree node.
- **Change Tracking** — Detects unsaved changes and prompts before closing or exiting.
- **File Format Support** — `.crmf`, `.der`, `.ber`, `.pem`, `.asn1`, and plain text files.
- **Auto-Save** — Debounced (300 ms) automatic synchronization when editing hex data.

## Screenshots

| Tree View | Hex Editor |
|-----------|------------|
| Parsed ASN.1 tree with node details | Editable hex bytes with selection and TLV highlighting |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| UI Framework | JavaFX 21 |
| Build Tool | Maven |
| Testing | JUnit 5 |
| File I/O | Java NIO |

## Prerequisites

- **JDK 21** or later
- **Maven** 3.9+

## Building

```bash
mvn clean package
```

This compiles the source code, runs all tests, and produces a JAR in `target/`.

## Running

### Via Maven (recommended)

```bash
mvn javafx:run
```

### Direct JAR

```bash
java --module-path lib --add-modules=javafx.controls,javafx.fxml \
     -jar target/asn1-editor-1.0-SNAPSHOT.jar
```

### With a file

```bash
mvn javafx:run -Dexec.args="Test.crmf"
java --module-path lib --add-modules=javafx.controls,javafx.fxml \
     -jar target/asn1-editor-1.0-SNAPSHOT.jar Test.crmf
```

## Project Structure

```
src/main/java/com/asn1editor/
├── main.java              ─ Application entry point
├── model/
│   ├── ASN1Document.java  ─ Immutable ASN.1 document container
│   └── ASN1Node.java      ─ Record representing a TLV node
├── parser/
│   ├── ASN1BerDecoder.java ─ BER/DER binary decoder (TLV parsing)
│   ├── ASN1BerEncoder.java ─ BER/DER encoder (tree → bytes)
│   ├── ASN1Lexer.java      ─ Tokenizer for plain-text ASN.1
│   ├── ASN1Parser.java     ─ Recursive-descent parser for ASN.1 syntax
│   ├── ASN1ParseException  ─ Parser exception
│   └── Token.java          ─ Lexer token
├── service/
│   ├── ASN1Service.java    ─ Business logic: format detection, Base64 decode, validation
│   ├── ASN1IOException     ─ I/O exception with hex context
│   └── ASN1Logger.java     ─ Structured logging
├── io/
│   └── ASN1FileIO.java     ─ File read/write operations
└── ui/
    ├── MainController.java ─ JavaFX controller (load, save, tree build, hex sync)
    ├── Main.java           ─ JavaFX Application class
    └── HexEditorControl.java ─ Custom Canvas-based hex editor control

src/main/resources/fxml/
└── main.fxml              ─ JavaFX UI layout (tree, details, hex editor, status bar)

src/test/java/com/asn1editor/
├── model/
│   ├── ASN1DocumentTest.java
│   └── ASN1NodeTest.java
├── parser/
│   ├── ASN1BerDecoderTest.java
│   ├── ASN1BerEncoderTest.java
│   ├── ASN1LexerTest.java
│   ├── ASN1ParserTest.java
│   ├── HighlightDebugTest.java
│   ├── HighlightEndToEndTest.java
│   ├── HighlightIntegrationTest.java
│   └── HighlightPositionTest.java
└── service/
    └── ASN1ServiceTest.java
```

## Key Architectural Decisions

### Offset Tracking
The decoder tracks byte offsets during TLV parsing (`valueAbsOffset` in `readChildren()` and `readChildrenUntilEoc()`). These offsets link tree nodes to their exact byte positions in the hex editor for highlighting.

### syncHexFromTree()
After loading a BER/DER file, the tree is encoded back to bytes via `ASN1BerEncoder` and the hex editor is synchronized. Because the encoder converts indefinite-length structures to definite-length, offsets shift. The fix (`rebuildTreeViewWithEncodedRoot`) re-decodes the encoded bytes to keep offsets consistent.

### Binary vs Text Saving
- `.crmf` files are saved as MIME Base64 (76-char line wrapping) to preserve the original format.
- `.der` and `.ber` files are saved as raw binary bytes.

## Running Tests

```bash
mvn test
```

All tests pass: **101 / 101**.

## License

This project is licensed under the [Apache License 2.0](LICENSE).
