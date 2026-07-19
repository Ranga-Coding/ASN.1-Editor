import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;
import java.util.Arrays;

public class SimpleRunTests {
    static int passed = 0;
    static int failed = 0;
    
    public static void main(String[] args) throws Exception {
        testRebuildOffsets();
        testOriginalOffsets();
        testRebuildConsistency();
        
        System.out.println("\n==========");
        System.out.println("Ergebnis: " + passed + " bestanden, " + failed + " fehlgeschlagen");
        System.exit(failed > 0 ? 1 : 0);
    }
    
    static void assertEquals(String msg, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            failed++;
            System.out.println("❌ " + msg);
            System.out.println("  expected: " + expected);
            System.out.println("  actual:   " + actual);
        } else {
            passed++;
            System.out.println("✅ " + msg);
        }
    }
    
    static void assertTrue(String msg, boolean cond) {
        if (!cond) {
            failed++;
            System.out.println("❌ " + msg);
        } else {
            passed++;
            System.out.println("✅ " + msg);
        }
    }
    
    static void assertNotNull(String msg, Object obj) {
        if (obj == null) {
            failed++;
            System.out.println("❌ " + msg);
        } else {
            passed++;
            System.out.println("✅ " + msg);
        }
    }
    
    // ===== TESTS =====
    
    static void testRebuildOffsets() throws Exception {
        System.out.println("\n--- testRebuildOffsets ---");
        byte[] data = new byte[] {
            (byte)0x30, (byte)0x17,
            (byte)0x02, (byte)0x01, (byte)0x00,
            (byte)0x0C, (byte)0x0D, (byte)0x53, (byte)0x4D, (byte)0x2D, (byte)0x54,
            (byte)0x65, (byte)0x73, (byte)0x74, (byte)0x2D, (byte)0x50, (byte)0x4B,
            (byte)0x49, (byte)0x2D, (byte)0x44,
            (byte)0x13, (byte)0x03, (byte)0x48, (byte)0x65, (byte)0x6C,
        };
        
        ASN1Node originalRoot = ASN1BerDecoder.decode(data);
        ASN1Document originalDoc = new ASN1Document(originalRoot);
        
        ASN1Node origUtf8 = findFirstUtf8String(originalDoc.definitions().get(0));
        assertNotNull("UTF8String im Original gefunden", origUtf8);
        
        int origOffset = origUtf8.offset();
        System.out.println("  Original UTF8String: offset=" + origOffset);
        
        byte[] encoded = ASN1BerEncoder.encode(originalDoc.definitions().get(0));
        System.out.println("  Encodierte Laenge: " + encoded.length + " (Original: " + data.length + ")");
        
        ASN1Node newRoot = ASN1BerDecoder.decode(encoded);
        ASN1Document newDoc = new ASN1Document(newRoot);
        
        ASN1Node newUtf8 = findFirstUtf8String(newDoc.definitions().get(0));
        assertNotNull("UTF8String im re-encoded Baum gefunden", newUtf8);
        
        int newOffset = newUtf8.offset();
        int newLength = newUtf8.length();
        System.out.println("  Neuer UTF8String: offset=" + newOffset + " length=" + newLength);
        
        byte[] highlighted = Arrays.copyOfRange(encoded, newOffset, newOffset + newLength);
        String actualHex = HexUtils.toCompactHex(highlighted);
        assertEquals("Highlight-Bereich zeigt auf korrekten UTF8String", 
                     "0C0D534D2D546573742D504B492D44", actualHex);
        
        assertTrue("Offset >= 0", newOffset >= 0);
        assertTrue("Length > 0", newLength > 0);
        assertTrue("Ende innerhalb von encoded", newOffset + newLength <= encoded.length);
    }
    
    static void testOriginalOffsets() throws Exception {
        System.out.println("\n--- testOriginalOffsets ---");
        byte[] data = new byte[] {
            (byte)0x30, (byte)0x17,
            (byte)0x02, (byte)0x01, (byte)0x00,
            (byte)0x0C, (byte)0x0D, (byte)0x53, (byte)0x4D, (byte)0x2D, (byte)0x54,
            (byte)0x65, (byte)0x73, (byte)0x74, (byte)0x2D, (byte)0x50, (byte)0x4B,
            (byte)0x49, (byte)0x2D, (byte)0x44,
            (byte)0x13, (byte)0x03, (byte)0x48, (byte)0x65, (byte)0x6C,
        };
        
        ASN1Node root = ASN1BerDecoder.decode(data);
        ASN1Document doc = new ASN1Document(root);
        ASN1Node utf8Node = findFirstUtf8String(doc.definitions().get(0));
        assertNotNull("UTF8String gefunden", utf8Node);
        
        byte[] originalBytes = Arrays.copyOfRange(data, utf8Node.offset(), utf8Node.offset() + utf8Node.length());
        assertEquals("Tag-Byte ist 0x0C", (byte)0x0C, (byte)originalBytes[0]);
        assertEquals("Length-Byte ist 0x0D", (byte)0x0D, (byte)originalBytes[1]);
        
        String text = new String(Arrays.copyOfRange(originalBytes, 2, originalBytes.length), 
                                java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("Textinhalt korrekt", "SM-Test-PKI-D", text);
    }
    
    static void testRebuildConsistency() throws Exception {
        System.out.println("\n--- testRebuildConsistency ---");
        byte[] data = new byte[] {
            (byte)0x30, (byte)0x17,
            (byte)0x02, (byte)0x01, (byte)0x00,
            (byte)0x0C, (byte)0x0D, (byte)0x53, (byte)0x4D, (byte)0x2D, (byte)0x54,
            (byte)0x65, (byte)0x73, (byte)0x74, (byte)0x2D, (byte)0x50, (byte)0x4B,
            (byte)0x49, (byte)0x2D, (byte)0x44,
            (byte)0x13, (byte)0x03, (byte)0x48, (byte)0x65, (byte)0x6C,
        };
        
        ASN1Node originalRoot = ASN1BerDecoder.decode(data);
        ASN1Document originalDoc = new ASN1Document(originalRoot);
        byte[] encoded = ASN1BerEncoder.encode(originalDoc.definitions().get(0));
        ASN1Node newRoot = ASN1BerDecoder.decode(encoded);
        ASN1Document newDoc = new ASN1Document(newRoot);
        
        assertEquals("Anzahl Root-Kinder gleich", 
                     originalRoot.children().size(), newRoot.children().size());
        
        for (ASN1Node child : newRoot.children()) {
            assertTrue(child.name() + " Offset >= 0", child.offset() >= 0);
            assertTrue(child.name() + " Length > 0", child.length() > 0);
            assertTrue(child.name() + " Ende innerhalb", 
                       child.offset() + child.length() <= encoded.length);
        }
    }
    
    static ASN1Node findFirstUtf8String(ASN1Node node) {
        if (node.name().equals("UTF8String")) return node;
        for (ASN1Node child : node.children()) {
            ASN1Node found = findFirstUtf8String(child);
            if (found != null) return found;
        }
        return null;
    }
}
