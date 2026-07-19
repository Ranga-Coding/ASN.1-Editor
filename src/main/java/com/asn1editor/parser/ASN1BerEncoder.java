package com.asn1editor.parser;

import com.asn1editor.model.ASN1Node;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * BER/DER-Encoder für ASN.1-Daten.
 *
 * <p>Codiert einen {@link ASN1Node}-Baum zurück in einen binären BER/DER-Stream
 * (TLV-Format: Tag-Length-Value).
 *
 * <p>Verwendet die vom {@link ASN1BerDecoder} gespeicherten Hex-Werte zur
 * verlustfreien Rekonstruktion der Original-Bytes.
 *
 * <p>Untersützte Typen:
 * <ul>
 *   <li>Konstruktive Typen: SEQUENCE, SET, APPLICATION, CONTEXT-SPECIFIC</li>
 *   <li>Primitive Typen: INTEGER, BOOLEAN, BIT STRING, OCTET STRING, NULL,
 *       OID, UTF8String, PrintableString, IA5String, BMPString, UTCTime, GeneralizedTime</li>
 * </ul>
 */
public final class ASN1BerEncoder {

    private ASN1BerEncoder() {
        // Nicht instanziiert
    }

    // ─── UNIVERSAL Tag-Konstanten ──────────────────────────────────────

    private static final int TAG_BOOLEAN = 0x01;
    private static final int TAG_INTEGER = 0x02;
    private static final int TAG_BIT_STRING = 0x03;
    private static final int TAG_OCTET_STRING = 0x04;
    private static final int TAG_NULL = 0x05;
    private static final int TAG_OID = 0x06;
    private static final int TAG_UTF8STRING = 0x0C;
    private static final int TAG_PRINTABLESTRING = 0x13;
    private static final int TAG_IA5STRING = 0x16;
    private static final int TAG_UTCTIME = 0x17;
    private static final int TAG_GENERALIZEDTIME = 0x18;
    private static final int TAG_BMPSTRING = 0x1E;
    private static final int TAG_SEQUENCE = 0x10;
    private static final int TAG_SET = 0x11;

