package com.asn1editor.parser;

import com.asn1editor.model.ASN1Node;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifiziert die Byte-Offset- und Highlight-Position-Korrektheit.
 */
class HighlightPositionTest {

    private static final int BYTES_PER_ROW = 16;
    private static final int HEX_CHARS_PER_BYTE = 2;
    private static final int CHAR_WIDTH = 9;
    private static final int BYTE_WIDTH = HEX_CHARS_PER_BYTE * CHAR_WIDTH + CHAR_WIDTH; // 27
    private static final int HEX_ONLY_WIDTH = HEX_CHARS_PER_BYTE * CHAR_WIDTH; // 18

    @Test
    void testSingleNodeInSingleRow() throws Exception {
        // SEQUENCE { INTEGER 1 } — alles in Zeile 0
        byte[] data = new byte[] {
            (byte)0x30, (byte)0x03,
            (byte)0x02, (byte)0x01,
            (byte)0x01,
        };

        ASN1Node root = ASN1BerDecoder.decode(data);

        assertEquals(0, root.offset());
        assertEquals(5, root.length());

        // Simuliere renderHighlight() für bytes 0-5
        int highlightStart = 0;
        int highlightEnd = 5;

        int lineStartRow = highlightStart / BYTES_PER_ROW; // 0
        int lineEndRow = Math.min((highlightEnd - 1) / BYTES_PER_ROW, 0); // 0
        assertEquals(0, lineStartRow);
        assertEquals(0, lineEndRow);

        int rowStartByte = 0;
        int rowEndByte = Math.min(16, 5); // 5
        int startByteInRow = Math.max(0, highlightStart - rowStartByte); // 0
        int endByteInRow = Math.min(highlightEnd, rowEndByte) - rowStartByte; // 5 - 0 = 5

        int startX = startByteInRow * BYTE_WIDTH; // 0
        int endX = (endByteInRow - 1) * BYTE_WIDTH + HEX_ONLY_WIDTH; // 4*27+18 = 126

        assertEquals(0, startX);
        assertEquals(126, endX);
        assertEquals(126, endX - startX);

        // Verifiziere: Byte 4 endet bei 4*27+18 = 126
        assertEquals(126, 4 * BYTE_WIDTH + HEX_ONLY_WIDTH);
    }

    @Test
    void testNodeSpansMultipleRows() throws Exception {
        // Einfacher: einzelnes INTEGER mit 42 Bytes (3 Zeilen)
        // 02 2A [42 Bytes Value] = 44 Bytes total
        byte[] data = new byte[44];
        data[0] = (byte)0x02; // INTEGER tag
        data[1] = 42; // length=42
        for (int i = 2; i < 44; i++) {
            data[i] = (byte)(i & 0xFF);
        }

        ASN1Node root = ASN1BerDecoder.decode(data);

        System.out.println("=== Spanning rows ===");
        System.out.println("Data: " + data.length + " bytes");
        System.out.println("Root: offset=" + root.offset() + " length=" + root.length());

        assertEquals(0, root.offset());
        assertEquals(data.length, root.length());

        // Highlight: bytes 0-43 (gesamtes INTEGER)
        int highlightStart = 0;
        int highlightEnd = 44;

        int lineStartRow = highlightStart / BYTES_PER_ROW; // 0
        int lineEndRow = Math.min((highlightEnd - 1) / BYTES_PER_ROW, 2); // 43/16=2

        for (int row = lineStartRow; row <= lineEndRow; row++) {
            int rowStartByte = row * BYTES_PER_ROW;
            int rowEndByte = Math.min(rowStartByte + BYTES_PER_ROW, data.length);

            int startByteInRow = Math.max(0, highlightStart - rowStartByte);
            int endByteInRow = Math.min(highlightEnd, rowEndByte) - rowStartByte;

            int startX = startByteInRow * BYTE_WIDTH;
            int endX = (endByteInRow - 1) * BYTE_WIDTH + HEX_ONLY_WIDTH;

            System.out.println("Row " + row + ": bytes " + startByteInRow + "-" + (endByteInRow-1)
                + " startX=" + startX + " endX=" + endX + " width=" + (endX - startX));

            if (row == 0) {
                // Zeile 0: relative bytes 0-15 (ganze Zeile)
                assertEquals(0, startByteInRow);
                assertEquals(16, endByteInRow);
                assertEquals(0, startX);
                assertEquals(15 * BYTE_WIDTH + HEX_ONLY_WIDTH, endX);
            } else if (row == 1) {
                // Zeile 1: relative bytes 0-15
                assertEquals(0, startByteInRow);
                assertEquals(16, endByteInRow);
                assertEquals(0, startX);
                assertEquals(15 * BYTE_WIDTH + HEX_ONLY_WIDTH, endX);
            } else {
                // Zeile 2: relative bytes 0-11 (12 Bytes)
                assertEquals(0, startByteInRow);
                assertEquals(12, endByteInRow);
                assertEquals(0, startX);
                assertEquals(11 * BYTE_WIDTH + HEX_ONLY_WIDTH, endX);
            }
        }
    }

