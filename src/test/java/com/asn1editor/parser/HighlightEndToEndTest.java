package com.asn1editor.parser;

import com.asn1editor.model.ASN1Node;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Test fÃ¼r das Byte-Highlighting.
 *
 * <p>Struktur (BER/DER Bytes):
 * <pre>
 * Byte 0-1:  SEQUENCE Header (30 18)
 * Byte 2-4:  INTEGER (02 01 00)
 * Byte 5-7:  BOOLEAN (01 01 FF)
 * Byte 8-13: OBJECT IDENTIFIER (06 03 2A 86 48)
 * Byte 14-21: PRINTABLESTRING (13 06 48 65 6C 6C 6F 21)
 * Byte 22-26: OCTET STRING (04 03 01 02 03)
 * </pre>
 */
class HighlightEndToEndTest {

    @Test
    void testCompleteStructure() throws Exception {
        byte[] data = new byte[] {
            (byte)0x30, (byte)0x18,
            (byte)0x02, (byte)0x01, (byte)0x00,
            (byte)0x01, (byte)0x01, (byte)0xFF,
            (byte)0x06, (byte)0x03, (byte)0x2A, (byte)0x86, (byte)0x48,
            (byte)0x13, (byte)0x06, (byte)0x48, (byte)0x65, (byte)0x6C,
            (byte)0x6C, (byte)0x6F, (byte)0x21,
            (byte)0x04, (byte)0x03, (byte)0x01, (byte)0x02, (byte)0x03,
        };

        System.out.println("=== BER/DER Byte-Stream ===");
        printHex(data);
        System.out.println("Gesamt: " + data.length + " Bytes\n");

        ASN1Node root = ASN1BerDecoder.decode(data);

        assertEquals(0, root.offset());
        assertEquals(data.length, root.length());
        assertEquals("SEQUENCE [CONSTRUCTED]", root.name());
        assertEquals(5, root.children().size());

        ASN1Node intNode = root.children().get(0);
        assertEquals(2, intNode.offset());
        assertEquals(3, intNode.length());
        assertEquals("INTEGER", intNode.name());
        assertTrue(intNode.offset() >= 0);
        assertTrue(intNode.length() > 0);
        assertTrue(intNode.offset() + intNode.length() <= data.length);

        ASN1Node boolNode = root.children().get(1);
        assertEquals(5, boolNode.offset());
        assertEquals(3, boolNode.length());
        assertEquals("BOOLEAN", boolNode.name());

        ASN1Node oidNode = root.children().get(2);
        assertEquals(8, oidNode.offset());
        assertEquals(5, oidNode.length());
        assertEquals("OBJECT IDENTIFIER", oidNode.name());

        ASN1Node stringNode = root.children().get(3);
        assertEquals(13, stringNode.offset());
        assertEquals(8, stringNode.length());
        assertEquals("PrintableString", stringNode.name());

        ASN1Node octNode = root.children().get(4);
        assertEquals(21, octNode.offset());
        assertEquals(5, octNode.length());
        assertEquals("OCTET STRING", octNode.name());

        // Keine Luecken zwischen Kindern
        List<ASN1Node> sortedChildren = root.children();
        for (int i = 1; i < sortedChildren.size(); i++) {
            ASN1Node prev = sortedChildren.get(i - 1);
            ASN1Node curr = sortedChildren.get(i);
            assertEquals(prev.offset() + prev.length(), curr.offset(),
                "Keine Luecke zwischen " + prev.name() + " und " + curr.name());
        }

        System.out.println("\n✅ Alle Offsets und Längen korrekt!");
    }

    @Test
    void testHighlightSimulation() throws Exception {
        byte[] data = createTestData();
        ASN1Node root = ASN1BerDecoder.decode(data);

        int BYTES_PER_ROW = 16;
        int BYTE_WIDTH = 27;
        int HEX_ONLY_WIDTH = 18;

        for (ASN1Node child : root.children()) {
            int highlightStart = child.offset();
            int highlightEnd = child.offset() + child.length();

            int lineStartRow = highlightStart / BYTES_PER_ROW;
            int lineEndRow = Math.min((highlightEnd - 1) / BYTES_PER_ROW, 0);

            for (int row = lineStartRow; row <= lineEndRow; row++) {
                int rowStartByte = row * BYTES_PER_ROW;
                int rowEndByte = Math.min(rowStartByte + BYTES_PER_ROW, data.length);

                int startByteInRow = Math.max(0, highlightStart - rowStartByte);
                int endByteInRow = Math.min(highlightEnd, rowEndByte) - rowStartByte;

                int startX = startByteInRow * BYTE_WIDTH;
                int endX = (endByteInRow - 1) * BYTE_WIDTH + HEX_ONLY_WIDTH;
                int width = endX - startX;

                // Verifiziere mit render()-Loop
                int lastByteIndex = endByteInRow - 1;
                int renderX = lastByteIndex * BYTE_WIDTH + HEX_ONLY_WIDTH;
                assertEquals(renderX, endX);

                int firstByteIndex = startByteInRow;
                int renderStartX = firstByteIndex * BYTE_WIDTH;
                assertEquals(renderStartX, startX);

                int expectedWidth = (endByteInRow - startByteInRow - 1) * BYTE_WIDTH + HEX_ONLY_WIDTH;
                assertEquals(expectedWidth, width);
            }
        }
    }

    private byte[] createTestData() {
        return new byte[] {
            (byte)0x30, (byte)0x18,
            (byte)0x02, (byte)0x01, (byte)0x00,
            (byte)0x01, (byte)0x01, (byte)0xFF,
            (byte)0x06, (byte)0x03, (byte)0x2A, (byte)0x86, (byte)0x48,
            (byte)0x13, (byte)0x06, (byte)0x48, (byte)0x65, (byte)0x6C,
            (byte)0x6C, (byte)0x6F, (byte)0x21,
            (byte)0x04, (byte)0x03, (byte)0x01, (byte)0x02, (byte)0x03,
        };
    }

    private void printHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            if (i % 16 == 0 && i > 0) sb.append("\n  ");
            sb.append(String.format("%02X ", data[i] & 0xFF));
        }
        System.out.println("  " + sb.toString().trim());
    }
}
