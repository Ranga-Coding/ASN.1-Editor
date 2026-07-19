package com.asn1editor.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Ein klassischer Hex-Editor für JavaFX.
 *
 * <p>Verhält sich wie etablierte Hex-Editoren (HxD, Hex Fiend, VS Code Hex Editor):
 * <ul>
 *   <li>Überschreiben statt Einfügen</li>
 *   <li>Cursor springt nach jeder Eingabe</li>
 *   <li>Nur Hex-Zeichen (0-9, A-F) werden akzeptiert</li>
 *   <li>ASCII-Spalte und Trennzeichen sind nicht editierbar</li>
 *   <li>Scroll-Position bleibt erhalten</li>
 * </ul>
 *
 * <p>Format: 16 Bytes pro Zeile, Hex-Darstellung gefolgt von ASCII-Darstellung.
 *
 * @author ASN.1 Editor Team
 */
public class HexEditorControl extends ScrollPane {

    // === Konstanten ===
    private static final int HEX_CHARS_PER_BYTE = 2;
    private static final int BYTES_PER_ROW = 16;
    private static final int CHAR_WIDTH = 9;
    private static final int HEX_COLUMN_WIDTH = BYTES_PER_ROW * (HEX_CHARS_PER_BYTE * CHAR_WIDTH + CHAR_WIDTH) - CHAR_WIDTH;
    private static final int ASCII_SEPARATOR_WIDTH = CHAR_WIDTH * 2;
    private static final int ASCII_COLUMN_WIDTH = BYTES_PER_ROW * CHAR_WIDTH;
    private static final int TOTAL_COLUMN_WIDTH = HEX_COLUMN_WIDTH + ASCII_SEPARATOR_WIDTH + ASCII_COLUMN_WIDTH;

    private static final String HEX_FONT_FAMILY = "Consolas";
    private static final int HEX_FONT_SIZE = 14;
    private static final int LINE_HEIGHT = 22;
    private static final int PADDING = 10;

    // === Farben ===
    private static final Color BG_COLOR = Color.rgb(30, 30, 30);
    private static final Color TEXT_COLOR = Color.rgb(220, 220, 220);
    private static final Color ASCII_COLOR = Color.rgb(150, 150, 150);
    private static final Color HIGHLIGHT_COLOR = Color.rgb(60, 60, 100);
    private static final Color CURSOR_COLOR = Color.rgb(200, 200, 255);
    private static final Color CURSOR_BG_COLOR = Color.rgb(80, 80, 180);

    // === Selektion-Zustand ===
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private boolean shiftDown = false;

    // === State ===
    private byte[] data = new byte[0];
    private int byteOffset = 0;
    private int charInByte = 0;
    private double scrollPosition = 0;

    // === Highlight-Zustand (TLV) ===
    private int highlightStart = -1;
    private int highlightEnd = -1;

    // === UI-Elemente ===
    private Canvas hexCanvas;
    private Canvas cursorCanvas;
    private GraphicsContext hexGc;
    private GraphicsContext cursorGc;

    // === Properties ===
    private final ObjectProperty<byte[]> hexData = new SimpleObjectProperty<>(this, "hexData");
    private final StringProperty hexText = new SimpleStringProperty(this, "hexText");

    /**
     * Erstellt einen neuen HexEditorControl.
     */
    public HexEditorControl() {
        this.getStyleClass().add("hex-editor");
        setupUI();
        initialize();
    }

    /**
     * Setzt die Byte-Daten und rendert den Editor neu.
     *
     * @param newData Die anzuzeigenden Byte-Daten
     */
    public void setHexData(byte[] newData) {
        this.data = newData != null ? newData : new byte[0];
        hexData.set(this.data);
        updateHexText();
        render();
    }

    /**
     * Gibt die aktuellen Byte-Daten zurück.
     *
     * @return Die aktuellen Byte-Daten
     */
    public byte[] getHexData() {
        return data;
    }

