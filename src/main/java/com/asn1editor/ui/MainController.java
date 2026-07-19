package com.asn1editor.ui;

import com.asn1editor.model.ASN1Document;
import com.asn1editor.model.ASN1Node;
import com.asn1editor.parser.BERDecodeException;
import com.asn1editor.service.ASN1IOException;
import com.asn1editor.service.ASN1Service;
import com.asn1editor.io.ASN1FileIO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.prefs.Preferences;

/**
 * Controller f�r die Haupt-UI der ASN.1-Editor-Anwendung.
 *
 * <p>Verbindet den {@link ASN1Service} mit der JavaFX-Oberfl�che.
 * Lädt ASN.1-Quelltext, parst ihn und visualisiert den Baum in einem TreeView.
 */
public class MainController {

    @FXML
    private TreeView<TreeItemContent> treeView;

    @FXML
    private TextArea detailsArea;

    /**
     * Plain-text ASN.1 Text-Ansicht (programmatisch erstellt).
     * Wird nur für Plain-text ASN.1-Dateien verwendet.
     */
    private TextArea rawArea;

    @FXML
    private HexEditorControl hexEditorArea;

    @FXML
    private VBox rootPane;

    @FXML
    private Label statusLabel;

    @FXML
    private Label cursorPosition;

    @FXML
    private Label hexStatusLabel;

    // Original-Bytes für Hex-Editor (wenn Datei geladen)
    private byte[] currentHexBytes;

    /**
     * True wenn der Hex-Editor seit der letzten Synchronisierung geändert wurde.
     */
    private boolean hexDirty = false;

    private static final String PREF_LAST_DIRECTORY = "lastDirectory";

    private final ASN1Service service = new ASN1Service();
    private final ASN1FileIO fileIO = new ASN1FileIO();
    private final Preferences preferences = Preferences.userNodeForPackage(MainController.class);
    private File currentFile;

    /**
     * True wenn es sich um eine Binärdatei (BER/DER) handelt.
     */
    private boolean isBinaryFile = false;

    /**
     * Timer für automatische Hex → TreeView-Synchronisation (Debounce).
     */
    private java.util.Timer autoSyncTimer;
    private static final long AUTO_SYNC_DELAY_MS = 300;

