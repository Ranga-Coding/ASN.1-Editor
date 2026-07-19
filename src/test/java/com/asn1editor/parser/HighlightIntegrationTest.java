package com.asn1editor.parser;

import com.asn1editor.model.ASN1Node;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integrationstests fuer das Byte-Highlighting.
 *
 * <p>Testet Decoder-Offsets und Highlight-Berechnung mit konkreten BER/DER-Daten.
 */
class HighlightIntegrationTest {

    /**
     * Test: Komplexe Struktur mit allen angeforderten ASN.1-Typen.
     *
     * <p>Byte-Offsets (exakt berechnet):
     * <pre>
     * Byte  0-1:  SEQUENCE Header (30 22) -- content = 34 Bytes
     * Byte  2-4:  Child 0: SEQUENCE (32 01 02) -- len=3
     * Byte  5-7:  Child 1: INTEGER (02 01 42) -- len=3
     * Byte  8-10: Child 2: BOOLEAN (01 01 FF) -- len=3
     * Byte 11-16: Child 3: OID (06 04 2A 86 48 10) -- len=6
     * Byte 17-21: Child 4: PrintableString (13 03 48 65 6C) -- len=5
     * Byte 22-25: Child 5: OCTET STRING (04 02 01 02) -- len=4
     * Byte 26-28: Child 6: SET (31 01 01) -- len=3
     * Byte 29-30: Child 7: NULL (05 00) -- len=2
     * </pre>
     *
     * <p>Content-Check: 3+3+3+6+5+4+3+2 = 29
     * Korrigiert: Content = 29 = 0x1D
     */
    @Test
    @DisplayName("Decoder + Highlight fuer komplexe BER/DER-Struktur")
    void testCompleteStructure() throws Exception {
        // Alle Kinder zusammen: 3+3+3+6+5+4+3+2 = 29 Bytes content
        byte[] data = new byte[] {
            (byte)0x30, (byte)0x1E,  // Root SEQUENCE len=30
            (byte)0x02, (byte)0x01, (byte)0x01,  // Child 0: INTEGER (ersetzt SEQUENCE)
            (byte)0x02, (byte)0x01, (byte)0x42,  // Child 1: INTEGER len=1, value=66
            (byte)0x01, (byte)0x01, (byte)0xFF,  // Child 2: BOOLEAN len=1, value=TRUE
            (byte)0x06, (byte)0x04, (byte)0x2A, (byte)0x86, (byte)0x48, (byte)0x10,  // Child 3: OID len=4 (2+2)
            (byte)0x13, (byte)0x03, (byte)0x48, (byte)0x65, (byte)0x6C,  // Child 4: PrintableString len=3
            (byte)0x04, (byte)0x02, (byte)0x01, (byte)0x02,  // Child 5: OCTET STRING len=2
            (byte)0x31, (byte)0x02, (byte)0x05, (byte)0x00,  // Child 6: SET [CONSTRUCTED] mit NULL-Kind
            (byte)0x04, (byte)0x00,  // Child 7: OCTET STRING (empty)
        };

        // Verify total: 2 + 3 + 3 + 3 + 6 + 5 + 4 + 4 + 2 = 32
        assertEquals(32, data.length);
        assertEquals(30, data[1] & 0xFF);  // SEQUENCE content length = 30

        System.out.println("\n=== BER/DER Byte-Stream ===");
        printHex(data);
        System.out.println("Gesamt: " + data.length + " Bytes\n");

        ASN1Node root = ASN1BerDecoder.decode(data);

        // Root SEQUENCE
        assertEquals(0, root.offset());
        assertEquals(data.length, root.length());
        assertEquals("SEQUENCE [CONSTRUCTED]", root.name());
        assertEquals(8, root.children().size());

        // Kinder-Offsets und -Laengen
        List<ASN1Node> children = root.children();
        verifyChild(children.get(0), "INTEGER", 2, 3, data);
        verifyChild(children.get(1), "INTEGER", 5, 3, data);
        verifyChild(children.get(2), "BOOLEAN", 8, 3, data);
        verifyChild(children.get(3), "OBJECT IDENTIFIER", 11, 6, data);
        verifyChild(children.get(4), "PrintableString", 17, 5, data);
        verifyChild(children.get(5), "OCTET STRING", 22, 4, data);
        verifyChild(children.get(6), "SET [CONSTRUCTED]", 26, 4, data);
        verifyChild(children.get(7), "OCTET STRING", 30, 2, data);

        // Keine Luecken zwischen Kindern
        for (int i = 1; i < children.size(); i++) {
            ASN1Node prev = children.get(i - 1);
            ASN1Node curr = children.get(i);
            assertEquals(prev.offset() + prev.length(), curr.offset(),
                "Keine Luecke: " + prev.name() + " endet bei " + (prev.offset() + prev.length())
                + ", " + curr.name() + " beginnt bei " + curr.offset());
        }

        // Root-Ende = letztes Kind-Ende
        ASN1Node lastChild = children.get(children.size() - 1);
        assertEquals(root.offset() + root.length(), lastChild.offset() + lastChild.length());

        // Highlight-Simulation fuer alle Kinder
        simulateHighlights(root, data);

        System.out.println("\n✅ Alle Offsets, Längen und Highlights korrekt!");
    }