    /**
     * Gibt den aktuellen Hex-Text zurück.
     *
     * @return Der Hex-Text
     */
    public String getHexText() {
        return hexText.get();
    }

    /**
     * Gibt die aktuelle Scroll-Position zurück.
     *
     * @return Die Scroll-Position (0.0 bis 1.0)
     */
    public double getScrollPosition() {
        return scrollPosition;
    }

    /**
     * Setzt die Scroll-Position.
     *
     * @param position Die Scroll-Position (0.0 bis 1.0)
     */
    public void setScrollPosition(double position) {
        this.scrollPosition = Math.max(0, Math.min(1, position));
    }

    /**
     * Setzt den Cursor auf ein bestimmtes Byte.
     *
     * @param byteIndex Das Byte (0-basiert)
     * @param charInByte 0 = erstes Hex-Zeichen, 1 = zweites Hex-Zeichen
     */
    public void setCursor(int byteIndex, int charInByte) {
        this.byteOffset = Math.max(0, Math.min(data.length - 1, byteIndex));
        this.charInByte = Math.max(0, Math.min(1, charInByte));
        render();
    }

    /**
     * Hebt einen Byte-Bereich im Hex-Editor hervor.
     *
     * @param startByte Start-Byte (0-basiert, inklusiv)
     * @param endByte   End-Byte (0-basiert, exlusiv)
     */
    public void setHighlightRange(int startByte, int endByte) {
        this.highlightStart = Math.max(0, startByte);
        this.highlightEnd = Math.max(startByte + 1, endByte);
        render();
    }

    /**
     * Entfernt die Byte-Hervorhebung.
     */
    public void clearHighlight() {
        this.highlightStart = -1;
        this.highlightEnd = -1;
        render();
    }

    /**
     * Gibt den Start-Byte der aktuellen Hervorhebung zurück.
     *
     * @return Start-Byte, oder -1 wenn keine Hervorhebung
     */
    public int getHighlightStart() {
        return highlightStart;
    }

    /**
     * Gibt das End-Byte der aktuellen Hervorhebung zurück.
     *
     * @return End-Byte, oder -1 wenn keine Hervorhebung
     */
    public int getHighlightEnd() {
        return highlightEnd;
    }

    /**
     * Gibt die aktuelle Cursor-Position zurück.
     *
     * @return Array [byteOffset, charInByte]
     */
    public int[] getCursorPos() {
        return new int[]{byteOffset, charInByte};
    }

    /**
     * Gibt die Anzahl der Bytes pro Zeile zurück.
     *
     * @return Anzahl der Bytes pro Zeile
     */
    public int getBytesPerRow() {
        return BYTES_PER_ROW;
    }

    /**
     * Gibt die Breite der Hex-Spalte zurück.
     *
     * @return Breite der Hex-Spalte in Pixeln
     */
    public int getHexColumnWidth() {
        return HEX_COLUMN_WIDTH;
    }

    /**
     * Gibt die Gesamtbreite der Spalten zurück.
     *
     * @return Gesamtbreite in Pixeln
     */
    public int getTotalColumnWidth() {
        return TOTAL_COLUMN_WIDTH;
    }

    /**
     * Gibt die Höhe einer Zeile zurück.
     *
     * @return Höhe einer Zeile in Pixeln
     */
    public int getLineHeight() {
        return LINE_HEIGHT;
    }

    /**
     * Gibt die maximale Anzahl von Zeilen zurück.
     *
     * @return Anzahl der Zeilen
     */
    public int getMaxRows() {
        return data.length == 0 ? 1 : (data.length + BYTES_PER_ROW - 1) / BYTES_PER_ROW;
    }

    /**
     * Gibt die maximale horizontale Position zurück.
     *
     * @return Maximale x-Position
     */
    public int getMaxX() {
        return HEX_COLUMN_WIDTH + ASCII_SEPARATOR_WIDTH + ASCII_COLUMN_WIDTH;
    }

