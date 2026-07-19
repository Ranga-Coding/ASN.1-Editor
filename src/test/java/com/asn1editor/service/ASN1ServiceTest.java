package com.asn1editor.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für {@link com.asn1editor.service.ASN1Service},
 * insbesondere Base64- und PEM-Erkennung/Dekodierung sowie Binär-Unterstützung.
 */
class ASN1ServiceTest {

    private final ASN1Service service = new ASN1Service();

    // ─── detectFormat ────────────────────────────────────────────────

    @Test
    void detectFormat_plainText_asn1_text() {
        assertEquals(ASN1Service.FileFormat.ASN1_TEXT, service.detectFormat("MyValue ::= SEQUENCE { id INTEGER }"));
    }

    @Test
    void detectFormat_pem_base64() {
        String pem = "-----BEGIN CERTIFICATE-----\nMIIDXTCCAkWgAwIBAgIJAKL0\n-----END CERTIFICATE-----";
        assertEquals(ASN1Service.FileFormat.BASE64, service.detectFormat(pem));
    }

    @Test
    void detectFormat_pem_with_any_header() {
        String pem = "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhk\n-----END PUBLIC KEY-----";
        assertEquals(ASN1Service.FileFormat.BASE64, service.detectFormat(pem));
    }

    @Test
    void detectFormat_raw_base64() {
        String base64 = "U2FtcGxlIFZhbHVlIDo9IFNFUVVFTkNFIGU=";
        assertEquals(ASN1Service.FileFormat.BASE64, service.detectFormat(base64));
    }

    @Test
    void detectFormat_binary_with_null() {
        byte[] bytes = new byte[] { 'T', 'e', 's', 't', 0, 0, (byte) 0x80, (byte) 0xFF };
        String binary = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertEquals(ASN1Service.FileFormat.BINARY, service.detectFormat(binary));
    }

    @Test
    void detectFormat_binary_with_high_byte() {
        byte[] bytes = new byte[] { 0x30, (byte) 0x80, 0x06, 0x09 }; // BER-TAG
        String binary = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertEquals(ASN1Service.FileFormat.BINARY, service.detectFormat(binary));
    }

    @Test
    void detectFormat_empty_returns_text() {
        assertEquals(ASN1Service.FileFormat.ASN1_TEXT, service.detectFormat(""));
        assertEquals(ASN1Service.FileFormat.ASN1_TEXT, service.detectFormat(null));
    }

    // ─── decodeBase64IfNeeded ────────────────────────────────────────

    @Test
    void decodeBase64IfNeeded_pem_decoded_text() {
        // PEM-formatierter ASN.1-Text-Content
        String original = "LesTool ::= SEQUENCE { id INTEGER }";
        String base64 = java.util.Base64.getEncoder().encodeToString(original.getBytes());
        String pem = "-----BEGIN CERTIFICATE-----\n" + base64 + "\n-----END CERTIFICATE-----";
        String result = service.decodeBase64IfNeeded(pem);
        assertEquals(original, result);
    }

    @Test
    void decodeBase64IfNeeded_pem_multiline_decoded() {
        // PEM mit mehreren Zeilen (typisch bei Zertifikaten)
        String pem = "-----BEGIN CERTIFICATE-----\n"
                + "MIIBkTCB+wIJALRiMLAh080LMA0GCSqGSIb3DQEBBQUAMBkxFzAVBgNVBAMTDkxv\n"
                + "Z2luZyBDZXJ0aWZpY2F0ZTAeFw0xNTExMTkwOTQ5MTlaFw0yNTExMTcwOTQ5MTla\n"
                + "MBkxFzAVBgNVBAMTDkxvZ2luZyBDZXJ0aWZpY2F0ZTBcMA0GCSqGSIb3DQEBAQUA\n"
                + "A0sAMEgCQQC6n0N+XQhN\n-----END CERTIFICATE-----";
        String result = service.decodeBase64IfNeeded(pem);
        // Decodierte Daten sind Binär (BER) → Hex-String
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(isHexString(result), "Decodierte Binärdaten sollten als Hex zurückgegeben werden");
    }

    @Test
    void decodeBase64IfNeeded_pem_only_begin_no_end() {
        // Kein PEM: nur BEGIN, kein END → nicht decodiert
        String incomplete = "-----BEGIN CERTIFICATE-----\nSGVsbG8=";
        String result = service.decodeBase64IfNeeded(incomplete);
        assertEquals(incomplete, result);
    }

    @Test
    void decodeBase64IfNeeded_plain_text_unverändert() {
        String input = "MyValue ::= SEQUENCE { name INTEGER }";
        assertEquals(input, service.decodeBase64IfNeeded(input));
    }