    /**
     * Initialisiert den Controller nach dem Laden des FXML-Dokuments.
     */
    @FXML
    public void initialize() {
        // Programmatisch: rawArea für Plain-text ASN.1 erstellen
        rawArea = new TextArea();
        rawArea.setWrapText(true);
        rawArea.setPromptText("ASN.1-Quelltext (Plain-text)");
        rawArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        VBox.setVgrow(rawArea, javafx.scene.layout.Priority.ALWAYS);
        rootPane.getChildren().add(rawArea);
        rawArea.setVisible(false); // Standardmäßig ausgeblendet

        detailsArea.setWrapText(true);

        // TreeView-Auswahl-Listener f�r Detail-Anzeige
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null) {
                showDetails(newItem.getValue());
                highlightSelectedNode(newItem.getValue());
            } else {
                clearHighlights();
            }
        });

        // Hex-Editor-Dirty-Tracking: Änderung der Daten markiert den Editor als "dirty"
        hexEditorArea.hexDataProperty().addListener((obs, oldData, newData) -> {
            if (currentHexBytes != null && newData != null) {
                hexDirty = true;
                updateHexStatus();
                scheduleAutoSync();
            }
        });

        // Auto-Sync-Timer initialisieren
        autoSyncTimer = new java.util.Timer("HexAutoSync", true);
    }

    /**
     * Zeigt die Details eines ASN1Node in der Detail-Anzeige.
     */
    private void showDetails(TreeItemContent content) {
        if (content == null) {
            detailsArea.setText("Keine Details verf�gbar.");
            return;
        }

        ASN1Node node = content.node();
        if (node == null) {
            detailsArea.setText(content.displayText());
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(node.name()).append("\n");
        if (node.value() != null) {
            sb.append("Wert: ").append(node.value()).append("\n");
        }
        for (ASN1Node child : node.children()) {
            if (child.isLeaf()) {
                sb.append(child.name()).append(": ").append(child.value()).append("\n");
            }
        }
        sb.append("Typ: ").append(node.isLeaf() ? "Blatt" : "Knoten").append("\n");
        sb.append("Kinder: ").append(node.children().size()).append("\n");
        detailsArea.setText(sb.toString());

        // Statusleiste: Knotenname, Wert und Byte-Position
        updateStatusLabel(node);
    }

    /**
     * Aktualisiert die Statusleiste mit dem ausgewählten Knoten und seiner Byte-Position.
     *
     * <p>Format:
     * <ul>
     *   <li>Ber/DER-Knoten: {@code Ausgewählt: name = value (Byte X-Y)}</li>
     *   <li>Text-ASN.1-Knoten: {@code Ausgewählt: name = value}</li>
     * </ul>
     *
     * @param node der ausgewählte ASN1Node
     */
    private void updateStatusLabel(ASN1Node node) {
        if (node == null) {
            statusLabel.setText("Bereit");
            return;
        }

        StringBuilder sb = new StringBuilder("Ausgewählt: ");
        sb.append(node.name());

        String displayValue = displayValueOf(node);
        if (displayValue != null && !displayValue.isEmpty()) {
            sb.append(" = ").append(displayValue);
        }

        // Byte-Position für BER/DER-Knoten
        if (node.offset() >= 0 && node.length() > 0) {
            int endByte = node.offset() + node.length() - 1;
            sb.append(" (Byte ").append(node.offset()).append("-").append(endByte).append(")");
            cursorPosition.setText("Byte " + node.offset() + "/" + endByte);
        } else {
            cursorPosition.setText("");
        }

        statusLabel.setText(sb.toString());
    }

    /**
     * Markiert die Bytes des ausgewählten Knotens im Hex-Editor.
     *
     * @param content der ausgewählte TreeItemContent
     */
    private void highlightSelectedNode(TreeItemContent content) {
        if (content == null || content.node() == null) {
            clearHighlights();
            return;
        }
        ASN1Node node = content.node();
        if (node.offset() < 0 || node.length() <= 0) {
            // Kein Byte-Offset vorhanden (Text-ASN.1)
            clearHighlights();
            return;
        }
        highlightBytes(node.offset(), node.length());
    }

    /**
     * Markiert die Bytes eines Knotens im Hex-Editor.
     *
     * <p>Verwendet die im ASN1Node gespeicherten TLV-Byte-Offsets (offset, length),
     * die vom {@link ASN1BerDecoder} bei der Dekodierung gesetzt wurden.
     *
     * @param byteOffset Start-Byte im aktuellen Hex-Inhalt
     * @param byteLength Länge des zu markierenden Bereichs in Bytes
     */
    private void highlightBytes(int byteOffset, int byteLength) {
        if (hexEditorArea == null || currentHexBytes == null || currentHexBytes.length == 0) {
            return;
        }
        int endByte = byteOffset + byteLength;
        hexEditorArea.setHighlightRange(byteOffset, endByte);
        // Hex-Editor zum markierten Bereich scrollen (Cursor setzen)
        hexEditorArea.setCursor(byteOffset, 0);
    }

    /**
     * Entfernt die Byte-Hervorhung im Hex-Editor.
     */
    private void clearHighlights() {
        if (hexEditorArea != null) {
            hexEditorArea.clearHighlight();
        }
        cursorPosition.setText("");
    }

    /**
     * Öffnet eine Datei über den FileChooser.
     */
    @FXML
    public void handleOpen() {
        FileChooser fileChooser = createFileChooser();

        File file = fileChooser.showOpenDialog(treeView.getScene().getWindow());
        if (file != null) {
            loadFile(file);
        }
    }

    /**
     * Speichert die aktuelle Datei.
     */
    @FXML
    public void handleSave() {
        if (currentFile != null) {
            saveToFile(currentFile);
        } else {
            handleSaveAs();
        }
    }

    /**
     * Speichert die Datei unter einem neuen Pfad.
     */
    @FXML
    public void handleSaveAs() {
        FileChooser fileChooser = createFileChooser();

        File file = fileChooser.showSaveDialog(treeView.getScene().getWindow());
        if (file != null && saveToFile(file)) {
            currentFile = file;
        }
    }

    /**
     * Lädt eine Datei und aktualisiert die Benutzeroberfläche.
     *
     * <p>Der Hex-Editor zeigt stets den decodierten Binärstrom:
     * <ul>
     *   <li>Base64/PEM → decodierte Bytes</li>
     *   <li>Plain-text ASN.1 → ISO-8859-1 Bytes</li>
     * </ul>
     */
    public void loadFile(File file) {
        try {
            byte[] rawBytes = java.nio.file.Files.readAllBytes(file.toPath());
            currentFile = file;
            rememberDirectory(file);

            String rawText = new String(rawBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
            String fileFormat = service.detectFormat(rawText).name();

            // ── Base64/PEM: Decodieren und decodierte Bytes anzeigen ──
            if ("BASE64".equals(fileFormat)) {
                String decodedContent = service.decodeBase64IfNeeded(rawText);

                if (ASN1Service.isHexString(decodedContent)) {
                    // Decodierte Binärdaten (BER/DER) — rawArea ausblenden
                    byte[] decodedBytes = ASN1Service.fromHexString(decodedContent);
                    currentHexBytes = decodedBytes;
                    isBinaryFile = true;
                    buildHexEditor(decodedBytes);
                    rawArea.clear();
                    rawArea.setVisible(false);

                    // BER-Decodierung und TreeView-Aufbau
                    try {
                        ASN1Node root = com.asn1editor.parser.ASN1BerDecoder.decode(decodedBytes);
                        buildTreeView(new com.asn1editor.model.ASN1Document(root));
                        statusLabel.setText("Geladen: " + file.getName() + " (" + decodedBytes.length + " Bytes)");
                    } catch (BERDecodeException e) {
                        buildHexEditor(decodedBytes);
                        statusLabel.setText("Geladen: " + file.getName()
                                + " (" + decodedBytes.length + " Bytes) — BER-Decodierung fehlgeschlagen");
                        showError("Binäre ASN.1-Datei erkannt", "Die Datei '" + file.getName()
                                + "' ist binär (BER/DER).\n\nDecodierung fehlgeschlagen:\n" + e.getMessage());
                    }
                } else {
                    // Decodierte ASN.1-Texte — rawArea anzeigen
                    rawArea.setText(decodedContent);
                    rawArea.setVisible(true);
                    currentHexBytes = decodedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    buildHexEditor(currentHexBytes);

                    ASN1Document doc = service.parse(decodedContent, file.toPath());
                    buildTreeView(doc);
                    statusLabel.setText("Geladen: " + file.getName());
                }
            }
            // ── Plain-text ASN.1: ISO-8859-1 Bytes anzeigen ──
            else {
                currentHexBytes = rawText.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                rawArea.setText(rawText);
                rawArea.setVisible(true);
                isBinaryFile = false;
                buildHexEditor(currentHexBytes);

                ASN1Document doc = service.parse(rawText, file.toPath());
                buildTreeView(doc);
                statusLabel.setText("Geladen: " + file.getName());
            }
        } catch (java.io.IOException e) {
            showError("Datei laden fehlgeschlagen", "IO-Fehler: " + e.getMessage());
            statusLabel.setText("Fehler beim Laden: " + file.getName());
        } catch (ASN1IOException e) {
            // Prüfen: Enthält Exception Hex-Inhalt (binäre Datei)?
            if (e.hasHexContent()) {
                showBinaryHex(file.getName(), e.getHexContent());
            } else {
                showError("Datei laden fehlgeschlagen", e.getMessage());
                statusLabel.setText("Fehler beim Laden: " + file.getName());
            }
        }
    }

    /**
     * Speichert den aktuellen ASN.1-Quelltext.
     */
    private boolean saveToFile(File file) {
        if (!confirmSaveAfterValidation(file)) {
            statusLabel.setText("Speichern abgebrochen: " + file.getName());
            return false;
        }

        try {
            if (isBinaryFile && currentHexBytes != null) {
                // Binärdatei (BER/DER): schreibe als Base64, wenn Dateiendung .crmf ist
                String lowerName = file.getName().toLowerCase();
                if (lowerName.endsWith(".crmf")) {
                    // CRMF ist per Konvention Base64-kodiert — Base64 kodieren
                    java.util.Base64.Encoder enc = java.util.Base64.getMimeEncoder(76, "\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    String b64 = enc.encodeToString(currentHexBytes);
                    java.nio.file.Files.writeString(file.toPath(), b64, java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    // .der/.ber: rohe Bytes schreiben
                    java.nio.file.Files.write(file.toPath(), currentHexBytes);
                }
            } else {
                // Plain-text ASN.1: schreibe Text
                fileIO.writeFile(file.toPath(), rawArea.getText());
            }
            rememberDirectory(file);
            statusLabel.setText("Gespeichert: " + file.getName());
            return true;
        } catch (ASN1IOException e) {
            showError("Speichern fehlgeschlagen", e.getMessage());
            statusLabel.setText("Fehler beim Speichern: " + file.getName());
            return false;
        } catch (java.io.IOException e) {
            showError("Speichern fehlgeschlagen", e.getMessage());
            statusLabel.setText("Fehler beim Speichern: " + file.getName());
            return false;
        }
    }

    /**
     * Validiert den aktuellen Inhalt vor dem Speichern.
     *
     * <p>Bei Binary-Dateien (BER/DER) wird currentHexBytes decodiert,
     * bei Text-Dateien rawArea.getText().
     */
    private boolean confirmSaveAfterValidation(File file) {
        try {
            String contentToParse;
            if (isBinaryFile) {
                // Binary-Datei: Bytes als Base64 kodieren, dann parsen
                contentToParse = service.encodeToBase64(currentHexBytes);
            } else {
                // Text-Datei: rawArea-Inhalt prüfen
                contentToParse = rawArea.getText();
            }
            service.parse(contentToParse, file != null ? file.toPath() : null);
            return true;
        } catch (ASN1IOException e) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Validierung fehlgeschlagen");
            alert.setHeaderText("Der ASN.1-Inhalt enthält Fehler.");
            alert.setContentText(e.getMessage() + "\n\nTrotzdem speichern?");

            ButtonType saveAnyway = new ButtonType("Trotzdem speichern", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancel = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(saveAnyway, cancel);

            return alert.showAndWait().orElse(cancel) == saveAnyway;
        }
    }

    /**
     * Baut den TreeView-Baum aus dem ASN1Document auf.
     *
     * <p>Prüft ob der Baum BER/DER-dekodierte Knoten enthält und synchronisiert
     * den Hex-Editor falls ja.
     */
    private void buildTreeView(ASN1Document doc) {
        ObservableList<TreeItem<TreeItemContent>> rootItems = FXCollections.observableArrayList();

        for (ASN1Node definition : doc.definitions()) {
            rootItems.add(buildTreeItem(definition));
        }

        // Root-Knoten für TreeView
        TreeItem<TreeItemContent> root = new TreeItem<>(
                new TreeItemContent("ASN.1 Document", null));
        root.setExpanded(true);
        root.getChildren().setAll(rootItems);

        expandTree(root);
        treeView.setRoot(root);
        treeView.setShowRoot(false);

        // BER/DER-Bäume → Hex-Editor synchronisieren
        boolean hasBerNodes = doc.definitions().stream()
                .anyMatch(this::containsBerNodes);
        if (hasBerNodes) {
            syncHexFromTree();
        } else {
        }
    }

    /**
     * Prüft ob ein Knoten oder seine Kinder BER/DER-dekodierte Knoten enthalten (offset >= 0).
     */
    private boolean containsBerNodes(ASN1Node node) {
        if (node.offset() >= 0) {
            return true;
        }
        return node.children().stream().anyMatch(this::containsBerNodes);
    }

    /**
     * Codiert den aktuellen TreeView-Baum zurück in BER/DER-Bytes und aktualisiert den Hex-Editor.
     *
     * <p>Nur für BER/DER-dekodierte Bäume (offset >= 0).
     * Plain-text ASN.1-Bäume werden nicht encodiert.
     */
    private void syncHexFromTree() {
        try {
            ASN1Document doc = getTreeViewDocument();
            if (doc == null || doc.definitions().isEmpty()) {
                return;
            }
            byte[] encoded = com.asn1editor.parser.ASN1BerEncoder.encode(doc.definitions().getFirst());
            currentHexBytes = encoded;
            buildHexEditor(encoded);

            // TreeView-offsets neu berechnen, da encodierte Bytes eine
            // andere Byte-Struktur haben können als die originalen Daten.
            // Andernfalls zeigen die alten offsets auf falsche Bytes.
            ASN1Node encodedRoot = com.asn1editor.parser.ASN1BerDecoder.decode(encoded);
            rebuildTreeViewWithEncodedRoot(encodedRoot);

            hexStatusLabel.setText(encoded.length + " Bytes — synchronisiert");
            statusLabel.setText("TreeView → Hex synchronisiert: " + encoded.length + " Bytes");
        } catch (Exception e) {
            hexStatusLabel.setText("Fehler!");
            statusLabel.setText("TreeView-Synchronisation fehlgeschlagen: " + e.getMessage());
        }
    }

    /**
     * Aktualisiert den TreeView mit einem neu decodierten Root-Knoten,
     * damit die Byte-offsets mit currentHexBytes übereinstimmen.
     */
    private void rebuildTreeViewWithEncodedRoot(ASN1Node encodedRoot) {
        ASN1Document doc = new ASN1Document(encodedRoot);
        ObservableList<TreeItem<TreeItemContent>> rootItems = FXCollections.observableArrayList();

        for (ASN1Node definition : doc.definitions()) {
            rootItems.add(buildTreeItem(definition));
        }

        TreeItem<TreeItemContent> root = new TreeItem<>(
                new TreeItemContent("ASN.1 Document", null));
        root.setExpanded(true);
        root.getChildren().setAll(rootItems);

        expandTree(root);
        treeView.setRoot(root);
        treeView.setShowRoot(false);

        // Nach dem rebuild verweist die Selection noch auf das alte TreeItem.
        // Auswahl zurücksetzen, damit highlightSelectedNode beim nächsten Klick
        // den korrekten Node aus dem neuen TreeView verwendet.
        treeView.getSelectionModel().clearSelection();
    }

    /**
     * Extrahiert das ASN1Document aus dem aktuellen TreeView.
     */
    private ASN1Document getTreeViewDocument() {
        TreeItem<TreeItemContent> root = treeView.getRoot();
        if (root == null || root.getChildren().isEmpty()) {
            return null;
        }
        java.util.List<ASN1Node> definitions = new java.util.ArrayList<>();
        for (TreeItem<TreeItemContent> item : root.getChildren()) {
            if (item.getValue() != null && item.getValue().node() != null) {
                definitions.add(item.getValue().node());
            }
        }
        return definitions.isEmpty() ? null : new ASN1Document(definitions);
    }

    /**
     * Erzeugt einen TreeItem für einen ASN1Node mit allen Kindern.
     */
    private TreeItem<TreeItemContent> buildTreeItem(ASN1Node node) {
        TreeItemContent content = new TreeItemContent(formatNodeDisplay(node), node);
        TreeItem<TreeItemContent> item = new TreeItem<>(content);

        for (ASN1Node child : node.children()) {
            item.getChildren().add(buildTreeItem(child));
        }

        return item;
    }

    /**
     * Klappt einen TreeView-Knoten und alle Unterknoten auf.
     */
    private void expandTree(TreeItem<TreeItemContent> item) {
        item.setExpanded(true);
        for (TreeItem<TreeItemContent> child : item.getChildren()) {
            expandTree(child);
        }
    }

    /**
     * Erstellt einen FileChooser mit den unterstützten ASN.1-Dateitypen und dem zuletzt genutzten Ordner.
     */
    private FileChooser createFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("ASN.1 Dateien", "*.asn", "*.asn1", "*.txt", "*.crmf", "*.der", "*.ber", "*.cer", "*.crt", "*.pem"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Alle Dateien", "*.*"));

        File lastDirectory = getLastDirectory();
        if (lastDirectory != null) {
            fileChooser.setInitialDirectory(lastDirectory);
        }
        return fileChooser;
    }

    private File getLastDirectory() {
        String path = preferences.get(PREF_LAST_DIRECTORY, null);
        if (path == null || path.isBlank()) {
            return null;
        }
        File directory = new File(path);
        return directory.isDirectory() ? directory : null;
    }

    private void rememberDirectory(File file) {
        File directory = file != null ? file.getParentFile() : null;
        if (directory != null && directory.isDirectory()) {
            preferences.put(PREF_LAST_DIRECTORY, directory.getAbsolutePath());
        }
    }

    /**
     * Formatiert die Anzeige eines Knotens im TreeView.
     */
    private String formatNodeDisplay(ASN1Node node) {
        if (isOidNode(node)) {
            return formatOidDisplay(node);
        }
        String value = displayValueOf(node);
        if (value != null && !value.isEmpty()) {
            return node.name() + " = " + value;
        }
        return node.name();
    }

    private boolean isOidNode(ASN1Node node) {
        return "OBJECT IDENTIFIER".equals(node.name());
    }

    private String formatOidDisplay(ASN1Node node) {
        String oid = null;
        for (ASN1Node child : node.children()) {
            if (child.isLeaf() && "oid".equals(child.name())) {
                oid = child.value();
                break;
            }
        }
        if (oid == null) {
            return "OBJECT IDENTIFIER";
        }
        // Suche friendly name aus dem value-Feld (formatiert durch Decoder)
        for (ASN1Node child : node.children()) {
            if (child.isLeaf() && "value".equals(child.name()) && child.value() != null) {
                String val = child.value();
                // Bekannter OID: Wert ist z.B. "2.5.4.10 (organizationName (X.520 DN component))"
                // Unbekannter OID: Wert ist nur der rohe oid-String
                int openParen = val.indexOf(" (");
                if (openParen > 0) {
                    // Finde das letzte ')' (Ausgleich verschachtelter Klammern)
                    int closeParen = val.lastIndexOf(")");
                    if (closeParen > openParen) {
                        String name = val.substring(openParen + 2, closeParen);
                        return oid + " " + name;
                    }
                }
                return oid;
            }
        }
        // Fallback: kein value-Kind gefunden
        return oid;
    }

    private String displayValueOf(ASN1Node node) {
        if (node.value() != null && !node.value().isEmpty()) {
            return node.value();
        }
        for (ASN1Node child : node.children()) {
            if (child.isLeaf() && "value".equals(child.name())) {
                return child.value();
            }
        }
        return null;
    }

    /**
     * Schließt das Programm.
     */
    @FXML
    public void handleExit() {
        javafx.application.Platform.exit();
    }

    /**
     * Zeigt einen About-Dialog an.
     */
    @FXML
    public void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Über ASN.1 Editor");
        alert.setHeaderText(null);
        alert.setContentText("ASN.1 Editor v1.0\n"
                + "Ein einfacher Editor zum Visualisieren und Bearbeiten von ASN.1-Dateien.");
        alert.showAndWait();
    }

    // ─── Methoden für Bearbeitung ────────────────────────────────────────

    @FXML
    public void handleRefresh() {
        try {
            ASN1Document doc = service.parse(rawArea.getText(), currentFile != null ? currentFile.toPath() : null);
            buildTreeView(doc);
            detailsArea.clear();
            statusLabel.setText("Aktualisiert" + (currentFile != null ? ": " + currentFile.getName() : ""));
        } catch (ASN1IOException e) {
            showError("Aktualisieren fehlgeschlagen", e.getMessage());
            statusLabel.setText("Aktualisieren fehlgeschlagen" + (currentFile != null ? ": " + currentFile.getName() : ""));
        }
    }

    @FXML
    public void handleUndo() {
        rawArea.undo();
    }

    @FXML
    public void handleRedo() {
        rawArea.redo();
    }

    @FXML
    public void handleCut() {
        rawArea.cut();
    }

    @FXML
    public void handleCopy() {
        rawArea.copy();
    }

    @FXML
    public void handlePaste() {
        rawArea.paste();
    }

    // ─── Hex-Editor ──────────────────────────────────────────────────────

    private static final int HEX_BYTES_PER_LINE = 16;
    private static final int HEX_COLUMN_WIDTH = HEX_BYTES_PER_LINE * 3 - 1;
 
    /**
     * Zeigt den Hex-Inhalt von Byte-Daten im Hex-Editor an.
     *
     * <p>Format: sechzehn Hex-Bytes pro Zeile, danach durch Leerzeichen getrennt
     * die ASCII-Darstellung. Nicht druckbare Zeichen werden als Punkt angezeigt.
     */
    private void buildHexEditor(byte[] data) {
        hexEditorArea.setHexData(data);
        hexStatusLabel.setText(data.length + " Bytes");
        hexDirty = false;
    }

    /**
     * Aktualisiert die Hex-Statusanzeige basierend auf dem Dirty-Status.
     */
    private void updateHexStatus() {
        if (currentHexBytes == null) {
            return;
        }
        if (hexDirty) {
            hexStatusLabel.setText(currentHexBytes.length + " Bytes — Änderungen nicht gespeichert");
        } else {
            hexStatusLabel.setText(currentHexBytes.length + " Bytes — synchronisiert");
        }
    }

    /**
     * Plant die automatische Hex → TreeView-Synchronisation nach AUTO_SYNC_DELAY_MS.
     *
     * <p>Setzt einen bestehenden Timer zurück, wenn bereits einer läuft.
     */
    private void scheduleAutoSync() {
        if (autoSyncTimer == null || currentHexBytes == null) {
            return;
        }
        // Bestehenden Timer zurücksetzen
        autoSyncTimer.purge();
        autoSyncTimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                executeAutoSync();
            }
        }, AUTO_SYNC_DELAY_MS);
    }

    /**
     * Führt die automatische Hex → TreeView-Synchronisation aus.
     *
     * <p>Wird nach AUTO_SYNC_DELAY_MS Inaktivität im Hex-Editor aufgerufen.
     */
    private void executeAutoSync() {
        if (!hexDirty || currentHexBytes == null) {
            return;
        }
        try {
            byte[] bytes = parseHexEditorText(hexEditorArea.getText().trim());
            ASN1Node root = com.asn1editor.parser.ASN1BerDecoder.decode(bytes);
            ASN1Document doc = new ASN1Document(root);
            buildTreeView(doc);
            currentHexBytes = bytes;
            buildHexEditor(bytes);
            hexDirty = false;
            hexStatusLabel.setText(bytes.length + " Bytes — automatisch synchronisiert");
            statusLabel.setText("Hex automatisch synchronisiert: " + bytes.length + " Bytes");
        } catch (Exception e) {
            hexStatusLabel.setText("Fehler!");
            statusLabel.setText("Automatische Synchronisation fehlgeschlagen: " + e.getMessage());
        }
    }

    /**
     * Liest das Hex-Editor-Format zurück in Bytes.
     *
     * <p>Ausgewertet wird pro Zeile nur die linke Hex-Spalte. Die rechts stehende
     * ASCII-Darstellung wird ignoriert.
     *
     * @throws IllegalArgumentException wenn ungültige Hex-Zeichen oder ungerade Anzahl
     */
    private byte[] parseHexEditorText(String text) {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        String[] lines = text.split("\\R");

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            String hexPart = line.length() > HEX_COLUMN_WIDTH
                    ? line.substring(0, HEX_COLUMN_WIDTH)
                    : line;
            String compactHex = hexPart.replaceAll("\\s+", "");

            if (compactHex.isEmpty()) {
                continue;
            }
            if (!compactHex.matches("(?i)[0-9a-f]+")) {
                // Finde die erste ungültige Stelle
                int invalidPos = -1;
                for (int i = 0; i < compactHex.length(); i++) {
                    char c = compactHex.charAt(i);
                    if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                        invalidPos = i;
                        break;
                    }
                }
                String charDesc = invalidPos >= 0
                        ? String.format("ungültiges Zeichen '%s' (Position %d in Zeile %d)",
                                Character.isISOControl(compactHex.charAt(invalidPos)) ? "<Steuerzeichen>"
                                        : String.valueOf(compactHex.charAt(invalidPos)),
                                invalidPos + 1, lineIndex + 1)
                        : "ungültige Zeichen";
                throw new IllegalArgumentException("Hex-Zeichenfehler: " + charDesc);
            }
            if (compactHex.length() % 2 != 0) {
                throw new IllegalArgumentException("Ungerade Anzahl Hex-Zeichen in Zeile " + (lineIndex + 1)
                        + " (" + compactHex.length() + " Zeichen; gerade Anzahl erforderlich)");
            }

            for (int i = 0; i < compactHex.length(); i += 2) {
                output.write(Integer.parseInt(compactHex.substring(i, i + 2), 16));
            }
        }

        return output.toByteArray();
    }

    /**
     * Wendet die hexadezimale Bearbeitung an:
     * Hex → Bytes → BER/DER-Dekodierung → TreeView aktualisieren.
     *
     * <p>Fehlerbehandlung:
     * <ul>
     *   <li>Ungültige Hex-Zeichen → IllegalArgumentException mit Position</li>
     *   <li>Ungerade Hex-Anzahl → IllegalArgumentException mit Zeilennummer</li>
     *   <li>Ungültige BER/DER-Struktur → BERDecodeException mit Byte-Offset</li>
     * </ul>
     */
    @FXML
    public void handleApplyHex() {
        // Auto-Sync-Timer abbrechen (Debounce überspringen)
        if (autoSyncTimer != null) {
            autoSyncTimer.cancel();
        }

        byte[] hexData = hexEditorArea.getHexData();
        if (hexData == null || hexData.length == 0) {
            showError("Hex-Editor", "Kein Hex-Inhalt zum Anwenden.");
            return;
        }

        try {
            byte[] bytes = hexData;

            // BER/DER-dekodieren
            ASN1Node root = com.asn1editor.parser.ASN1BerDecoder.decode(bytes);
            ASN1Document doc = new ASN1Document(root);

            // TreeView aktualisieren
            buildTreeView(doc);
            detailsArea.clear();
            currentHexBytes = bytes;
            buildHexEditor(bytes);
            hexDirty = false;

            hexStatusLabel.setText(bytes.length + " Bytes — angewendet");
            statusLabel.setText("Hex angewendet: " + bytes.length + " Bytes");
        } catch (BERDecodeException e) {
            String detail = String.format("Die hexadezimalen Daten enthalten keine gültige BER/DER-Struktur.\n" +
                    "Fehler: %s", e.getMessage());
            showError("BER/DER-Decodierung fehlgeschlagen", detail);
            hexStatusLabel.setText("Fehler!");
        } catch (IllegalArgumentException e) {
            String detail = String.format("Ungültiger Hex-Inhalt:\n%s", e.getMessage());
            showError("Hex-Editor", detail);
            hexStatusLabel.setText("Fehler!");
        } catch (Exception e) {
            String detail = String.format("Unerwarteter Fehler:\n%s", e.getMessage());
            showError("Hex anwenden fehlgeschlagen", detail);
            hexStatusLabel.setText("Fehler!");
        }
    }

    /**
     * Lädt den Hex-Inhalt neu aus den aktuellen Original-Bytes.
     * Damit wird die Hex-Ansicht auf den aktuellen Dateistand zurückgesetzt.
     */
    @FXML
    public void handleReloadHexFromTree() {
        // Auto-Sync-Timer abbrechen
        if (autoSyncTimer != null) {
            autoSyncTimer.cancel();
        }

        if (currentHexBytes != null) {
            buildHexEditor(currentHexBytes);
            hexStatusLabel.setText(currentHexBytes.length + " Bytes — neu geladen");
            statusLabel.setText("Hex neu geladen");
        } else if (currentFile != null) {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(currentFile.toPath());
                currentHexBytes = bytes;
                buildHexEditor(bytes);
                hexStatusLabel.setText(bytes.length + " Bytes — neu geladen");
                statusLabel.setText("Hex neu geladen: " + currentFile.getName());
            } catch (java.io.IOException e) {
                showError("Hex neu laden fehlgeschlagen", e.getMessage());
            }
        } else {
            showError("Hex neu laden", "Keine Datei geladen.");
        }
    }

    /**
     * Zeigt einen Error-Dialog an.
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Zeigt Hex-Inhalt einer binären ASN.1-Datei an.
     */
    private void showBinaryHex(String fileName, String hexContent) {
        byte[] bytes = ASN1Service.fromHexString(hexContent);
        rawArea.setText(hexContent);
        currentHexBytes = bytes;
        isBinaryFile = true;
        buildHexEditor(bytes);
        statusLabel.setText("Binäre ASN.1-Datei (" + fileName + ") — Hex-Darstellung");

        // Warnung anzeigen
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Binäre ASN.1-Datei erkannt");
        alert.setHeaderText(null);
        alert.setContentText("Die Datei '" + fileName + "' ist eine binäre ASN.1-Datei (DER/BER).\n\n"
                + "Inhalt wird als Hex-String angezeigt.\n"
                + "Kein ASN.1-Baum verfügbar.");
        alert.showAndWait();
    }
}