    /**
     * Gibt die maximale vertikale Position zurück.
     *
     * @return Maximale y-Position
     */
    public int getMaxY() {
        return getMaxRows() * LINE_HEIGHT;
    }

    /**
     * Setzt die maximale horizontale Position.
     *
     * @param x Neue maximale x-Position
     */
    public void setMaxX(int x) {
        // Dummy-Methode für FXML-Kompatibilität
    }

    /**
     * Setzt die maximale vertikale Position.
     *
     * @param y Neue maximale y-Position
     */
    public void setMaxY(int y) {
        // Dummy-Methode für FXML-Kompatibilität
    }

    /**
     * Gibt den Hex-Text für eine gegebene Zeile zurück.
     *
     * @param row Die Zeilennummer (0-basiert)
     * @return Der Hex-Text für die Zeile
     */
    public String getHexText(int row) {
        int startByte = row * BYTES_PER_ROW;
        if (startByte >= data.length) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int bytesInRow = Math.min(BYTES_PER_ROW, data.length - startByte);

        // Hex-Spalte
        for (int i = 0; i < bytesInRow; i++) {
            sb.append(String.format("%02X", data[startByte + i]));
            if (i < bytesInRow - 1) {
                sb.append(" ");
            }
        }

        // ASCII-Spalte
        sb.append("  ");
        for (int i = 0; i < bytesInRow; i++) {
            char c = (data[startByte + i] >= 32 && data[startByte + i] < 127)
                    ? (char) data[startByte + i] : '.';
            sb.append(c);
        }

        return sb.toString();
    }

    /**
     * Gibt die Breite eines Zeichens zurück.
     *
     * @return Breite eines Zeichens
     */
    public double getCharWidth() {
        return CHAR_WIDTH;
    }

    /**
     * Gibt die Anzahl der sichtbaren Zeilen zurück.
     *
     * @return Anzahl der sichtbaren Zeilen
     */
    public int getVisibleRows() {
        return getMaxRows();
    }

    /**
     * Setzt den Text des Editors.
     *
     * @param text Der neue Text
     */
    public void setText(String text) {
        // Dummy-Methode für FXML-Kompatibilität
    }

    /**
     * Gibt den aktuellen Text des Editors zurück.
     *
     * @return Der aktuelle Text
     */
    public String getText() {
        return hexText.get();
    }

    /**
     * Gibt die Länge des Textes zurück.
     *
     * @return Länge des Textes
     */
    public int getLength() {
        return hexText.get().length();
    }

    /**
     * Setzt den Text-Eigenschaftswert.
     *
     * @param text Der neue Text
     */
    public void textProperty(String text) {
        hexText.set(text);
    }

    /**
     * Gibt die Text-Eigenschaft zurück.
     *
     * @return Die Text-Eigenschaft
     */
    public StringProperty textProperty() {
        return hexText;
    }

    /**
     * Gibt die Daten-Eigenschaft zurück.
     *
     * @return Die Daten-Eigenschaft
     */
    public ObjectProperty<byte[]> hexDataProperty() {
        return hexData;
    }

    /**
     * Setzt die Daten-Eigenschaft.
     *
     * @param data Die neuen Daten
     */
    public void hexDataProperty(byte[] data) {
        setHexData(data);
    }

    /**
     * Setzt den Text-Eigenschaftswert.
     *
     * @param text Der neue Text
     */
    public void setTextProperty(String text) {
        hexText.set(text);
    }

    /**
     * Setzt die Text-Eigenschaft.
     *
     * @param textProperty Die Text-Eigenschaft
     */
    public void textProperty(StringProperty textProperty) {
        // Dummy-Methode für FXML-Kompatibilität
    }

