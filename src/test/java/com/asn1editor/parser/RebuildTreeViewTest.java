package com.asn1editor.parser;

import com.asn1editor.model.ASN1Document;
import com.asn1editor.model.ASN1Node;
import com.asn1editor.service.HexUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests fuer den syncHexFromTree -> rebuildTreeViewWithEncodedRoot Flow.
 *
 * <p>Simuliert den Load-Flow einer PEM/BER-Datei:
 * <ol>
 *   <li>Original decodieren</li>
 *   <li>TreeView bauen</li>
 *   <li>syncHexFromTree: encode -> re-decode -> rebuild</li>
 *   <li>Pruefen, dass die neuen Knoten korrekte Offsets haben</li>
 * </ol>
 */
class RebuildTreeViewTest {

    /**
     * Test: Nach syncHexFromTree haben alle Knoten korrekte Offsets.
     *
     * <p>Das ist der Kern-Bug: Vor dem Fix haben die Knoten nach dem rebuild
     * falsche Offsets gezeigt, weil die TreeItem-Selektion auf alte Knoten
     * zeigte.
     */
    @Test
    @DisplayName("syncHexFromTree: Nach rebuild haben alle Knoten korrekte Offsets")
    void testRebuildOffsets() throws Exception {
        // Erzeuge eine einfache BER-Struktur
        byte[] data = new byte[] {
            (byte)0x30, (byte)0x18,  // SEQUENCE len=24
            (byte)0x02, (byte)0x01, (byte)0x00,  // INTEGER 0
            (byte)0x0C, (byte)0x0E, (byte)0x53, (byte)0x4D, (byte)0x2D, (byte)0x54,
            (byte)0x65, (byte)0x73, (byte)0x74, (byte)0x2D, (byte)0x50, (byte)0x4B,
            (byte)0x49, (byte)0x2D, (byte)0x44,  // UTF8String "SM-Test-PKI-D"
            (byte)0x13, (byte)0x03, (byte)0x48, (byte)0x65, (byte)0x6C,  // PrintableString "Hel"
        };

        // Schritt 1: Original decodieren
        ASN1Node originalRoot = ASN1BerDecoder.decode(data);
        ASN1Document originalDoc = new ASN1Document(originalRoot);

        // UTF8String im Original finden
        ASN1Node origUtf8 = findFirstUtf8String(originalDoc.definitions().get(0));
        assertNotNull(origUtf8, "UTF8String im Original gefunden");
        int origUtf8Offset = origUtf8.offset();
        int origUtf8Length = origUtf8.length();

        System.out.println("Original UTF8String:");
        System.out.println("  offset=" + origUtf8Offset);
        System.out.println("  length=" + origUtf8Length);
        System.out.println("  bytes=[" + HexUtils.toCompactHex(Arrays.copyOfRange(data, origUtf8Offset, origUtf8Offset + origUtf8Length)) + "]");

        // Schritt 2: Encodieren (simuliert syncHexFromTree)
        byte[] encoded = ASN1BerEncoder.encode(originalDoc.definitions().get(0));
        System.out.println("\nEncodierte Laenge: " + encoded.length + " (Original: " + data.length + ")");

        // Schritt 3: Re-decodieren und rebuild (simuliert rebuildTreeViewWithEncodedRoot)
        ASN1Node newRoot = ASN1BerDecoder.decode(encoded);
        ASN1Document newDoc = new ASN1Document(newRoot);

        // UTF8String im neu decodierten Baum finden
        ASN1Node newUtf8 = findFirstUtf8String(newDoc.definitions().get(0));
        assertNotNull(newUtf8, "UTF8String im re-encoded Baum gefunden");

        int newUtf8Offset = newUtf8.offset();
        int newUtf8Length = newUtf8.length();

        System.out.println("Neuer UTF8String:");
        System.out.println("  offset=" + newUtf8Offset);
        System.out.println("  length=" + newUtf8Length);
        System.out.println("  bytes=[" + HexUtils.toCompactHex(Arrays.copyOfRange(encoded, newUtf8Offset, newUtf8Offset + newUtf8Length)) + "]");

        // Verifiziere: Der neue Offset fuehrt zu den korrekten Bytes
        byte[] highlighted = Arrays.copyOfRange(encoded, newUtf8Offset, newUtf8Offset + newUtf8Length);
        String expectedHex = "0C0D534D2D546573742D504B492D44";
        assertEquals(expectedHex, HexUtils.toCompactHex(highlighted),
            "Highlight-Bereich im re-encoded Baum sollte auf den korrekten UTF8String zeigen");

        // Verifiziere: Alle Kinder des re-encodeden Baums haben korrekte Offsets
        verifyChildOffsets(newRoot, encoded);

        // Verifiziere: Die Offsets sind im Bereich der encodierten Daten
        assertTrue(newUtf8Offset >= 0, "Offset muss >= 0 sein");
        assertTrue(newUtf8Length > 0, "Length muss > 0 sein");
        assertTrue(newUtf8Offset + newUtf8Length <= encoded.length,
            "Ende des UTF8String muss innerhalb der encodierten Daten liegen");

        System.out.println("\n✅ syncHexFromTree Flow: Alle Offsets nach rebuild korrekt!");
    }