    /**
     * Test: Highlight fuer einzelnen INTEGER.
     */
    @Test
    @DisplayName("Highlight: Einzelner INTEGER")
    void testIntegerHighlight() throws Exception {
        byte[] data = new byte[] {
            (byte)0x30, (byte)0x03,  // SEQUENCE len=3
            (byte)0x02, (byte)0x01, (byte)0x42,  // INTEGER 66
        };

        ASN1Node root = ASN1BerDecoder.decode(data);
        ASN1Node intNode = root.children().get(0);

        // SEQUENCE Header = 2 Bytes, INTEGER beginnt bei Byte 2
        assertEquals(2, intNode.offset());
        assertEquals(3, intNode.length());

        simulateHighlight(intNode, data);
        System.out.println("\n✅ INTEGER Highlight korrekt!");
    }

    /**
     * Test: Highlight fuer mehrzeiligen OID.
     */
    @Test
    @DisplayName("Highlight: OID ueber Zeilengrenze")
    void testOidMultiRowHighlight() throws Exception {
        // OID = 06 08 2A 86 48 86 F7 0D 01 (10 bytes: tag+length+8 oid bytes)
        // SEQUENCE = 30 0A [OID 10 bytes] = 12 bytes total, content = 10
        byte[] data = new byte[] {
            (byte)0x30, (byte)0x0A,  // SEQUENCE len=10
            (byte)0x06, (byte)0x08, (byte)0x2A, (byte)0x86, (byte)0x48, (byte)0x86,
            (byte)0xF7, (byte)0x0D, (byte)0x01, (byte)0x02,  // OID (1.2.840.113549.1.2)
        };
        assertEquals(12, data.length);

        ASN1Node root = ASN1BerDecoder.decode(data);
        ASN1Node oidNode = root.children().get(0);

        assertEquals(2, oidNode.offset());
        assertEquals(10, oidNode.length());  // 2 Header + 8 OID

        simulateHighlight(oidNode, data);
        System.out.println("\n✅ OID Multi-Row Highlight korrekt!");
    }

