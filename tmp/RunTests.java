import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.Arrays;

/**
 * Einfacher Test-Runner, der die Tests direkt aufruft.
 */
public class RunTests {
    public static void main(String[] args) throws Exception {
        int passed = 0;
        int failed = 0;

        try {
            testRebuildOffsets();
            passed++;
            System.out.println("✅ testRebuildOffsets");
        } catch (AssertionError e) {
            failed++;
            System.out.println("❌ testRebuildOffsets: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            testOriginalOffsets();
            passed++;
            System.out.println("✅ testOriginalOffsets");
        } catch (AssertionError e) {
            failed++;
            System.out.println("❌ testOriginalOffsets: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            testRebuildConsistency();
            passed++;
            System.out.println("✅ testRebuildConsistency");
        } catch (AssertionError e) {
            failed++;
            System.out.println("❌ testRebuildConsistency: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n==========");
        System.out.println("Ergebnis: " + passed + " bestanden, " + failed + " fehlgeschlagen");
        System.exit(failed > 0 ? 1 : 0);
    }

    // ===== TESTS =====

    static void testRebuildOffsets() throws Exception {
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
        Assertions.assertNotNull(origUtf8, "UTF8String im Original gefunden");

        byte[] encoded = ASN1BerEncoder.encode(originalDoc.definitions().get(0));
        ASN1Node newRoot = ASN1BerDecoder.decode(encoded);
        ASN1Document newDoc = new ASN1Document(newRoot);

        ASN1Node newUtf8 = findFirstUtf8String(newDoc.definitions().get(0));
        Assertions.assertNotNull(newUtf8, "UTF8String im re-encoded Baum gefunden");

        byte[] highlighted = Arrays.copyOfRange(encoded, newUtf8.offset(), newUtf8.offset() + newUtf8.length());
        String actualHex = HexUtils.toCompactHex(highlighted);
        String expectedHex = "0C0D534D2D546573742D504B492D44";
        Assertions.assertEquals(expectedHex, actualHex,
            "Highlight-Bereich im re-encoded Baum sollte auf den korrekten UTF8String zeigen");

        Assertions.assertTrue(newUtf8.offset() >= 0);
        Assertions.assertTrue(newUtf8.length() > 0);
        Assertions.assertTrue(newUtf8.offset() + newUtf8.length() <= encoded.length);

        System.out.println("  Original UTF8String: offset=" + origUtf8.offset() + " length=" + origUtf8.length());
        System.out.println("  Neuer UTF8String: offset=" + newUtf8.offset() + " length=" + newUtf8.length());
        System.out.println("  Highlighted hex: " + actualHex);
    }

    static void testOriginalOffsets() throws Exception {
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
        Assertions.assertNotNull(utf8Node);

        byte[] originalBytes = Arrays.copyOfRange(data, utf8Node.offset(), utf8Node.offset() + utf8Node.length());
        Assertions.assertEquals((byte)0x0C, originalBytes[0]);
        Assertions.assertEquals((byte)0x0E, originalBytes[1]);

        String text = new String(Arrays.copyOfRange(originalBytes, 2, originalBytes.length), java.nio.charset.StandardCharsets.UTF_8);
        Assertions.assertEquals("SM-Test-PKI-D", text);
    }

    static void testRebuildConsistency() throws Exception {
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

        Assertions.assertEquals(originalRoot.children().size(), newRoot.children().size());

        for (ASN1Node child : newRoot.children()) {
            Assertions.assertTrue(child.offset() >= 0, child.name() + " Offset >= 0");
            Assertions.assertTrue(child.length() > 0, child.name() + " Length > 0");
            Assertions.assertTrue(child.offset() + child.length() <= encoded.length,
                child.name() + " Ende innerhalb von encoded");
        }
    }

    // ===== HILFSMETHODEN =====

    static ASN1Node findFirstUtf8String(ASN1Node node) {
        if (node.name().equals("UTF8String")) {
            return node;
        }
        for (ASN1Node child : node.children()) {
            ASN1Node found = findFirstUtf8String(child);
            if (found != null) return found;
        }
        return null;
    }
}