    @Test
    void decodeBase64IfNeeded_plain_text_with_curly_braces_unverändert() {
        // Enthält { } : aber auch :: = → ASN1_TEXT → unverändert
        String input = "MyValue ::= { field \"test\" }";
        assertEquals(input, service.decodeBase64IfNeeded(input));
    }

    @Test
    void decodeBase64IfNeeded_raw_base64_decoded_text() {
        String base64 = "U2FtcGxlIFZhbHVl"; // "Sample Value"
        String result = service.decodeBase64IfNeeded(base64);
        assertEquals("Sample Value", result);
    }

    @Test
    void decodeBase64IfNeeded_raw_base64_with_newlines() {
        String original = "Test ::= SEQUENCE { id INTEGER }";
        String base64 = java.util.Base64.getEncoder().encodeToString(original.getBytes());
        String withNewlines = base64.substring(0, 30) + "\n" + base64.substring(30);
        String result = service.decodeBase64IfNeeded(withNewlines);
        assertEquals(original, result);
    }

    @Test
    void decodeBase64IfNeeded_raw_base64_binary_to_hex() {
        // Base64, die zu Binärdaten decodiert → Hex-String
        byte[] binaryData = new byte[] { 0x30, (byte) 0x80, 0x06, 0x09, 0x04, 0x00, (byte) 0x7F, 0x00 };
        String base64 = java.util.Base64.getEncoder().encodeToString(binaryData);
        String result = service.decodeBase64IfNeeded(base64);
        String expectedHex = ASN1Service.toHexString(binaryData);
        assertEquals(expectedHex, result);
    }

    @Test
    void decodeBase64IfNeeded_invalid_base64_padding_original() {
        // '=' am Anfang ist ungültig → Decoder wirft Exception → Original zurückgeben
        String badPadding = "=SGVsbG8=";
        String result = service.decodeBase64IfNeeded(badPadding);
        assertEquals(badPadding, result);
    }

    @Test
    void decodeBase64IfNeeded_empty_returns_same() {
        assertNull(service.decodeBase64IfNeeded(null));
        assertEquals("", service.decodeBase64IfNeeded(""));
    }

    // ─── toHexString / fromHexString ─────────────────────────────────

    @Test
    void toHexString_simple() {
        assertEquals("48 65 6C 6C 6F", ASN1Service.toHexString("Hello".getBytes()));
    }

    @Test
    void toHexString_empty() {
        assertEquals("", ASN1Service.toHexString(new byte[0]));
    }

    @Test
    void toHexString_binary_data() {
        byte[] data = new byte[]{0x00, (byte) 0xFF, (byte) 0x80};
        assertEquals("00 FF 80", ASN1Service.toHexString(data));
    }

    @Test
    void toHexString_ber_tag() {
        byte[] ber = new byte[]{0x30, (byte) 0x80, 0x06, 0x09};
        assertEquals("30 80 06 09", ASN1Service.toHexString(ber));
    }

    @Test
    void fromHexString_roundtrip() {
        byte[] original = new byte[]{0x30, (byte) 0x80, 0x06, 0x09, 0x04};
        String hex = ASN1Service.toHexString(original);
        byte[] decoded = ASN1Service.fromHexString(hex);
        assertArrayEquals(original, decoded);
    }

    @Test
    void fromHexString_with_spaces() {
        byte[] original = new byte[]{0x00, (byte) 0xFF};
        String hex = "00 FF";
        assertArrayEquals(original, ASN1Service.fromHexString(hex));
    }

    @Test
    void fromHexString_lowercase() {
        byte[] original = new byte[]{0x0A, (byte) 0xBF};
        assertArrayEquals(original, ASN1Service.fromHexString("0a bf"));
    }

    @Test
    void fromHexString_no_spaces() {
        byte[] original = new byte[]{0x0A, (byte) 0xBF};
        assertArrayEquals(original, ASN1Service.fromHexString("0ABF"));
    }

    // ─── isHexString (privat, über decodeBase64IfNeeded geprüft) ──────

    @Test
    void decodeBase64IfNeeded_binary_content_returns_hex() {
        // Test: Base64 decodiert zu Binär (BER) → Hex-String, kein UTF-8
        byte[] berData = new byte[] { 0x30, (byte) 0x82, 0x00, 0x00 }; // BER SEQUENCE mit langer Länge
        String base64 = java.util.Base64.getEncoder().encodeToString(berData);
        String result = service.decodeBase64IfNeeded(base64);
        assertTrue(isHexString(result));
    }

    // ─── parse ───────────────────────────────────────────────────────