    /**
     * Codiert einen ASN1Node-Baum in einen BER/DER-Byte-Stream.
     *
     * @param root der Wurzelknoten des ASN.1-Baums
     * @return die BER/DER-codierten Bytes
     * @throws ASN1EncodeException wenn der Baum nicht codiert werden kann
     */
    public static byte[] encode(ASN1Node root) {
        if (root == null) {
            throw new ASN1EncodeException("Knoten darf nicht null sein.");
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            encodeNode(root, baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new ASN1EncodeException("Encode-Fehler: " + e.getMessage());
        }
    }

    /**
     * Codiert einen einzelnen ASN1Node in TLV-Bytes.
     */
    private static void encodeNode(ASN1Node node, ByteArrayOutputStream out) throws IOException {
        encodeTag(node, out);

        boolean isConstructed = isConstructedType(node);
        byte[] valueBytes;

        if (isConstructed) {
            // Konstruktive Typen: Kinder rekursiv codieren
            ByteArrayOutputStream valueOut = new ByteArrayOutputStream();
            for (ASN1Node child : node.children()) {
                encodeNode(child, valueOut);
            }
            valueBytes = valueOut.toByteArray();
        } else {
            // Primitive Typen: Wert aus dem hex-Feld rekonstruieren
            valueBytes = getPrimitiveValueBytes(node);
        }

        encodeLength(valueBytes.length, out);
        out.write(valueBytes);
    }

    /**
     * Prüft, ob ein Knoten ein konstruktiver Typ ist.
     *
     * <p>Ein Knoten ist konstruktiv, wenn:
     * <ul>
     *   <li>mindestens ein Kind kein Blatt ist (TLV-Element), oder</li>
     *   <li>der Name "[CONSTRUCTED]" enthält (SEQUENCE, SET, APPLICATION, CONTEXT)</li>
     * </ul>
     */
    private static boolean isConstructedType(ASN1Node node) {
        if (node.children().stream().anyMatch(child -> !child.isLeaf())) {
            return true;
        }
        // Konstruktive Typen ohne Kinder (z.B. leere SEQUENCE) erkennen
        return node.name().contains("[CONSTRUCTED]");
    }

    /**
     * Codiert das Tag-Byte eines ASN1Node.
     *
     * <p>Tag-Byte = Class (2 Bit) | Constructed-Flag (1 Bit) | Tag-Number (5 Bit)
     */
    private static void encodeTag(ASN1Node node, ByteArrayOutputStream out) throws IOException {
        TagInfo tagInfo = parseTagInfo(node.name());
        int tagByte = (tagInfo.tagClass() << 6) | ((tagInfo.constructed() ? 1 : 0) << 5) | tagInfo.tagNumber();
        out.write(tagByte);
    }

    /**
     * Extrahiert Tag-Informationen aus dem Knoten-Namen.
     *
     * <p>Format: "SEQUENCE [CONSTRUCTED]", "INTEGER", "APPLICATION[5]", "CONTEXT[0] [CONSTRUCTED]"
     */
    private static TagInfo parseTagInfo(String name) {
        // Trimme "[CONSTRUCTED]" Suffix
        String baseName = name;
        boolean constructed = false;
        if (name.endsWith(" [CONSTRUCTED]")) {
            baseName = name.substring(0, name.length() - " [CONSTRUCTED]".length());
            constructed = true;
        }

        // APPLICATION[x]
        if (baseName.startsWith("APPLICATION[")) {
            int closeBracket = baseName.indexOf(']');
            if (closeBracket > 0) {
                int tagNumber = Integer.parseInt(baseName.substring("APPLICATION[".length(), closeBracket));
                return new TagInfo(1, tagNumber, constructed, baseName);
            }
        }

        // CONTEXT[x]
        if (baseName.startsWith("CONTEXT[")) {
            int closeBracket = baseName.indexOf(']');
            if (closeBracket > 0) {
                int tagNumber = Integer.parseInt(baseName.substring("CONTEXT[".length(), closeBracket));
                return new TagInfo(2, tagNumber, constructed, baseName);
            }
        }

        // PRIVATE[x]
        if (baseName.startsWith("PRIVATE[")) {
            int closeBracket = baseName.indexOf(']');
            if (closeBracket > 0) {
                int tagNumber = Integer.parseInt(baseName.substring("PRIVATE[".length(), closeBracket));
                return new TagInfo(3, tagNumber, constructed, baseName);
            }
        }

        // UNIVERSAL-Typen
        int tagNumber = universalTagNumber(baseName);
        return new TagInfo(0, tagNumber, constructed, baseName);
    }

    /**
     * Mapping von Typname zu UNIVERSAL-Tag-Number.
     */
    private static int universalTagNumber(String name) {
        return switch (name) {
            case "BOOLEAN" -> TAG_BOOLEAN;
            case "INTEGER" -> TAG_INTEGER;
            case "BIT STRING" -> TAG_BIT_STRING;
            case "OCTET STRING" -> TAG_OCTET_STRING;
            case "NULL" -> TAG_NULL;
            case "OBJECT IDENTIFIER" -> TAG_OID;
            case "UTF8String" -> TAG_UTF8STRING;
            case "PrintableString" -> TAG_PRINTABLESTRING;
            case "IA5String" -> TAG_IA5STRING;
            case "UTCTime" -> TAG_UTCTIME;
            case "GeneralizedTime" -> TAG_GENERALIZEDTIME;
            case "BMPString" -> TAG_BMPSTRING;
            case "SEQUENCE" -> TAG_SEQUENCE;
            case "SET" -> TAG_SET;
            default -> 0; // Fallback — sollte nicht vorkommen
        };
    }

    /**
     * Codiert eine BER/DER-Länge.
     *
     * <p>Kurze Form: 1 Byte, Wert = Länge (0–127)
     * <p>Lange Form: 1. Byte = 0x80+n, n=Anzahl der folgenden Bytes
     */
    private static void encodeLength(int length, ByteArrayOutputStream out) throws IOException {
        if (length < 128) {
            out.write(length);
        } else {
            byte[] lenBytes = intToBytes(length);
            out.write(0x80 | lenBytes.length);
            out.write(lenBytes);
        }
    }

    /**
     * Konvertiert einen int in einen Byte-Array (big-endian, minimal).
     */
    private static byte[] intToBytes(int value) {
        if (value == 0) {
            return new byte[]{0};
        }
        int numBytes = (32 - Integer.numberOfLeadingZeros(value) + 7) / 8;
        byte[] result = new byte[numBytes];
        for (int i = numBytes - 1; i >= 0; i--) {
            result[i] = (byte) (value & 0xFF);
            value >>>= 8;
        }
        return result;
    }

    /**
     * Rekonstruiert die Wert-Bytes eines primitiven Typs aus dem hex-Feld.
     *
     * <p>Für NULL-Typen (kein hex-Feld) wird ein leeres Byte-Array zurückgegeben.
     */
    private static byte[] getPrimitiveValueBytes(ASN1Node node) {
        for (ASN1Node child : node.children()) {
            if ("hex".equals(child.name()) && child.value() != null) {
                return hexToBytes(child.value());
            }
        }
        // NULL-Typ: leere Value-Bytes
        if ("NULL".equals(node.name())) {
            return new byte[0];
        }
        throw new ASN1EncodeException("Kein hex-Wert für primitiven Knoten gefunden: " + node.name());
    }

    /**
     * Konvertiert einen Hex-String zu einem Byte-Array.
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] result = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            result[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    | Character.digit(hex.charAt(i + 1), 16));
        }
        return result;
    }

    // ─── TagInfo-Referenz ──────────────────────────────────────────────
    // Verwendet den gemeinsamen Record aus TagInfo.java

    /**
     * Exception für BER/DER-Encoding-Fehler.
     */
    public static class ASN1EncodeException extends RuntimeException {
        ASN1EncodeException(String message) {
            super(message);
        }
    }
}