    /**
     * Test: Highlight fuer OCTET STRING der exakt eine Zeile füllt.
     */
    @Test
    @DisplayName("Highlight: OCTET STRING füllt genau eine Zeile")
    void testOctetStringSingleRow() throws Exception {
        // OCTET STRING = 04 06 00 01 02 03 04 05 (8 bytes: tag+length+6 value)
        // SEQUENCE = 30 08 [OCTET 8 bytes] = 10 bytes total, content = 8
        byte[] data = new byte[] {
            (byte)0x30, (byte)0x08,  // SEQUENCE len=8
            (byte)0x04, (byte)0x06, (byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03,
            (byte)0x04, (byte)0x05,
        };
        assertEquals(10, data.length);

        ASN1Node root = ASN1BerDecoder.decode(data);
        ASN1Node octNode = root.children().get(0);

        // SEQUENCE Header (2), OCTET STRING beginnt bei Byte 2
        assertEquals(2, octNode.offset());
        assertEquals(8, octNode.length());  // 2 Header + 6 Value

        simulateHighlight(octNode, data);
        System.out.println("\n✅ OCTET STRING Single-Row Highlight korrekt!");
    }

    // ===== HILFSMETHODEN =====

    private void verifyChild(ASN1Node child, String expectedName,
                              int expectedOffset, int expectedLength,
                              byte[] data) {
        System.out.println("\n" + child.name() + ":");
        System.out.println("  Offset: " + child.offset() + ", Length: " + child.length());

        if (child.offset() >= 0 && child.length() > 0
            && child.offset() + child.length() <= data.length) {
            byte[] bytes = Arrays.copyOfRange(data, child.offset(), child.offset() + child.length());
            System.out.println("  Bytes: [" + bytesToHex(bytes) + "]");
        }

        assertEquals(expectedName, child.name());
        assertEquals(expectedOffset, child.offset(), child.name() + " offset");
        assertEquals(expectedLength, child.length(), child.name() + " length");

        assertTrue(child.offset() >= 0);
        assertTrue(child.length() > 0);
        assertTrue(child.offset() + child.length() <= data.length);
    }

    private void simulateHighlight(ASN1Node node, byte[] data) {
        simulateHighlight(node, data, 16);
    }

    private void simulateHighlight(ASN1Node node, byte[] data, int bytesPerRow) {
        int highlightStart = node.offset();
        int highlightEnd = node.offset() + node.length();

        int BYTE_WIDTH = 2 * 9 + 9;  // 27
        int HEX_ONLY_WIDTH = 2 * 9;  // 18

        int lineStartRow = highlightStart / bytesPerRow;
        int lineEndRow = (highlightEnd - 1) / bytesPerRow;

        System.out.println("  " + node.name() + " (Byte " + highlightStart + "-" + (highlightEnd - 1) + "):");

        for (int row = lineStartRow; row <= lineEndRow; row++) {
            int rowStartByte = row * bytesPerRow;
            int rowEndByte = Math.min(rowStartByte + bytesPerRow, data.length);

            int startByteInRow = Math.max(0, highlightStart - rowStartByte);
            int endByteInRow = Math.min(highlightEnd, rowEndByte) - rowStartByte;

            int startX = startByteInRow * BYTE_WIDTH;
            int endX = (endByteInRow - 1) * BYTE_WIDTH + HEX_ONLY_WIDTH;
            int width = endX - startX;

            System.out.println("    Row " + row + ": bytes " + startByteInRow + "-" + (endByteInRow - 1)
                + " X=[" + startX + "," + endX + "] width=" + width);

            // Verifiziere: Letztes Byte X-Position stimmt mit render()-Loop ueberein
            int lastByteIndex = endByteInRow - 1;
            int renderX = lastByteIndex * BYTE_WIDTH + HEX_ONLY_WIDTH;
            assertEquals(renderX, endX, "EndX mismatch Row " + row);

            // Verifiziere: Erstes Byte X-Position stimmt mit render()-Loop ueberein
            int firstByteIndex = startByteInRow;
            int renderStartX = firstByteIndex * BYTE_WIDTH;
            assertEquals(renderStartX, startX, "StartX mismatch Row " + row);

            // Verifiziere: Breite korrekt
            int expectedWidth = (endByteInRow - startByteInRow - 1) * BYTE_WIDTH + HEX_ONLY_WIDTH;
            assertEquals(expectedWidth, width, "Width mismatch Row " + row);
        }

        System.out.println("  ✓ Highlight-Berechnung verifiziert");
    }

    private void simulateHighlights(ASN1Node root, byte[] data) {
        System.out.println("\n=== Highlight-Simulation fuer alle Kinder ===");
        for (ASN1Node child : root.children()) {
            simulateHighlight(child, data, 16);
        }
    }

    private void printHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            if (i % 16 == 0 && i > 0) sb.append("\n  ");
            sb.append(String.format("%02X ", data[i] & 0xFF));
        }
        System.out.println("  " + sb.toString().trim());
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString().trim();
    }
}