    @Test
    void parse_plain_text_sequence() throws Exception {
        String source = "MySequence ::= SEQUENCE { id INTEGER, name STRING }";
        var doc = service.parse(source);
        assertNotNull(doc);
        assertFalse(doc.root().children().isEmpty());
    }

    @Test
    void parse_base64_encoded_sequence() throws Exception {
        String original = "MySequence ::= SEQUENCE { id INTEGER, name STRING }";
        String base64 = java.util.Base64.getEncoder().encodeToString(original.getBytes());
        var doc = service.parse(base64);
        assertNotNull(doc);
        assertFalse(doc.root().children().isEmpty());
    }

    @Test
    void parse_pem_encoded_sequence() throws Exception {
        String original = "MySequence ::= SET { id INTEGER }";
        String base64 = java.util.Base64.getEncoder().encodeToString(original.getBytes());
        String pem = "-----BEGIN CERTIFICATE-----\n" + base64 + "\n-----END CERTIFICATE-----";
        var doc = service.parse(pem);
        assertNotNull(doc);
        assertFalse(doc.root().children().isEmpty());
    }

    @Test
    void parse_pem_with_multiline_base64() throws Exception {
        String original = "MySequence ::= SET { id INTEGER }";
        String base64 = java.util.Base64.getEncoder().encodeToString(original.getBytes());
        String multilineBase64 = base64.substring(0, 32) + "\n" + base64.substring(32);
        String pem = "-----BEGIN CERTIFICATE-----\n" + multilineBase64 + "\n-----END CERTIFICATE-----";
        var doc = service.parse(pem);
        assertNotNull(doc);
        assertFalse(doc.root().children().isEmpty());
    }

    @Test
    void parse_binary_throws_exception() throws Exception {
        // Binärdatei sollte eine Exception werfen mit Hinweis auf Hex-Darstellung
        byte[] bytes = new byte[] { 'T', 'e', 's', 't', 0, 0, (byte) 0x80, (byte) 0xFF };
        String binary = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        var ex = assertThrows(Exception.class, () -> service.parse(binary));
        assertTrue(ex.getMessage().contains("Binäre ASN.1-Datei"));
    }

    @Test
    void parse_base64_binary_throws_exception_with_hex() throws Exception {
        // Base64, das zu Binärdaten decodiert → Exception mit Hex-Inhalt
        byte[] berData = new byte[] { 0x30, (byte) 0x82, 0x00, 0x00, 0x01 };
        String base64 = java.util.Base64.getEncoder().encodeToString(berData);
        ASN1IOException ex = assertThrows(ASN1IOException.class, () -> service.parse(base64));
        assertTrue(ex.getMessage().contains("Binäre ASN.1-Datei"));

        // Prüfen: Exception enthält Hex-Inhalt
        assertTrue(ex.hasHexContent(), "ASN1IOException sollte Hex-Inhalt für binäre Datei enthalten");
        String hex = ex.getHexContent();
        assertEquals(ASN1Service.toHexString(berData), hex);
    }

    @Test
    void parse_binary_throws_exception_with_hex() throws Exception {
        // Roh-Binärdatei → Exception sollte Hex-Inhalt enthalten
        byte[] berData = new byte[] { 0x30, (byte) 0x80, (byte) 0xA1, 0x02 };
        String binary = new String(berData, java.nio.charset.StandardCharsets.ISO_8859_1);
        ASN1IOException ex = assertThrows(ASN1IOException.class, () -> service.parse(binary));
        assertTrue(ex.hasHexContent(), "Binär-Exception sollte Hex-Inhalt enthalten");
        assertEquals(ASN1Service.toHexString(berData), ex.getHexContent());
    }

    @Test
    void parse_invalid_text_throws_exception() {
        assertThrows(Exception.class, () -> service.parse("This is not ASN.1 at all @#$%"));
    }

    // ─── detectFormat edge cases ─────────────────────────────────────

    @Test
    void detectFormat_pem_only_begin_no_end() {
        String incomplete = "-----BEGIN CERTIFICATE-----\nSGVsbG8=";
        assertEquals(ASN1Service.FileFormat.ASN1_TEXT, service.detectFormat(incomplete));
    }

    @Test
    void detectFormat_text_with_parentheses() {
        String text = "MyValue ::= CHOICE { field (INTEGER, OCTET STRING) }";
        assertEquals(ASN1Service.FileFormat.ASN1_TEXT, service.detectFormat(text));
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    /**
     * Hilfsfunktion: prüft ob ein String ein Hex-String ist.
     * (Die private Methode isHexString() ist nicht direkt testbar,
     *  aber ihr Verhalten wird über decodeBase64IfNeeded indirekt geprüft.)
     */
    private boolean isHexString(String source) {
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
}
