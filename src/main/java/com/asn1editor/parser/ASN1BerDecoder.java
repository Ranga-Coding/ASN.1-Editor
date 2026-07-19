package com.asn1editor.parser;

import com.asn1editor.model.ASN1Node;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BER/DER-Decoder für binäre ASN.1-Dateien.
 *
 * <p>Dekodiert TLV-Strukturen (Tag-Length-Value) aus Rohdaten und erzeugt einen
 * {@link ASN1Node}-Baum, der wie ein aus Text-ASN.1 gelesener Baum aussieht.
 *
 * <p>Grammatik (vereinfacht, aber praxistauglich für X.509, PKCS#7, CMS, etc.):
 * <pre>
 *   TLV            ::= Tag Length Value
 *   Tag            ::= 1+ Bytes (BER-encoding)
 *   Length         ::= 1 Byte (kurze Form < 128)
 *                    | 1+ Bytes (lange Form: 0x80+n, n=folgende Bytes)
 *   Value          ::= Bytes (primitive) oder TLV-Liste (konstruktiv)
 * </pre>
 *
 * <p>Tag-Klassifizierung:
 * <ul>
 *   <li><b>UNIVERSAL</b>: 0x01 (BOOLEAN), 0x02 (INTEGER), 0x03 (BIT STRING),
 *       0x04 (OCTET STRING), 0x06 (OID), 0x0A (NULL), 0x0C (STRING), 0x10 (SEQUENCE), 0x11 (SET)</li>
 *   <li><b>APPLICATION</b>: 0x40+ (z.B. 0x40 = X.509 Version)</li>
 *   <li><b>CONTEXT-SPECIFIC</b>: 0x80+ (z.B. 0x80 = tbsCertificate)</li>
 *   <li><b>PRIMITIV</b>: Class-Bit == 0, P-Flag == 0 → primitive</li>
 *   <li><b>KONSTRUKTIV</b>: Class-Bit == 0, P-Flag == 1 → konstruktiv (verschachtelbar)</li>
 * </ul>
 *
 * <p>Design-Entscheidungen:
 * <ul>
 *   <li>Knoten erhalten einen {@code kind}-Tag mit dem ASN.1-Typnamen (SEQUENCE, INTEGER, etc.)</li>
 *   <li>Primitive Werte werden als Hex und als Text (wenn möglich) gespeichert</li>
 *   <li>Konstruktive Typen haben Kinder-Knoten</li>
 * </ul>
 */
public final class ASN1BerDecoder {

    // ─── UNIVERSAL Tag-Konstanten ──────────────────────────────────────

    private static final int TAG_BOOLEAN = 0x01;
    private static final int TAG_INTEGER = 0x02;
    private static final int TAG_BIT_STRING = 0x03;
    private static final int TAG_OCTET_STRING = 0x04;
    private static final int TAG_NULL = 0x05;
    private static final int TAG_OID = 0x06;
    private static final int TAG_UTF8STRING = 0x0C;
    private static final int TAG_NUMERICSTRING = 0x12;
    private static final int TAG_PRINTABLESTRING = 0x13;
    private static final int TAG_T61STRING = 0x14;
    private static final int TAG_IA5STRING = 0x16;
    private static final int TAG_UTCTIME = 0x17;
    private static final int TAG_GENERALIZEDTIME = 0x18;
    private static final int TAG_BMPSTRING = 0x1E;
    private static final int TAG_SEQUENCE = 0x10;
    private static final int TAG_SET = 0x11;
    private static final int INDEFINITE_LENGTH = -1;

    // Vollständige OID-Namenszuordnungen (X.520, BSI, PKCS, RFC)
    private static final Map<String, String> RFC5280_OID_NAMES = Map.ofEntries(
            // ── BSI TR-03109 / PKIMessage ───────────────────────────────
            Map.entry("0.4.0.127.0.7.4.1.1", "bsiCertReqMsgs"),
            Map.entry("0.4.0.127.0.7.4.1.2", "bsiCertRepMsgs"),
            Map.entry("0.4.0.127.0.7.4.1.3", "bsiCertConfMsgs"),
            Map.entry("0.4.0.127.0.7.4.1.4", "bsiCertCRLConfMsgs"),
            Map.entry("0.4.0.127.0.7.4.1.5", "bsiPKIStatusInfo"),
            // ── X.520 / X.501 DN attributes (2.5.4.x) ─────────────────
            Map.entry("2.5.4.1", "domainComponent"),
            Map.entry("2.5.4.2", "unknown"),
            Map.entry("2.5.4.3", "commonName (X.520 DN component)"),
            Map.entry("2.5.4.4", "surname (X.520 DN component)"),
            Map.entry("2.5.4.5", "serialNumber"),
            Map.entry("2.5.4.6", "countryName (X.520 DN component)"),
            Map.entry("2.5.4.7", "localityName (X.520 DN component)"),
            Map.entry("2.5.4.8", "stateOrProvinceName (X.520 DN component)"),
            Map.entry("2.5.4.9", "streetAddress (X.520 DN component)"),
            Map.entry("2.5.4.10", "organizationName (X.520 DN component)"),
            Map.entry("2.5.4.11", "organizationalUnitName (X.520 DN component)"),
            Map.entry("2.5.4.12", "title"),
            Map.entry("2.5.4.13", "description"),
            Map.entry("2.5.4.15", "businessCategory"),
            Map.entry("2.5.4.16", "postalCode"),
            Map.entry("2.5.4.17", "postalAddress"),
            Map.entry("2.5.4.42", "givenName (X.520 DN component)"),
            Map.entry("2.5.4.43", "initials"),
            Map.entry("2.5.4.44", "generationQualifier"),
            Map.entry("2.5.4.65", "pKCS9email"),
            // ── PKCS#1 / RSA (1.2.840.113549.1.1.x) ──────────────────
            Map.entry("1.2.840.113549.1.1.1", "rsaEncryption"),
            Map.entry("1.2.840.113549.1.1.2", "md2WithRSA"),
            Map.entry("1.2.840.113549.1.1.3", "md4WithRSA"),
            Map.entry("1.2.840.113549.1.1.4", "md5WithRSA"),
            Map.entry("1.2.840.113549.1.1.5", "sha1WithRSAEncryption"),
            Map.entry("1.2.840.113549.1.1.6", "dsaWithSHA1"),
            Map.entry("1.2.840.113549.1.1.7", "ideaWithRSA"),
            Map.entry("1.2.840.113549.1.1.8", "sha224WithRSAEncryption"),
            Map.entry("1.2.840.113549.1.1.9", "directRSA"),
            Map.entry("1.2.840.113549.1.1.10", "ecdsaWithSHA1"),
            Map.entry("1.2.840.113549.1.1.11", "sha256WithRSAEncryption"),
            Map.entry("1.2.840.113549.1.1.12", "sha384WithRSAEncryption"),
            Map.entry("1.2.840.113549.1.1.13", "sha512WithRSAEncryption"),
            // ── PKCS#7 / CMS (1.2.840.113549.1.7.x) ──────────────────
            Map.entry("1.2.840.113549.1.7.1", "data"),
            Map.entry("1.2.840.113549.1.7.2", "signedData"),
            Map.entry("1.2.840.113549.1.7.3", "envelopedData"),
            Map.entry("1.2.840.113549.1.7.4", "signedAndEnvelopedData"),
            Map.entry("1.2.840.113549.1.7.5", "digestedData"),
            Map.entry("1.2.840.113549.1.7.6", "encryptedData"),
            Map.entry("1.2.840.113549.1.7.12", "authenticatedData"),
            // ── PKCS#9 / CSR Attributes (1.2.840.113549.1.9.x) ───────
            Map.entry("1.2.840.113549.1.9.1", "emailAddress"),
            Map.entry("1.2.840.113549.1.9.2", "unstructuredName"),
            Map.entry("1.2.840.113549.1.9.3", "contentType"),
            Map.entry("1.2.840.113549.1.9.4", "signingTime"),
            Map.entry("1.2.840.113549.1.9.5", "counterSignature"),
            Map.entry("1.2.840.113549.1.9.6", "challengePassword"),
            Map.entry("1.2.840.113549.1.9.7", "unstructuredAddress"),
            Map.entry("1.2.840.113549.1.9.8", "extensionRequest"),
            Map.entry("1.2.840.113549.1.9.9", "smimeCapabilities"),
            // ── EC / Curves (1.2.840.10045.x, 1.3.132.0.x) ──────────
            Map.entry("1.2.840.10045.2.1", "ecPublicKey"),
            Map.entry("1.2.840.10045.3.1.7", "secp256r1"),
            Map.entry("1.3.132.0.6", "secp256k1"),
            Map.entry("1.3.132.0.34", "secp384r1"),
            Map.entry("1.3.132.0.35", "secp521r1"),
            // ── X.509 Extensions (2.5.29.x) ──────────────────────────
            Map.entry("2.5.29.14", "subjectKeyIdentifier"),
            Map.entry("2.5.29.15", "keyUsage"),
            Map.entry("2.5.29.17", "subjectAltName"),
            Map.entry("2.5.29.19", "basicConstraints"),
            Map.entry("2.5.29.31", "cRLDistributionPoints"),
            Map.entry("2.5.29.32", "certificatePolicies"),
            Map.entry("2.5.29.35", "authorityKeyIdentifier"),
            Map.entry("2.5.29.37", "extKeyUsage"),
            // ── Internet / PKIX (1.3.6.1.5.5.7.x) ───────────────────
            Map.entry("1.3.6.1.5.5.7.1.1", "authorityInfoAccess"),
            Map.entry("1.3.6.1.5.5.7.3.1", "serverAuth"),
            Map.entry("1.3.6.1.5.5.7.3.2", "clientAuth"),
            Map.entry("1.3.6.1.5.5.7.3.3", "codeSigning"),
            Map.entry("1.3.6.1.5.5.7.48.1", "ocsp"),
            Map.entry("1.3.6.1.5.5.7.48.2", "caIssuers"),
            // ── DSA (1.2.840.10040.4.x) ─────────────────────────────
            Map.entry("1.2.840.10040.4.1", "dsaWithSHA1"),
            Map.entry("1.2.840.10040.4.3.2", "dsaWithSha256"),
            // ── SHA / Hash OIDs ─────────────────────────────────────
            Map.entry("2.16.840.1.101.3.4.2.1", "sha256"),
            Map.entry("2.16.840.1.101.3.4.2.2", "sha384"),
            Map.entry("2.16.840.1.101.3.4.2.3", "sha512"),
            Map.entry("2.16.840.1.101.3.4.2.4", "sha224"),
            Map.entry("1.3.14.3.2.2", "md5"),
            Map.entry("1.3.14.3.2.13", "ripemd160"),
            // ── BSI Algorithmen (0.4.0.127.7.x) ─────────────────────
            Map.entry("0.4.0.127.7.3", "bsiESHA256withECDSA"),
            Map.entry("0.4.0.127.7.4", "bsiESHA384withECDSA"),
            Map.entry("0.4.0.127.7.5", "bsiESHA512withECDSA"),
            Map.entry("0.4.0.127.7.21", "bsiRSA-SHA1"),
            Map.entry("0.4.0.127.7.22", "bsiRSA-SHA224")
    );

    // ─── TagInfo-Referenz ──────────────────────────────────────────────
    // Verwendet den gemeinsamen Record aus TagInfo.java

    // ─── Öffentliche API ───────────────────────────────────────────────

    /**
     * Dekodiert BER/DER-Binärdaten und erzeugt einen ASN1Node-Baum.
     *
     * @param bytes die binären ASN.1-Daten (BER/DER)
     * @return der Wurzelknoten des ASN.1-Baums
     * @throws ASN1ParseException wenn die Daten ungültig sind
     */
    public static ASN1Node decode(byte[] bytes) throws BERDecodeException {
        if (bytes == null || bytes.length == 0) {
            throw new BERDecodeException("Binäre ASN.1-Daten sind leer.", 0);
        }

        Decoder decoder = new Decoder(bytes);
        ASN1Node root = decoder.readTlv();
        if (decoder.pos < bytes.length) {
            throw new BERDecodeException("Unerwartete Daten nach dem Root-Element.", decoder.pos, bytes.length - decoder.pos);
        }



        return root;
    }

    // ─── Decoder-Kontext ──────────────────────────────────────────────

    /**
     * Hält den aktuellen Leseposition und das Byte-Array.
     */
    private static class Decoder {
        byte[] data;
        int pos = 0;

        Decoder(byte[] data) {
            this.data = data;
        }

        /**
         * Liest ein TLV-Element und erzeugt einen ASN1Node mit absolutem Offset.
         *
         * @param absOffset Absolute Start-Position im Originaldaten-Stream.
         */
        private ASN1Node readTlvWithAbsoluteOffset(int absOffset) throws BERDecodeException {
            return readTlvWithOffset(absOffset);
        }

        /**
         * Liest ein TLV-Element und erzeugt einen ASN1Node.
         *
         * <p>Speichert die Byte-Position (offset, length) für die Hex-Editor-Markierung.
         */
        private ASN1Node readTlv() throws BERDecodeException {
            return readTlvWithOffset(-1);
        }

        /**
         * Liest ein TLV-Element und erzeugt einen ASN1Node.
         *
         * @param absOffset Absoluter Offset im Originaldaten-Stream (-1 = relativer Offset).
         */
        private ASN1Node readTlvWithOffset(int absOffset) throws BERDecodeException {
            if (pos >= data.length) {
                throw new BERDecodeException("Unerwartetes Ende der Daten beim Lesen eines Tags.", pos, 0);
            }

            // Absoluten Offset verwenden, sonst relativen (pos)
            int absOffsetStart = absOffset >= 0 ? absOffset : pos;

            int startPos = pos;
            int tagByte = data[pos++] & 0xFF;
            TagInfo tagInfo = readTag(tagByte);

            int length = readLength();
            // Header-Größe: Bytes verbraucht seit dem ersten Byte dieses TLV
            int headerBytes = pos - startPos;

            int valueLength;

            // Absolute Position des ersten Wert-Bytes im Originaldatenstrom berechnen
            // = absOffsetStart + Header-Größe relativ zum Originaldatenstrom
            // Aber headerBytes ist relativ zum aktuellen data, nicht zum Original!
            // Korrektur: wenn data != Originaldatenstrom, ist headerBytes trotzdem korrekt,
            // weil readLength() die gleiche Anzahl von Bytes aus data liest.
            int valueAbsOffset = absOffset >= 0 ? absOffsetStart + headerBytes
                    : pos;  // relativer Offset für Top-Level


            if (length == INDEFINITE_LENGTH) {
                if (!tagInfo.constructed()) {
                    throw new BERDecodeException("Indefinite Länge ist nur für konstruktive BER-Typen erlaubt.", pos, 0);
                }
                List<ASN1Node> children = readChildrenUntilEoc(valueAbsOffset, valueAbsOffset - absOffsetStart);
                valueLength = pos - startPos;
                return ASN1Node.internal(tagInfo.getDisplayName(), children, absOffsetStart, valueLength);
            }

            byte[] value = readBytes(length);
            valueLength = length;

            return buildNode(tagInfo, value, absOffsetStart, headerBytes + valueLength, valueAbsOffset);
        }

        /**
         * Liest n Bytes ab der aktuellen Position.
         */
        byte[] readBytes(int length) throws BERDecodeException {
            if (length < 0) {
                throw new BERDecodeException("Negative Länge: " + length + ".", pos, 0);
            }
            if (pos + length > data.length) {
                throw new BERDecodeException(
                        "Unerwartetes Ende der Daten beim Lesen von " + length + " Bytes.", pos, length);
            }
            byte[] result = new byte[length];
            System.arraycopy(data, pos, result, 0, length);
            pos += length;
            return result;
        }

        /**
         * Decodiert verschachtelte TLV-Elemente.
         *
         * @param outerValue    Die Wert-Bytes des übergeordneten TLV.
         * @param valueOffset   Absolute Position des ersten Bytes des Wertes im Originalstrom.
         */
        List<ASN1Node> readChildren(byte[] outerValue, int valueOffset) throws BERDecodeException {
            List<ASN1Node> children = new ArrayList<>();

            // Temporären Kontext für die verschachtelten Daten erstellen
            int savedPos = pos;
            byte[] savedData = data;

            data = outerValue;
            pos = 0;

            try {
                while (pos < outerValue.length) {
                    int childAbsOffset = valueOffset + pos;
                    ASN1Node child = readTlvWithAbsoluteOffset(childAbsOffset);
                    children.add(child);
                }
            } finally {
                // Original-Kontext wiederherstellen
                pos = savedPos;
                data = savedData;
            }

            return children;
        }

        /**
         * Decodiert BER-Elemente mit indefiniter Länge bis zum End-of-Contents-Marker (00 00).
         *
         * @param valueAbsOffset Absolute Start-Position des Value-Bytes im Originalstrom.
         */
        List<ASN1Node> readChildrenUntilEoc(int valueAbsOffset, int headerOffset) throws BERDecodeException {
            List<ASN1Node> children = new ArrayList<>();

            while (true) {
                if (pos + 1 >= data.length) {
                    throw new BERDecodeException("End-of-Contents-Marker für indefinite Länge fehlt.", pos, 2);
                }
                if ((data[pos] & 0xFF) == 0x00 && (data[pos + 1] & 0xFF) == 0x00) {
                    pos += 2;
                    return children;
                }
                // pos ist die absolute Position im Originaldatenstrom, da readChildrenUntilEoc
                // denselben data-Array verwendet und nicht wie readChildren den Kontext tauscht.
                children.add(readTlvWithAbsoluteOffset(pos));
            }
        }

        /**
         * Baut einen ASN1Node aus Tag und Value.
         *
         * @param tagInfo       Tag-Informationen
         * @param value         Der Wert als Byte-Array
         * @param offset        Start-Byte im Binärstrom
         * @param length        Byte-Länge (Tag + Length + Value)
         * @param valueAbsOffset Absolute Start-Position des Value-Bytes im Originalstrom
         */
        private ASN1Node buildNode(TagInfo tagInfo, byte[] value, int offset, int length, int valueAbsOffset) throws BERDecodeException {

            String name = tagInfo.getDisplayName();

            // Konstruktive Typen → rekursiv decodieren
            if (tagInfo.constructed()) {
                List<ASN1Node> children = readChildren(value, valueAbsOffset);
                return ASN1Node.internal(name, children, offset, length);
            }

            // Primitiver Typ → Leaf-Knoten
            String hexValue = bytesToHex(value);

            if (tagInfo.name().equals("INTEGER")) {
                int intValue = valueToInt(value);
                return ASN1Node.internal(name, List.of(
                        ASN1Node.leaf("kind", tagInfo.name()),
                        ASN1Node.leaf("hex", hexValue),
                        ASN1Node.leaf("value", String.valueOf(intValue))
                ), offset, length);
            }

            if (tagInfo.name().equals("BOOLEAN")) {
                boolean boolValue = (value.length > 0 && value[0] != 0);
                return ASN1Node.internal(name, List.of(
                        ASN1Node.leaf("kind", tagInfo.name()),
                        ASN1Node.leaf("hex", hexValue),
                        ASN1Node.leaf("value", String.valueOf(boolValue))
                ), offset, length);
            }

            if (tagInfo.name().equals("NULL")) {
                return ASN1Node.internal(name, List.of(
                        ASN1Node.leaf("kind", tagInfo.name()),
                        ASN1Node.leaf("value", "null")
                ), offset, length);
            }

            if (tagInfo.name().equals("OBJECT IDENTIFIER")) {
                String oidValue = decodeOid(value);
                String displayValue = formatOidValue(oidValue);
                return ASN1Node.internal(name, List.of(
                        ASN1Node.leaf("kind", tagInfo.name()),
                        ASN1Node.leaf("hex", hexValue),
                        ASN1Node.leaf("value", displayValue),
                        ASN1Node.leaf("oid", oidValue)
                ), offset, length);
            }

            if (tagInfo.name().equals("BIT STRING")) {
                if (value.length > 0) {
                    int unusedBits = value[0];
                    StringBuilder hexNoPadding = new StringBuilder();
                    for (int i = 1; i < value.length; i++) {
                        hexNoPadding.append(String.format("%02X", value[i] & 0xFF));
                    }
                    return ASN1Node.internal(name, List.of(
                            ASN1Node.leaf("kind", tagInfo.name()),
                            ASN1Node.leaf("hex", hexValue),
                            ASN1Node.leaf("value", hexNoPadding.toString()),
                            ASN1Node.leaf("unusedBits", String.valueOf(unusedBits))
                    ), offset, length);
                }
                return ASN1Node.internal(name, List.of(
                        ASN1Node.leaf("kind", tagInfo.name()),
                        ASN1Node.leaf("hex", hexValue)
                ), offset, length);
            }

            if (tagInfo.name().equals("OCTET STRING")) {
                String text = tryAsText(value);
                return ASN1Node.internal(name, List.of(
                        ASN1Node.leaf("kind", tagInfo.name()),
                        ASN1Node.leaf("hex", hexValue),
                        ASN1Node.leaf("value", text)
                ), offset, length);
            }

            if (isStringType(tagInfo.name())) {
                String text = tryAsText(value);
                return ASN1Node.internal(name, List.of(
                        ASN1Node.leaf("kind", tagInfo.name()),
                        ASN1Node.leaf("hex", hexValue),
                        ASN1Node.leaf("value", text)
                ), offset, length);
            }

            // Generic primitive
            return ASN1Node.internal(name, List.of(
                    ASN1Node.leaf("kind", tagInfo.name()),
                    ASN1Node.leaf("hex", hexValue)
            ), offset, length);
        }

        // ─── Tag-Lesen ─────────────────────────────────────────────────

        /**
         * Liest ein Tag und gibt TagInfo zurück.
         */
        private TagInfo readTag(int tagByte) {
            int tagClass = (tagByte >> 6) & 0x03;
            boolean constructed = (tagByte & 0x20) != 0;
            int tagNumber = tagByte & 0x1F;

            if (tagClass == 0 && tagNumber == 0x1F) {
                // Long form tag — wird später gelesen
                return new TagInfo(tagClass, 0x1F, constructed, "LONG_TAG");
            }

            String name = ASN1BerDecoder.universalTagName(tagClass, tagNumber);
            // Für nicht-UNIVERSAL-Tags: P-Flag prüfen
            boolean isConstructed = constructed;
            if (tagClass == 0 && (name.equals("SEQUENCE") || name.equals("SET"))) {
                isConstructed = true; // SEQUENCE und SET sind immer konstruktiv
            }

            return new TagInfo(tagClass, tagNumber, isConstructed, name);
        }

        // ─── Längen-Lesen ──────────────────────────────────────────────

        /**
         * Liest eine BER/DER-Länge.
         *
         * <p>Kurze Form: 1 Byte, Wert = Länge (0–127)
         * <p>Lange Form: 1. Byte = 0x80+n, n=Anzahl der folgenden Bytes, dann n Bytes = Länge
         */
        private int readLength() throws BERDecodeException {
            if (pos >= data.length) {
                throw new BERDecodeException("Unerwartetes Ende beim Lesen einer Länge.", pos, 0);
            }

            int firstByte = data[pos++] & 0xFF;
            int result;

            if ((firstByte & 0x80) == 0) {
                // Kurze Form
                result = firstByte;
            } else {
                // Lange Form: 0x80 | n, n = Anzahl der folgenden Bytes
                int numLengthBytes = firstByte & 0x7F;
                if (numLengthBytes == 0) {
                    result = INDEFINITE_LENGTH;
                } else {
                    int length = 0;
                    for (int i = 0; i < numLengthBytes; i++) {
                        if (pos >= data.length) {
                            throw new BERDecodeException("Unerwartetes Ende bei langer Länge.", pos, 0);
                        }
                        length = (length << 8) | (data[pos++] & 0xFF);
                    }
                    result = length;
                }
            }

            return result;
        }
    }

    // ─── Hilfsmethoden (statisch) ──────────────────────────────────────

    /**
     * Gibt den ASN.1-Typnamen basierend auf Tag-Class und Tag-Number zurück.
     */
    static String universalTagName(int tagClass, int tagNumber) {
        if (tagClass == 1) {
            return "APPLICATION[" + tagNumber + "]";
        }
        if (tagClass == 2) {
            return "CONTEXT[" + tagNumber + "]";
        }
        if (tagClass == 3) {
            return "PRIVATE[" + tagNumber + "]";
        }

        return switch (tagNumber) {
            case TAG_BOOLEAN -> "BOOLEAN";
            case TAG_INTEGER -> "INTEGER";
            case TAG_BIT_STRING -> "BIT STRING";
            case TAG_OCTET_STRING -> "OCTET STRING";
            case TAG_NULL -> "NULL";
            case TAG_OID -> "OBJECT IDENTIFIER";
            case TAG_UTF8STRING -> "UTF8String";
            case TAG_NUMERICSTRING -> "NumericString";
            case TAG_PRINTABLESTRING -> "PrintableString";
            case TAG_T61STRING -> "T61String";
            case TAG_IA5STRING -> "IA5String";
            case TAG_UTCTIME -> "UTCTime";
            case TAG_GENERALIZEDTIME -> "GeneralizedTime";
            case TAG_BMPSTRING -> "BMPString";
            case TAG_SEQUENCE -> "SEQUENCE";
            case TAG_SET -> "SET";
            default -> "UNKNOWN(" + tagNumber + ")";
        };
    }

    /**
     * Konvertiert ein Byte-Array zu einem int (Zweierkomplement, big-endian).
     * Verwendet BigInteger für korrekte Behandlung großer Werte.
     */
    private static int valueToInt(byte[] value) {
        if (value.length == 0) {
            return 0;
        }
        // BigInteger behandelt Zweierkomplement korrekt
        return new java.math.BigInteger(value).intValue();
    }

    /**
     * Dekodiert einen OID (Object Identifier).
     */
    private static String decodeOid(byte[] value) {
        if (value.length < 2) {
            return bytesToHex(value);
        }

        StringBuilder sb = new StringBuilder();
        int first = value[0] & 0xFF;
        sb.append(first / 40).append('.').append(first % 40);

        // X.690 base-128 encoding
        int component = 0;
        for (int i = 1; i < value.length; i++) {
            int b = value[i] & 0xFF;
            component = (component << 7) | (b & 0x7F);
            if ((b & 0x80) == 0) {
                sb.append('.').append(component);
                component = 0;
            }
        }

        return sb.toString();
    }

    /**
     * Formatiert bekannte OIDs mit sprechendem Namen.
     * Bekannte OIDs: oid + " (name)" — z.B. "2.5.4.10 (organizationName (X.520 DN component))"
     * Unbekannte OIDs: nur oid-String
     */
    private static String formatOidValue(String oid) {
        String name = RFC5280_OID_NAMES.get(oid);
        if (name == null) {
            return oid;
        }
        return oid + " (" + name + ")";
    }

    /**
     * Versucht, Bytes als Text darzustellen.
     */
    private static String tryAsText(byte[] value) {
        try {
            String s = new String(value, StandardCharsets.UTF_8);
            // Nur zurückgeben, wenn es sichtbare druckbare Zeichen enthält
            boolean hasPrintable = false;
            for (byte b : value) {
                if (b >= 0x20 && b <= 0x7E) {
                    hasPrintable = true;
                    break;
                }
            }
            return hasPrintable ? s : bytesToHex(value);
        } catch (Exception e) {
            return bytesToHex(value);
        }
    }

    /**
     * Prüft, ob ein Typ-Name ein String-Typ ist.
     */
    private static boolean isStringType(String name) {
        return name.equals("UTF8String") ||
               name.equals("NumericString") ||
               name.equals("PrintableString") ||
               name.equals("T61String") ||
               name.equals("IA5String") ||
               name.equals("UTCTime") ||
               name.equals("GeneralizedTime") ||
               name.equals("BMPString");
    }

    /**
     * Konvertiert Byte-Array zu Hex-String.
     */
    private static String bytesToHex(byte[] bytes) {
        return com.asn1editor.service.HexUtils.toCompactHex(bytes);
    }
}
