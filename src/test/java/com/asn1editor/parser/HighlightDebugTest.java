package com.asn1editor.parser;

import com.asn1editor.model.ASN1Node;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifiziert dass ASN1Node.offset/length exakt auf die Binärdaten passen.
 *
 * <p>Wichtig für die Byte-Highlighting-Funktion im Hex-Editor:
 * Wenn ein TreeView-Knoten ausgewählt wird, müssen die darin gespeicherten
 * TLV-Byte-Offsets (offset, length) exakt auf die entsprechenden Bytes
 * im currentHexBytes-Array zeigen.
 */
class HighlightDebugTest {

    @Test
    void testSimpleSequenceWithInteger() throws Exception {
        // SEQUENCE { INTEGER 1 }
        //   0    1    2    3    4
        // 30 03 02 01 01
        byte[] data = new byte[] {
            (byte)0x30, (byte)0x03,
            (byte)0x02, (byte)0x01,
            (byte)0x01,
        };

        ASN1Node root = ASN1BerDecoder.decode(data);

        // Root (SEQUENCE) deckt den gesamten Stream ab
        assertEquals(0, root.offset());
        assertEquals(data.length, root.length());

        // Root-Bytes mit Original abgleichen
        for (int i = 0; i < root.length(); i++) {
            assertEquals(data[i], data[root.offset() + i]);
        }

        // Child (INTEGER) muss korrekte Offsets haben
        List<ASN1Node> children = root.children();
        assertEquals(1, children.size());

        ASN1Node intNode = children.get(0);
        assertEquals("INTEGER", intNode.name());
        assertEquals(2, intNode.offset());
        assertEquals(3, intNode.length());

        // Verifiziere: INTEGER-Bytes bei offset 2 im Original
        assertEquals((byte)0x02, data[intNode.offset()]);       // tag
        assertEquals((byte)0x01, data[intNode.offset() + 1]);   // length
        assertEquals((byte)0x01, data[intNode.offset() + 2]);   // value
    }

    @Test
    void testSimpleIntegerStandalone() throws Exception {
        // Einzelnes INTEGER: 02 01 01
        byte[] data = new byte[] {
            (byte)0x02, (byte)0x01,
            (byte)0x01,
        };

        ASN1Node root = ASN1BerDecoder.decode(data);

        assertEquals(0, root.offset());
        assertEquals(data.length, root.length());
        assertEquals(3, root.length());
    }

    @Test
    void testNestedSequence() throws Exception {
        // SEQUENCE { SEQUENCE { INTEGER 1 } }
        //   0    1    2    3    4    5    6
        // 30 05 30 03 02 01 01
        byte[] data = new byte[] {
            (byte)0x30, (byte)0x05,
            (byte)0x30, (byte)0x03,
            (byte)0x02, (byte)0x01,
            (byte)0x01,
        };

        ASN1Node outer = ASN1BerDecoder.decode(data);
        assertEquals(0, outer.offset());
        assertEquals(7, outer.length());

        List<ASN1Node> innerSeq = outer.children();
        assertEquals(1, innerSeq.size());

        ASN1Node inner = innerSeq.get(0);
        assertEquals(2, inner.offset());
        assertEquals(5, inner.length());

        // Child des inneren Sequences
        List<ASN1Node> intNode = inner.children();
        assertEquals(1, intNode.size());
        assertEquals(4, intNode.get(0).offset());
        assertEquals(3, intNode.get(0).length());
    }
}