    /**
     * Setzt die Daten-Eigenschaft.
     *
     * @param dataProperty Die Daten-Eigenschaft
     */
    public void hexDataProperty(ObjectProperty<byte[]> dataProperty) {
        hexData.set(dataProperty != null ? dataProperty.get() : new byte[0]);
    }

    private void setupUI() {
        // Canvas für Hex-Zeichen
        hexCanvas = new Canvas(TOTAL_COLUMN_WIDTH + PADDING * 2, 100);
        hexGc = hexCanvas.getGraphicsContext2D();
        hexGc.setFont(Font.font(HEX_FONT_FAMILY, HEX_FONT_SIZE));

        // Canvas für Cursor-Overlay
        cursorCanvas = new Canvas(TOTAL_COLUMN_WIDTH + PADDING * 2, 100);
        cursorGc = cursorCanvas.getGraphicsContext2D();
        cursorGc.setFont(Font.font(HEX_FONT_FAMILY, HEX_FONT_SIZE));
        cursorGc.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
        cursorGc.setTextBaseline(VPos.TOP);

        // ScrollPane konfigurieren
        setContent(hexCanvas);
        setFitToWidth(true);
        setFitToHeight(true);
        setPannable(true);
        setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        // Event-Handler für Eingabe
        setupInputHandlers();
    }

    private void initialize() {
        render();
    }

    private void updateHexText() {
        StringBuilder sb = new StringBuilder();
        int rows = getMaxRows();
        for (int i = 0; i < rows; i++) {
            sb.append(getHexText(i));
            if (i < rows - 1) {
                sb.append("\n");
            }
        }
        hexText.set(sb.toString());
    }

    private void render() {
        if (hexGc == null || cursorGc == null) {
            return;
        }

        int rows = getMaxRows();
        int height = Math.max(rows * LINE_HEIGHT, 100);

        hexCanvas.setWidth(TOTAL_COLUMN_WIDTH + PADDING * 2);
        hexCanvas.setHeight(height);
        cursorCanvas.setWidth(TOTAL_COLUMN_WIDTH + PADDING * 2);
        cursorCanvas.setHeight(height);

        // Canvas positionieren
        hexCanvas.setLayoutX(PADDING);
        hexCanvas.setLayoutY(PADDING);
        cursorCanvas.setLayoutX(PADDING);
        cursorCanvas.setLayoutY(PADDING);

        // Hintergrund
        hexGc.setFill(BG_COLOR);
        hexGc.fillRect(0, 0, hexCanvas.getWidth(), hexCanvas.getHeight());

        // Highlight-Bereich zeichnen (gelb, 25% opacity)
        renderHighlight();

        // Selektion zeichnen (blau, 25% opacity)
        renderSelection();

        // Hex-Zeichen rendern
        hexGc.setFill(TEXT_COLOR);
        hexGc.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
        hexGc.setTextBaseline(VPos.TOP);

        for (int row = 0; row < rows; row++) {
            int startByte = row * BYTES_PER_ROW;
            int bytesInRow = Math.min(BYTES_PER_ROW, data.length - startByte);
            int y = row * LINE_HEIGHT + (int)(HEX_FONT_SIZE * 0.75);

            // Hex-Spalte
            int x = 0;
            for (int i = 0; i < bytesInRow; i++) {
                // Byte-Highlighting
                if (startByte + i == byteOffset) {
                    hexGc.setFill(HIGHLIGHT_COLOR);
                    hexGc.fillRect(x, y - 2, HEX_CHARS_PER_BYTE * CHAR_WIDTH + 2, LINE_HEIGHT);
                    hexGc.setFill(CURSOR_COLOR);
                }

                String hex = String.format("%02X", data[startByte + i]);
                hexGc.fillText(hex, x, y);
                x += HEX_CHARS_PER_BYTE * CHAR_WIDTH;

                if (i < bytesInRow - 1) {
                    x += CHAR_WIDTH; // Leerzeichen zwischen Bytes
                }
            }

            // ASCII-Spalte
            int asciiX = HEX_COLUMN_WIDTH + ASCII_SEPARATOR_WIDTH;
            hexGc.setFill(ASCII_COLOR);
            for (int i = 0; i < bytesInRow; i++) {
                char c = (data[startByte + i] >= 32 && data[startByte + i] < 127)
                        ? (char) data[startByte + i] : '.';
                hexGc.fillText(String.valueOf(c), asciiX + i * CHAR_WIDTH, y);
            }
        }

        // Cursor rendern
        renderCursor();
    }