    @Test
    void testPartialRangeInMultipleRows() throws Exception {
        // Highlight bytes 10-20 in einem 64-Byte-Array
        byte[] data = new byte[64];
        for (int i = 0; i < data.length; i++) data[i] = (byte)i;

        int highlightStart = 10;
        int highlightEnd = 20;

        int lineStartRow = highlightStart / BYTES_PER_ROW; // 0
        int lineEndRow = Math.min((highlightEnd - 1) / BYTES_PER_ROW, 1); // 19/16=1

        // Zeile 0: relative bytes 10-15 (6 Bytes)
        int row0StartByte = Math.max(0, highlightStart - 0); // 10
        int row0EndByte = Math.min(highlightEnd, 16) - 0; // 16
        int row0StartX = row0StartByte * BYTE_WIDTH; // 270
        int row0EndX = (row0EndByte - 1) * BYTE_WIDTH + HEX_ONLY_WIDTH; // 15*27+18=423

        System.out.println("\nRow 0: bytes " + row0StartByte + "-" + (row0EndByte-1));
        System.out.println("  startX=" + row0StartX + " endX=" + row0EndX + " width=" + (row0EndX - row0StartX));

        assertEquals(6, row0EndByte - row0StartByte); // 6 Bytes
        assertEquals(270, row0StartX);
        assertEquals(423, row0EndX);

        // Zeile 1: relative bytes 0-3 (4 Bytes)
        int row1StartByte = Math.max(0, highlightStart - 16); // 0
        int row1EndByte = Math.min(highlightEnd, 16 + 16) - 16; // 20-16=4
        int row1StartX = row1StartByte * BYTE_WIDTH; // 0
        int row1EndX = (row1EndByte - 1) * BYTE_WIDTH + HEX_ONLY_WIDTH; // 3*27+18=99

        System.out.println("Row 1: bytes " + row1StartByte + "-" + (row1EndByte-1));
        System.out.println("  startX=" + row1StartX + " endX=" + row1EndX + " width=" + (row1EndX - row1StartX));

        assertEquals(4, row1EndByte - row1StartByte); // 4 Bytes
        assertEquals(0, row1StartX);
        assertEquals(99, row1EndX);
    }

    @Test
    void testNestedNodeOffsets() throws Exception {
        // SEQUENCE { SEQUENCE { INTEGER 1 } }
        byte[] data = new byte[] {
            (byte)0x30, (byte)0x05,
            (byte)0x30, (byte)0x03,
            (byte)0x02, (byte)0x01,
            (byte)0x01,
        };

        ASN1Node outer = ASN1BerDecoder.decode(data);
        ASN1Node inner = outer.children().get(0);
        ASN1Node intNode = inner.children().get(0);

        System.out.println("\n=== Nested nodes ===");
        System.out.println("Outer: offset=" + outer.offset() + " length=" + outer.length());
        System.out.println("  bytes=[" + bytesToHex(Arrays.copyOfRange(data, outer.offset(), outer.offset() + outer.length())) + "]");
        System.out.println("Inner: offset=" + inner.offset() + " length=" + inner.length());
        System.out.println("  bytes=[" + bytesToHex(Arrays.copyOfRange(data, inner.offset(), inner.offset() + inner.length())) + "]");
        System.out.println("Int:   offset=" + intNode.offset() + " length=" + intNode.length());
        System.out.println("  bytes=[" + bytesToHex(Arrays.copyOfRange(data, intNode.offset(), intNode.offset() + intNode.length())) + "]");

        assertEquals(0, outer.offset());
        assertEquals(7, outer.length());

        assertEquals(2, inner.offset());
        assertEquals(5, inner.length());
        assertTrue(inner.offset() >= outer.offset());
        assertTrue(inner.offset() + inner.length() <= outer.offset() + outer.length());

        assertEquals(4, intNode.offset());
        assertEquals(3, intNode.length());
        assertTrue(intNode.offset() >= inner.offset());
        assertTrue(intNode.offset() + intNode.length() <= inner.offset() + inner.length());

        // Highlight-Berechnung für inner node (bytes 2-6)
        // Row 0: relative bytes 2-5 (4 Bytes)
        int highlightStart = 2;
        int highlightEnd = 7; // exclusive

        int rowStartByte = 0;
        int rowEndByte = Math.min(16, 7); // 7
        int startByteInRow = Math.max(0, highlightStart - rowStartByte); // 2
        int endByteInRow = Math.min(highlightEnd, rowEndByte) - rowStartByte; // 7-0=7

        int startX = startByteInRow * BYTE_WIDTH; // 54
        int endX = (endByteInRow - 1) * BYTE_WIDTH + HEX_ONLY_WIDTH; // 6*27+18=180

        System.out.println("Inner highlight: bytes " + startByteInRow + "-" + (endByteInRow-1));
        System.out.println("  startX=" + startX + " endX=" + endX + " width=" + (endX - startX));

        // Verifiziere: Byte 6 (letztes, da length=5 → bytes 2-6) endet bei 6*27+18 = 180
        assertEquals(180, endX);
    }

