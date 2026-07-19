package com.asn1editor.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ASN1DocumentTest {

    @Test
    void testDocumentCreationWithSingleNode() {
        ASN1Node root = ASN1Node.leaf("root", "val");
        ASN1Document doc = new ASN1Document(root);
        assertEquals(root, doc.root());
        assertEquals(1, doc.definitions().size());
    }

    @Test
    void testDocumentCreationWithMultipleRoots() {
        ASN1Node first = ASN1Node.leaf("first", "val1");
        ASN1Node second = ASN1Node.leaf("second", "val2");
        ASN1Document doc = new ASN1Document(List.of(first, second));
        assertEquals(2, doc.definitions().size());
        assertEquals(first, doc.definitions().get(0));
        assertEquals(second, doc.definitions().get(1));
    }

    @Test
    void testNullRootThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ASN1Document((ASN1Node) null);
        });
    }

    @Test
    void testEmptyListThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ASN1Document(List.of());
        });
    }
}