    /**
     * Zeichnet den gelben Highlight-Bereich für TLV-Bytes.
     *
     * <p>Die X-Position jedes Byte i in einer Zeile ist: i * (HEX_CHARS_PER_BYTE * CHAR_WIDTH + CHAR_WIDTH)
     * Jedes Byte belegt HEX_CHARS_PER_BYTE * CHAR_WIDTH Pixel + CHAR_WIDTH für das Trennzeichen.
     * Das letzte Byte pro Zeile hat kein Trennzeichen, daher wird die letzte X-Position korrigiert.
     */
    private void renderHighlight() {
        if (highlightStart < 0 || highlightEnd <= highlightStart) {
            return;
        }

        int rows = getMaxRows();
        int lineStartRow = highlightStart / BYTES_PER_ROW;
        int lineEndRow = Math.min((highlightEnd - 1) / BYTES_PER_ROW, rows - 1);

        if (lineStartRow > lineEndRow) {
            return;
        }

        // Breite eines Byte-Blocks: 2 Hex-Zeichen + 1 Leerzeichen
        final int BYTE_WIDTH = HEX_CHARS_PER_BYTE * CHAR_WIDTH + CHAR_WIDTH;
        // Breite nur der Hex-Zeichen (ohne Trennzeichen)
        final int HEX_ONLY_WIDTH = HEX_CHARS_PER_BYTE * CHAR_WIDTH;

        for (int row = lineStartRow; row <= lineEndRow; row++) {
            int rowStartByte = row * BYTES_PER_ROW;
            int rowEndByte = Math.min(rowStartByte + BYTES_PER_ROW, data.length);

            // Relevanten Byte-Bereich in dieser Zeile bestimmen (relativ zur Zeile)
            // highlightStart/End sind absolut, rowStartByte/rowEndByte sind absolut
            int startByteInRow = Math.max(0, highlightStart - rowStartByte);
            int endByteInRow = Math.min(highlightEnd, rowEndByte) - rowStartByte;

            if (startByteInRow >= endByteInRow) {
                continue;
            }

            // X-Positionen: jedes Byte beginnt bei i * BYTE_WIDTH
            int startX = startByteInRow * BYTE_WIDTH;
            // End-X: rechtes Ende des letzten Bytes dieser Zeile
            int endX = (endByteInRow - 1) * BYTE_WIDTH + HEX_ONLY_WIDTH;

            int highlightWidth = endX - startX;
            if (highlightWidth > 0) {
                int y = row * LINE_HEIGHT;
                hexGc.setFill(Color.rgb(255, 255, 0, 0.25));
                hexGc.fillRect(startX, y, highlightWidth, LINE_HEIGHT);
            }
        }
    }