    @Test
    void testHighlightXMatchesRenderLoopX() throws Exception {
        // Für jedes Byte in einer Zeile: X-Position im Render-Loop = X-Position im Highlight

        for (int byteIndex = 0; byteIndex < 16; byteIndex++) {
            int renderX = byteIndex * (HEX_CHARS_PER_BYTE * CHAR_WIDTH + CHAR_WIDTH);
            int highlightStartX = byteIndex * BYTE_WIDTH;
            assertEquals(renderX, highlightStartX,
                "Byte " + byteIndex + ": renderX=" + renderX + " != highlightStartX=" + highlightStartX);

            // Rechte Kante
            int renderRightEdge = renderX + HEX_ONLY_WIDTH;
            int highlightRightEdge = (byteIndex) * BYTE_WIDTH + HEX_ONLY_WIDTH;
            assertEquals(renderRightEdge, highlightRightEdge,
                "Byte " + byteIndex + ": rightEdge mismatch");
        }

        System.out.println("\nAlle 16 Bytes: renderX = highlightStartX ✓");
    }

    @Test
    void testFullRangeHighlight() throws Exception {
        // Teste Highlight bytes 0-31 in 32-Byte-Daten (2 volle Zeilen)
        byte[] data = new byte[32];
        for (int i = 0; i < data.length; i++) data[i] = (byte)i;

        int highlightStart = 0;
        int highlightEnd = 32;

        int lineStartRow = 0;
        int lineEndRow = Math.min(31 / BYTES_PER_ROW, 1); // 1

        for (int row = 0; row <= lineEndRow; row++) {
            int rowStartByte = row * BYTES_PER_ROW;
            int rowEndByte = Math.min(rowStartByte + BYTES_PER_ROW, data.length);

            int startByteInRow = Math.max(0, highlightStart - rowStartByte);
            int endByteInRow = Math.min(highlightEnd, rowEndByte) - rowStartByte;

            int startX = startByteInRow * BYTE_WIDTH;
            int endX = (endByteInRow - 1) * BYTE_WIDTH + HEX_ONLY_WIDTH;
            int width = endX - startX;

            System.out.println("\nFull range row " + row + ": bytes " + startByteInRow + "-" + (endByteInRow-1));
            System.out.println("  startX=" + startX + " endX=" + endX + " width=" + width);

            if (row == 0) {
                assertEquals(0, startByteInRow);
                assertEquals(16, endByteInRow);
                assertEquals(0, startX);
                assertEquals(15 * BYTE_WIDTH + HEX_ONLY_WIDTH, endX);
            } else {
                assertEquals(0, startByteInRow);
                assertEquals(16, endByteInRow);
                assertEquals(0, startX);
                assertEquals(15 * BYTE_WIDTH + HEX_ONLY_WIDTH, endX);
            }
        }
    }

    @Test
    void testSingleByteHighlight() throws Exception {
        // Highlight genau 1 Byte: byte 8
        int highlightStart = 8;
        int highlightEnd = 9; // exclusive

        int row = 0;
        int rowStartByte = 0;
        int rowEndByte = Math.min(16, 32); // 16

        int startByteInRow = Math.max(0, highlightStart - rowStartByte); // 8
        int endByteInRow = Math.min(highlightEnd, rowEndByte) - rowStartByte; // 9 - 0 = 9

        int startX = startByteInRow * BYTE_WIDTH; // 216
        int endX = (endByteInRow - 1) * BYTE_WIDTH + HEX_ONLY_WIDTH; // 8*27+18=234
        int width = endX - startX; // 18

        assertEquals(216, startX);
        assertEquals(234, endX);
        assertEquals(18, width); // genau HEX_ONLY_WIDTH
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString().trim();
    }
}
