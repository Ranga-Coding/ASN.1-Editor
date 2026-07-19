package com.asn1editor.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class ASN1NodeTest {

    @Test
    void testLeafCreation() {
        ASN1Node node = ASN1Node.leaf("test", "value");
        assertEquals("test", node.name());
        assertEquals("value", node.value());
        assertTrue(node.isLeaf());
        assertTrue(node.children().isEmpty());
    }

    @Test
    void testInternalCreation() {
        ASN1Node child = ASN1Node.leaf("child", "val");
        ASN1Node parent = ASN1Node.internal("parent", List.of(child));

        assertEquals("parent", parent.name());
        assertNull(parent.value());
        assertFalse(parent.isLeaf());
        assertEquals(1, parent.children().size());
        assertEquals(child, parent.children().get(0));
    }

    @Test
    void testNullNameThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ASN1Node(null, null, List.of(), true, -1, 0);
        });
    }

    @Test
    void testImmutability() {
        ASN1Node node = ASN1Node.leaf("name", "val");
        // Records sind von Natur aus immutable hinsichtlich der Felder.
        // Wir prüfen hier die Unveränderlichkeit der Listen in den Factory-Methoden.

        ASN1Node parent = ASN1Node.internal("parent", List.of(node));
        assertThrows(UnsupportedOperationException.class, () -> {
            parent.children().add(node);
        });
    }
}
