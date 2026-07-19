import java.nio.file.*;
import java.util.Base64;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugReenc {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] originalBytes = service.fromHexString(decodedContent);
        
        System.out.println("Original length: " + originalBytes.length);
        System.out.println("Original first 60 bytes: " + HexUtils.toHexString(Arrays.copyOf(originalBytes, Math.min(60, originalBytes.length))));
        
        // Decode
        ASN1Node root = ASN1BerDecoder.decode(originalBytes);
        ASN1Document doc = new ASN1Document(root);
        
        // Re-encode
        byte[] encoded = ASN1BerEncoder.encode(doc.definitions().getFirst());
        System.out.println("Encoded length: " + encoded.length);
        System.out.println("Encoded first 60 bytes: " + HexUtils.toHexString(Arrays.copyOf(encoded, Math.min(60, encoded.length))));
        
        // Compare
        if (Arrays.equals(originalBytes, encoded)) {
            System.out.println("IDENTICAL — offsets preserved");
        } else {
            System.out.println("DIFFERENT — offsets may not match!");
            // Find first diff
            int diff = -1;
            for (int i = 0; i < Math.min(originalBytes.length, encoded.length); i++) {
                if (originalBytes[i] != encoded[i]) {
                    diff = i;
                    break;
                }
            }
            System.out.println("First difference at byte " + diff);
            if (diff >= 0) {
                System.out.println("Original at diff: " + HexUtils.toHexString(Arrays.copyOfRange(originalBytes, Math.max(0,diff-2), Math.min(originalBytes.length, diff+10))));
                System.out.println("Encoded at diff: " + HexUtils.toHexString(Arrays.copyOfRange(encoded, Math.max(0,diff-2), Math.min(encoded.length, diff+10))));
            }
        }
        
        // Now find the UTF8String in re-decoded tree
        ASN1Node encodedRoot = ASN1BerDecoder.decode(encoded);
        ASN1Document encodedDoc = new ASN1Document(encodedRoot);
        ASN1Node utf8Node = findUtf8String(encodedDoc.definitions().get(0), "SM-Test-PKI-DE");
        if (utf8Node != null && utf8Node.offset() >= 0) {
            System.out.println("\n=== UTF8String in re-encoded tree ===");
            System.out.println("offset: " + utf8Node.offset());
            System.out.println("length: " + utf8Node.length());
            byte[] nodeBytes = Arrays.copyOfRange(encoded, utf8Node.offset(), utf8Node.offset() + utf8Node.length());
            System.out.println("hex: " + HexUtils.toHexString(nodeBytes));
            System.out.println("expected: " + "0C 0E 53 4D 2D 54 65 73 74 2D 50 4B 49 2D 44");
        } else {
            System.out.println("UTF8String 'SM-Test-PKI-DE' not found in re-encoded tree!");
        }
    }
    
    static ASN1Node findUtf8String(ASN1Node node, String expectedValue) {
        if (node.name().equals("UTF8String") && node.value() != null && node.value().equals(expectedValue)) {
            return node;
        }
        for (ASN1Node child : node.children()) {
            ASN1Node found = findUtf8String(child, expectedValue);
            if (found != null) return found;
        }
        return null;
    }
}