    /**
     * Zeichnet den blau hervorgehobenen Selektions-Bereich.
     */
    private void renderSelection() {
        if (selectionStart < 0 || selectionEnd <= selectionStart) {
            return;
        }

        int rows = getMaxRows();
        int lineStartRow = selectionStart / BYTES_PER_ROW;
        int lineEndRow = Math.min((selectionEnd - 1) / BYTES_PER_ROW, rows - 1);

        if (lineStartRow > lineEndRow) {
            return;
        }

        final int BYTE_WIDTH = HEX_CHARS_PER_BYTE * CHAR_WIDTH + CHAR_WIDTH;
        final int HEX_ONLY_WIDTH = HEX_CHARS_PER_BYTE * CHAR_WIDTH;

        for (int row = lineStartRow; row <= lineEndRow; row++) {
            int rowStartByte = row * BYTES_PER_ROW;
            int rowEndByte = Math.min(rowStartByte + BYTES_PER_ROW, data.length);

            int startByteInRow = Math.max(0, selectionStart - rowStartByte);
            int endByteInRow = Math.min(selectionEnd, rowEndByte) - rowStartByte;

            if (startByteInRow >= endByteInRow) {
                continue;
            }

            int startX = startByteInRow * BYTE_WIDTH;
            int endX = (endByteInRow - 1) * BYTE_WIDTH + HEX_ONLY_WIDTH;

            int selWidth = endX - startX;
            if (selWidth > 0) {
                int y = row * LINE_HEIGHT;
                hexGc.setFill(Color.rgb(60, 60, 255, 0.25));
                hexGc.fillRect(startX, y, selWidth, LINE_HEIGHT);
            }
        }
    }

    private void renderCursor() {
        if (cursorGc == null) {
            return;
        }

        cursorGc.clearRect(0, 0, cursorCanvas.getWidth(), cursorCanvas.getHeight());

        int row = byteOffset / BYTES_PER_ROW;
        int charInBytePos = byteOffset % BYTES_PER_ROW;
        int x = charInBytePos * (HEX_CHARS_PER_BYTE * CHAR_WIDTH + 1) + charInByte * CHAR_WIDTH;
        int y = row * LINE_HEIGHT + (int)(HEX_FONT_SIZE * 0.75);

        // Cursor-Hintergrund
        cursorGc.setFill(CURSOR_BG_COLOR);
        cursorGc.fillRect(x, y, CHAR_WIDTH, LINE_HEIGHT);

        // Cursor-Text
        cursorGc.setFill(CURSOR_COLOR);
        if (byteOffset < data.length) {
            String hex = String.format("%02X", data[byteOffset]);
            cursorGc.fillText(String.valueOf(hex.charAt(charInByte)), x, y);
        }
    }