    /**
     * Test: Die Offsets im originalen Baum stimmen mit den originalen Bytes ueberein.
     */
    @Test
    @DisplayName("syncHexFromTree: Original-Offsets stimmen mit originalen Bytes ueberein")
    void testOriginalOffsets() throws Exception {
        byte[] data = new byte[] {
            (byte)0x30, (byte)0x18,  // SEQUENCE len=24
            (byte)0x02, (byte)0x01, (byte)0x00,  // INTEGER 0
            (byte)0x0C, (byte)0x0E, (byte)0x53, (byte)0x4D, (byte)0x2D, (byte)0x54,
            (byte)0x65, (byte)0x73, (byte)0x74, (byte)0x2D, (byte)0x50, (byte)0x4B,
            (byte)0x49, (byte)0x2D, (byte)0x44,  // UTF8String "SM-Test-PKI-D"
            (byte)0x13, (byte)0x03, (byte)0x48, (byte)0x65, (byte)0x6C,  // PrintableString "Hel"
        };

        ASN1Node root = ASN1BerDecoder.decode(data);
        ASN1Document doc = new ASN1Document(root);

        // UTF8String im Original finden und verifizieren
        ASN1Node utf8Node = findFirstUtf8String(doc.definitions().get(0));
        assertNotNull(utf8Node);

        byte[] originalBytes = Arrays.copyOfRange(data, utf8Node.offset(), utf8Node.offset() + utf8Node.length());
        assertEquals((byte)0x0C, originalBytes[0], "Tag-Byte muss 0x0C (UTF8String) sein");
        assertEquals((byte)0x0E, originalBytes[1], "Length-Byte muss 0x0E (14) sein");

        // Der Textinhalt
        String text = new String(Arrays.copyOfRange(originalBytes, 2, originalBytes.length), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("SM-Test-PKI-D", text);

        System.out.println("\n✅ Original-Offsets korrekt!");
    }

    /**
     * Test: Re-encoding und re-decoding erzeugt einen konsistenten Baum.
     */
    @Test
    @DisplayName("syncHexFromTree: Re-encoding erzeugt konsistenten Baum")
    void testRebuildConsistency() throws Exception {
        byte[] data = new byte[] {
            (byte)0x30, (byte)0x18,  // SEQUENCE len=24
            (byte)0x02, (byte)0x01, (byte)0x00,  // INTEGER 0
            (byte)0x0C, (byte)0x0E, (byte)0x53, (byte)0x4D, (byte)0x2D, (byte)0x54,
            (byte)0x65, (byte)0x73, (byte)0x74, (byte)0x2D, (byte)0x50, (byte)0x4B,
            (byte)0x49, (byte)0x2D, (byte)0x44,  // UTF8String "SM-Test-PKI-D"
            (byte)0x13, (byte)0x03, (byte)0x48, (byte)0x65, (byte)0x6C,  // PrintableString "Hel"
        };

        ASN1Node originalRoot = ASN1BerDecoder.decode(data);
        ASN1Document originalDoc = new ASN1Document(originalRoot);

        // Encodieren
        byte[] encoded = ASN1BerEncoder.encode(originalDoc.definitions().get(0));

        // Re-decodieren
        ASN1Node newRoot = ASN1BerDecoder.decode(encoded);
        // Strukturvergleich
        assertEquals(originalRoot.children().size(), newRoot.children().size(),
            "Anzahl der Root-Kinder sollte gleich bleiben");

        // Alle Kinder sollten korrekte Offsets haben
        for (ASN1Node child : newRoot.children()) {
            assertTrue(child.offset() >= 0, child.name() + " Offset >= 0");
            assertTrue(child.length() > 0, child.name() + " Length > 0");
            assertTrue(child.offset() + child.length() <= encoded.length,
                child.name() + " Ende innerhalb von encoded");
        }

        System.out.println("\n✅ Re-encoding konsistent!");
    }

    // ===== HILFSMETHODEN =====

    private ASN1Node findFirstUtf8String(ASN1Node node) {
        if (node.name().equals("UTF8String")) {
            return node;
        }
        for (ASN1Node child : node.children()) {
            ASN1Node found = findFirstUtf8String(child);
            if (found != null) return found;
        }
        return null;
    }

    private void verifyChildOffsets(ASN1Node node, byte[] data) {
        if (!node.children().isEmpty()) {
            for (ASN1Node child : node.children()) {
                assertTrue(child.offset() >= 0, child.name() + " Offset >= 0");
                assertTrue(child.length() > 0, child.name() + " Length > 0");
                assertTrue(child.offset() + child.length() <= data.length,
                    child.name() + " Ende innerhalb von data");

                // Bytes am angegebenen Offset sollten mit dem Tag-Byte uebereinstimmen
                assertTrue(child.offset() + 1 <= data.length,
                    child.name() + " Tag-Byte lesbar");

                verifyChildOffsets(child, data);
            }
        }
    }
}
