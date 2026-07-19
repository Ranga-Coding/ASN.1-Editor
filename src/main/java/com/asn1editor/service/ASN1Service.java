package com.asn1editor.service;

import com.asn1editor.model.ASN1Document;
import com.asn1editor.model.ASN1Node;
import com.asn1editor.parser.ASN1BerDecoder;
import com.asn1editor.parser.ASN1Lexer;
import com.asn1editor.parser.ASN1ParseException;
import com.asn1editor.parser.ASN1Parser;
import com.asn1editor.parser.BERDecodeException;
import com.asn1editor.parser.Token;

import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * Business-Logic-Service für ASN.1-Verarbeitung.
 *
 * <p>Dient als Brücke zwischen Parser und Benutzeroberfläche.
 * Die UI ruft {@link #parse(String)} auf und erhält ein {@link ASN1Document}
 * oder eine {@link ASN1IOException}.
 *
 * <p>Erkennt automatisch drei Dateiformate:
 * <ol>
 *   <li><b>Plain-text ASN.1</b> (BER-Textformat) — wird direkt geparst</li>
 *   <li><b>Base64-kodierte ASN.1</b> (PEM oder roher Base64-Content) — decodiert und geparst</li>
 *   <li><b>Binäre ASN.1 (DER/BER)</b> — als Hex-String dargestellt</li>
 * </ol>
 *
 * <p>Verantwortlichkeiten:
 * <ul>
 *   <li>Dateiformat-Erkennung → {@link #detectFormat(String)}</li>
 *   <li>Base64-Erkennung → automatische Dekodierung von Base64-kodierten ASN.1-Dateien (inkl. PEM)</li>
 *   <li>Binär-Erkennung → Hex-Darstellung für DER/BER-Dateien</li>
 *   <li>Lexer → Tokenisierung</li>
 *   <li>Parser → AST-Erzeugung</li>
 *   <li>Fehlerbehandlung → einheitliche {@link ASN1IOException}</li>
 * </ul>
 */
public class ASN1Service {

    /**
     * Unterstützte ASN.1-Dateiformate.
     */
    public enum FileFormat {
        /** BER-Textformat (klartext ASN.1, z.B. RFC 2252) */
        ASN1_TEXT,
        /** Base64-kodiert (PEM-Format oder roher Base64-Content) */
        BASE64,
        /** Binär (DER/BER) */
        BINARY
    }

    /**
     * Parsed den gegebenen ASN.1-Inhalt und liefert ein Dokument.
     *
     * <p>Erkennt automatisch das Dateiformat:
     * <ul>
     *   <li>Plain-text ASN.1 (enthält {{@code ::=}}) → direkt geparst</li>
     *   <li>Base64-kodiert (PEM oder roher Base64-Content) → decodiert und geparst</li>
     *   <li>Binär (DER/BER) → Hex-Darstellung → wird im Editor angezeigt, nicht geparst</li>
     *   <li>Hex-String (z.B. von vorheriger Base64→Binär-Konvertierung) → wird als Binär angezeigt</li>
     * </ul>
     *
     * @param source der ASN.1-Inhalt (Text, Base64, Binär oder Hex)
     * @return das geparste ASN1Document
     * @throws ASN1IOException wenn das Parsing fehlschlägt oder Binär erkannt wird.
     *                         Bei Binärdateien enthält die Exception den Hex-String über {@link ASN1IOException#getHexContent()}.
     */
    public ASN1Document parse(String source) throws ASN1IOException {
        return parse(source, null);
    }

    /**
     * Parsed den gegebenen ASN.1-Inhalt und loggt zusätzlich den Dateipfad.
     *
     * @param source der ASN.1-Inhalt (Text, Base64, Binär oder Hex)
     * @param path Pfad der geladenen Datei, falls bekannt
     * @return das geparste ASN1Document
     * @throws ASN1IOException wenn das Parsing fehlschlägt
     */
    public ASN1Document parse(String source, Path path) throws ASN1IOException {
        FileFormat format = detectFormat(source);
        ASN1Logger.logFileLoad(path, format.name(), source != null ? source.length() : 0);

        String processedSource = null;
        byte[] binaryData = null;
        boolean isBinary = false;
        switch (format) {
            case ASN1_TEXT -> processedSource = source;
            case BASE64 -> {
                int originalLen = source.length();
                processedSource = decodeBase64IfNeeded(source);
                // Prüfen: wurde decodiert zu Hex (Binär) oder Text?
                boolean decodedToHex = isHexString(processedSource);
                ASN1Logger.logDecoding(processedSource.length() != source.length(), originalLen, processedSource.length());
                if (decodedToHex) {
                    ASN1Logger.info("Base64 decodierte zu Binär-Daten (" + (originalLen / 4 * 3) + " Bytes), Hex-Darstellung");
                    binaryData = fromHexString(processedSource);
                    ASN1Logger.logBinarySnippet(binaryData, 64);
                    isBinary = true;
                }
            }
            case BINARY -> {
                // Roh-Binärdaten → Hex-Darstellung für Anzeige
                binaryData = source.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                String hex = toHexString(binaryData);
                ASN1Logger.logBinarySnippet(binaryData, 64);
                isBinary = true;
            }
            default -> processedSource = source;
        }

        ASN1Node root;
        if (isBinary) {
            // Binärdaten → BER/DER-Decodierung
            try {
                root = ASN1BerDecoder.decode(binaryData);
                if (root.name().equals("ASN.1 BER Document")) {
                    ASN1Logger.logParsing(true, "BER-Decodierung: Container mit " + root.children().size() + " Root-Nodes");
                } else {
                    ASN1Logger.logParsing(true, "BER-Decodierung: Einzelne Definition: " + root.name());
                }
                return new ASN1Document(root);
            } catch (BERDecodeException e) {
                ASN1Logger.logParsing(false, "BER-Decodierung fehlgeschlagen: " + e.getMessage());
                // BER-Decodierung fehlgeschlagen → Exception mit Hex-Inhalt
                throw new ASN1IOException(
                        "Binäre ASN.1-Datei (DER/BER) erkannt, Decodierung fehlgeschlagen: " + e.getMessage(),
                        null,
                        toHexString(binaryData), // Hex-Inhalt für GUI
                        e);
            }
        }

        try {
            List<Token> tokens = new ASN1Lexer(processedSource).tokenize();
            root = new ASN1Parser(tokens).parse();
            // Parser kann einen "ASN.1 Document" Container zurückgeben (bei mehreren Definitions)
            // oder einen einzelnen Definition-Knoten.
            if (root.name().equals("ASN.1 Document")) {
                ASN1Logger.logParsing(true, "Container mit " + root.children().size() + " Root-Nodes");
                return new ASN1Document(root.children());
            }
            ASN1Logger.logParsing(true, "Einzelne Definition: " + root.name());
            return new ASN1Document(root);
        } catch (ASN1ParseException e) {
            ASN1Logger.logParsing(false, e.getMessage());
            throw new ASN1IOException(
                    "Parsing-Fehler: " + e.getMessage(),
                    null,
                    e);
        }
    }

    /**
     * Prüft, ob ein String ein Hex-String ist (nur 0-9, A-F, a-f, Leerzeichen).
     *
     * @param source der zu prüfende String
     * @return true wenn der String nur Hex-Zeichen enthält
     */
    public static boolean isHexString(String source) {
        if (source == null || source.isEmpty()) {
            return false;
        }
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            boolean isHex = (c >= '0' && c <= '9') ||
                            (c >= 'A' && c <= 'F') ||
                            (c >= 'a' && c <= 'f');
            if (!isHex) {
                return false;
            }
        }
        return true;
    }

    /**
     * Erkennt das Format des gegebenen ASN.1-Inhalts.
     *
     * <p>Heuristik in Priorität:
     * <ol>
     *   <li>PEM-Header {{@code -----BEGIN ...-----}} → {{@code BASE64}}</li>
     *   <li>Inhalt enthält {{@code ::=}} → {{@code ASN1_TEXT}}</li>
     *   <li>Nur Base64-Zeichen (kein {{@code ::=}}) → {{@code BASE64}}</li>
     *   <li>Enthält Null-Bytes oder hohe Bytes (>= 128) → {{@code BINARY}}</li>
     *   <li>Sonst → {{@code ASN1_TEXT}}</li>
     * </ol>
     *
     * @param source der zu prüfende ASN.1-Inhalt
     * @return das erkannte Format
     */
    public FileFormat detectFormat(String source) {
        if (source == null || source.isEmpty()) {
            return FileFormat.ASN1_TEXT;
        }

        // 1. PEM-Header erkennen → BASE64
        if (source.contains("-----BEGIN") && source.contains("-----END")) {
            return FileFormat.BASE64;
        }

        // 2. ASN.1-Syntax-Operator → Text
        if (source.contains("::=")) {
            return FileFormat.ASN1_TEXT;
        }

        // 3. Nur Base64-Zeichen → BASE64
        if (isBase64Only(source)) {
            return FileFormat.BASE64;
        }

        // 4. Null-Bytes oder hohe Bytes → Binär
        for (int i = 0; i < source.length() && i < 2048; i++) {
            char c = source.charAt(i);
            if (c == '\0' || c >= 128) {
                return FileFormat.BINARY;
            }
        }

        // 5. Sonst → Text (vielleicht unvollständig oder fehlerhaft)
        return FileFormat.ASN1_TEXT;
    }

    /**
     * Liest eine Datei als Binärdaten und gibt den Inhalt als Hex-String zurück.
     *
     * <p>Verwendet ISO-8859-1, um jedes Byte 1:1 abzubilden.
     *
     * @param path Pfad zur Datei
     * @return Hex-Darstellung des Dateiinhalts
     */
    public byte[] readFileBinary(java.nio.file.Path path) throws ASN1IOException {
        try {
            byte[] data = java.nio.file.Files.readAllBytes(path);
            ASN1Logger.logFileLoad(path, FileFormat.BINARY.name(), data.length);
            ASN1Logger.logBinarySnippet(data, 64);
            return data;
        } catch (java.io.IOException e) {
            ASN1Logger.logError("Binärdatei lesen", e);
            throw new ASN1IOException(
                    "Datei konnte nicht gelesen werden: ",
                    path != null ? path.toString() : null,
                    e);
        }
    }

    /**
     * Konvertiert einen Byte-Array in einen Hex-String.
     *
     * @param bytes die zu konvertierenden Bytes
     * @return Hex-String, z.B. "30 80 06 09"
     */
    public static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Konvertiert einen Hex-String zurück in ein Byte-Array.
     *
     * @param hex Hex-String, z.B. "30 80 06 09"
     * @return Byte-Array
     */
    public static byte[] fromHexString(String hex) {
        hex = hex.replaceAll("\\s+", "");
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Prüft, ob ein Byte-Array nur ASCII-Zeichen (0-127) enthält.
     *
     * @param bytes der zu prüfende Byte-Array
     * @return true wenn alle Bytes im Bereich 0-127
     */
    private static boolean isPureAscii(byte[] bytes) {
        for (byte b : bytes) {
            if (b < 0 || b > 127) {
                return false;
            }
        }
        return true;
    }

    // ─── Base64-Erkennung und Dekodierung ──────────────────────────────

    /**
     * Erkennt Base64-kodierte ASN.1-Inhalte (inkl. PEM-Format) und decodiert sie.
     *
     * <p>PEM-Format:
     * <pre>
     * -----BEGIN ...-----
     * Base64-Inhalt...
     * -----END ...-----
     * </pre>
     *
     * <p>Heuristik:
     * <ol>
     *   <li>PEM-Header/Footer erkennen und Base64-Content extrahieren</li>
     *   <li>Sonst: roher Base64-Content (nur Base64-Zeichen + Whitespace)</li>
     * </ol>
     *
     * <p>Nach dem Decodieren wird geprüft, ob der Inhalt Text (ASCII, 0-127) oder Binär ist:
     * <ul>
     *   <li><b>ASCII</b>: wird als UTF-8-String zurückgegeben (für ASN.1-Text)</li>
     *   <li><b>Binär</b>: wird als Hex-String zurückgegeben (für BER/DER-Dateien wie .crmf)</li>
     * </ul>
     *
     * @param source der zu prüfende ASN.1-Quelltext
     * @return der Originaltext, die Base64-decodierte UTF-8-Version oder Hex-String
     */
    /**
     * Decodiert Base64/PEM-Inhalt und gibt den decodierten String zurück.
     *
     * <p>Bei Binärdaten (BER/DER) wird ein Hex-String zurückgegeben.
     *
     * @param source der zu prüfende ASN.1-Quelltext
     * @return decodierter Inhalt oder Original bei Fehler
     */
    public String decodeBase64IfNeeded(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }

        // PEM-Format: -----BEGIN ...----- bis -----END ...-----
        String base64Content = extractPemContent(source);
        if (base64Content != null) {
            return decodeBase64Safe(base64Content);
        }

        // Roh-Base64: Nur Base64-Zeichen (A-Z, a-z, 0-9, +, /, =, Whitespace)
        // UND kein ASN.1 ::=-Operator
        if (!isBase64Only(source)) {
            return source;
        }
        if (source.contains("::=")) {
            return source;
        }

        // Base64-decodieren
        return decodeBase64Safe(source);
    }

    /**
     * Extrahiert Base64-Content aus PEM-Format.
     *
     * @param source PEM-formatierter Text
     * @return Base64-Content ohne Header/Footer, oder null wenn kein PEM
     */
    private String extractPemContent(String source) {
        int beginIdx = source.indexOf("-----BEGIN");
        int endIdx = source.indexOf("-----END");
        if (beginIdx == -1 || endIdx == -1 || endIdx <= beginIdx) {
            return null;
        }

        // Alles zwischen -----BEGIN und -----END extrahieren
        int contentStart = source.indexOf('\n', beginIdx);
        if (contentStart == -1) {
            contentStart = beginIdx;
        } else {
            contentStart++; // nach dem Newline
        }
        return source.substring(contentStart, endIdx).trim();
    }

    /**
     * Decodiert einen Base64-String sicher.
     *
     * <p>Prüft nach dem Decodieren, ob der Inhalt Text (ASCII) oder Binär ist:
     * <ul>
     *   <li>ASCII → UTF-8-String (für ASN.1-Text)</li>
     *   <li>Binär → Hex-String (für BER/DER-Dateien wie .crmf)</li>
     * </ul>
     */
    private String decodeBase64Safe(String base64) {
        try {
            String clean = base64.replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(clean);

            // Prüfen: Text oder Binär?
            if (isPureAscii(decoded)) {
                return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            } else {
                // Binär (BER/DER) → Hex-Darstellung
                return toHexString(decoded);
            }
        } catch (IllegalArgumentException e) {
            ASN1Logger.warn("Base64-Decodierung fehlgeschlagen: " + e.getMessage());
            return base64;
        }
    }

    /**
     * Prüft, ob der String ausschließlich aus Base64-Zeichen und Whitespace besteht.
     */
    private boolean isBase64Only(String source) {
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            boolean isBase64Char =
                    (c >= 'A' && c <= 'Z') ||
                    (c >= 'a' && c <= 'z') ||
                    (c >= '0' && c <= '9') ||
                    c == '+' || c == '/' || c == '=';
            if (!isBase64Char) {
                return false;
            }
        }
        return true;
    }

    /**
     * Konvertiert einen Byte-Array zu einem Hex-String.
     */
    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    /**
     * Validiert den ASN.1-Quelltext (delegiert an den Parser).
     *
     * @param source der ASN.1-Quelltext
     * @return true, wenn der Text syntaktisch gültig ist
     */
    public boolean validate(String source) {
        try {
            parse(source);
            return true;
        } catch (ASN1IOException e) {
            return false;
        }
    }

    /**
     * Kodiert einen Byte-Array als Base64-String.
     * Wird verwendet, um Binary-Bytes vor der ASN.1-Validierung
     * in eine parsebare Textform zu bringen.
     */
    public String encodeToBase64(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(bytes);
    }
}