    private void setupInputHandlers() {
        // Shift-Taste gedrückt / losgelassen tracken
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.SHIFT) {
                shiftDown = true;
            }
        });
        addEventFilter(javafx.scene.input.KeyEvent.KEY_RELEASED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.SHIFT) {
                shiftDown = false;
            }
        });

        // Hex-Zeichen nur erlauben
        addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, e -> {
            String keyChar = e.getCharacter();
            if (keyChar != null && !keyChar.isEmpty()) {
                if (!keyChar.matches("[0-9a-fA-F]")) {
                    e.consume();
                    return;
                }
                // Überschreiben statt Einfügen
                e.consume();
                if (byteOffset < data.length) {
                    int digit = Integer.parseInt(keyChar.toUpperCase(), 16);
                    if (charInByte == 0) {
                        data[byteOffset] = (byte) ((data[byteOffset] & 0x0F) | (digit << 4));
                        charInByte = 1;
                    } else {
                        data[byteOffset] = (byte) ((data[byteOffset] & 0xF0) | digit);
                        charInByte = 0;
                        byteOffset++;
                    }
                    updateHexText();
                    render();
                }
            }
        });

        // Backspace/Delete/Pfeiltasten
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.SHIFT) {
                // bereits oben behandelt
                return;
            }

            if (e.getCode() == javafx.scene.input.KeyCode.BACK_SPACE) {
                e.consume();
                if (charInByte == 0 && byteOffset > 0) {
                    byteOffset--;
                    charInByte = 1;
                } else if (charInByte == 1) {
                    charInByte = 0;
                }
                render();
                return;
            }

            if (e.getCode() == javafx.scene.input.KeyCode.DELETE) {
                e.consume();
                if (charInByte == 0) {
                    charInByte = 1;
                } else if (byteOffset < data.length - 1) {
                    byteOffset++;
                    charInByte = 0;
                }
                render();
                return;
            }

            if (e.getCode() == javafx.scene.input.KeyCode.RIGHT) {
                e.consume();
                int newOffset = byteOffset;
                int newChar = charInByte;
                if (charInByte == 0) {
                    newChar = 1;
                } else if (byteOffset < data.length - 1) {
                    newOffset++;
                    newChar = 0;
                }
                if (shiftDown) {
                    startSelection(newOffset, newChar);
                } else {
                    clearSelection();
                    byteOffset = newOffset;
                    charInByte = newChar;
                }
                render();
                return;
            }

            if (e.getCode() == javafx.scene.input.KeyCode.LEFT) {
                e.consume();
                int newOffset = byteOffset;
                int newChar = charInByte;
                if (charInByte == 1) {
                    newChar = 0;
                } else if (byteOffset > 0) {
                    newOffset--;
                    newChar = 1;
                }
                if (shiftDown) {
                    startSelection(newOffset, newChar);
                } else {
                    clearSelection();
                    byteOffset = newOffset;
                    charInByte = newChar;
                }
                render();
                return;
            }

            if (e.getCode() == javafx.scene.input.KeyCode.UP) {
                e.consume();
                int newOffset = byteOffset - BYTES_PER_ROW;
                if (newOffset < 0) {
                    newOffset = 0;
                }
                int newChar = charInByte;
                if (shiftDown) {
                    startSelection(newOffset, newChar);
                } else {
                    clearSelection();
                    byteOffset = newOffset;
                    charInByte = newChar;
                }
                render();
                return;
            }

            if (e.getCode() == javafx.scene.input.KeyCode.DOWN) {
                e.consume();
                int newOffset = byteOffset + BYTES_PER_ROW;
                if (newOffset >= data.length) {
                    newOffset = data.length - 1;
                }
                int newChar = charInByte;
                if (shiftDown) {
                    startSelection(newOffset, newChar);
                } else {
                    clearSelection();
                    byteOffset = newOffset;
                    charInByte = newChar;
                }
                render();
                return;
            }

            if (e.getCode() == javafx.scene.input.KeyCode.HOME) {
                e.consume();
                int newOffset = byteOffset / BYTES_PER_ROW * BYTES_PER_ROW;
                if (shiftDown) {
                    startSelection(newOffset, 0);
                } else {
                    clearSelection();
                    byteOffset = newOffset;
                    charInByte = 0;
                }
                render();
                return;
            }

            if (e.getCode() == javafx.scene.input.KeyCode.END) {
                e.consume();
                int row = byteOffset / BYTES_PER_ROW;
                int rowEnd = Math.min((row + 1) * BYTES_PER_ROW - 1, data.length - 1);
                int newOffset = rowEnd;
                if (shiftDown) {
                    startSelection(newOffset, 0);
                } else {
                    clearSelection();
                    byteOffset = newOffset;
                    charInByte = 0;
                }
                render();
                return;
            }

            if (e.getCode() == javafx.scene.input.KeyCode.PAGE_UP) {
                e.consume();
                int scrollBytes = BYTES_PER_ROW * 8;
                int newOffset = byteOffset - scrollBytes;
                if (shiftDown) {
                    startSelection(Math.max(0, newOffset), charInByte);
                } else {
                    clearSelection();
                    byteOffset = Math.max(0, newOffset);
                }
                render();
                return;
            }

            if (e.getCode() == javafx.scene.input.KeyCode.PAGE_DOWN) {
                e.consume();
                int scrollBytes = BYTES_PER_ROW * 8;
                int newOffset = byteOffset + scrollBytes;
                if (shiftDown) {
                    startSelection(Math.min(data.length - 1, newOffset), charInByte);
                } else {
                    clearSelection();
                    byteOffset = Math.min(data.length - 1, newOffset);
                }
                render();
                return;
            }
        });

        // Mausklick → Cursor-Positionierung (präzise pro Hex-Zeichen)
        addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
            // e.getX/Y() sind relativ zum Canvas (ScrollPane.Content = Canvas).
            // Canvas LayoutX/Y = PADDING wird in render() gesetzt, aber die Zeichen
            // beginnen bei x=0, y=0 im Canvas. Also KEIN PADDING subtrahieren.
            double mouseX = e.getX();
            double mouseY = e.getY();

            int row = (int) (mouseY / LINE_HEIGHT);
            int startByte = row * BYTES_PER_ROW;
            if (startByte >= data.length || row < 0) {
                return;
            }
            int bytesInRow = Math.min(BYTES_PER_ROW, data.length - startByte);

            // Hex-Spalte: jedes Byte ist BYTE_WIDTH Pixel breit
            final int BYTE_WIDTH = HEX_CHARS_PER_BYTE * CHAR_WIDTH + CHAR_WIDTH;
            final int HEX_ONLY_WIDTH = HEX_CHARS_PER_BYTE * CHAR_WIDTH;

            // Zuerst prüfen ob wir in der Hex-Spalte sind
            if (mouseX < HEX_COLUMN_WIDTH) {
                int byteIndex = (int) (mouseX / BYTE_WIDTH);
                byteIndex = Math.max(0, Math.min(bytesInRow - 1, byteIndex));
                int xForByte = byteIndex * BYTE_WIDTH;
                int relX = (int) mouseX - xForByte;

                // Innerhalb dieses Bytes: welches der 2 Hex-Zeichen?
                // High-Nibble (linke Hälfte) oder Low-Nibble (rechte Hälfte)
                int charIndex = (relX < HEX_ONLY_WIDTH / 2) ? 0 : 1;
                charIndex = Math.max(0, Math.min(1, charIndex));

                int targetOffset = startByte + byteIndex;

                if (shiftDown) {
                    startSelection(targetOffset, charIndex);
                } else {
                    clearSelection();
                    byteOffset = targetOffset;
                    charInByte = charIndex;
                }
                render();
                return;
            }

            // In der ASCII-Spalte klicken: korrespondierendes Byte auswählen
            int asciiOffset = (int) ((mouseX - HEX_COLUMN_WIDTH - ASCII_SEPARATOR_WIDTH) / CHAR_WIDTH);
            asciiOffset = Math.max(0, Math.min(bytesInRow - 1, asciiOffset));
            int targetOffset = startByte + asciiOffset;

            if (shiftDown) {
                startSelection(targetOffset, 0);
            } else {
                clearSelection();
                byteOffset = targetOffset;
                charInByte = 0;
            }
            render();
        });
    }

    /**
     * Startet eine Selektion vom aktuellen Cursor zur Zielposition.
     */
    private void startSelection(int targetByte, int targetChar) {
        // Berechne die absolute Byte-Position für targetChar
        int startAbs;
        if (targetChar == 0) {
            startAbs = targetByte;
        } else {
            startAbs = Math.min(targetByte, data.length - 1);
        }

        // Cursor-Position als Byte-Adresse berechnen
        int cursorAbs;
        if (charInByte == 0) {
            cursorAbs = byteOffset;
        } else {
            cursorAbs = byteOffset;
        }

        // Sicherstellen dass selectionStart <= selectionEnd
        selectionStart = Math.min(startAbs, cursorAbs);
        selectionEnd = Math.max(startAbs + 1, cursorAbs + 1);

        // Begrenze auf Daten-Grenzen
        selectionStart = Math.max(0, selectionStart);
        selectionEnd = Math.min(data.length, selectionEnd);
    }

    /**
     * Entfernt die aktuelle Selektion.
     */
    private void clearSelection() {
        selectionStart = -1;
        selectionEnd = -1;
    }
}
