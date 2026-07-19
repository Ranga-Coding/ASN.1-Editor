import java.nio.file.*;
import java.util.Base64;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugReenc3 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] originalBytes = service.fromHexString(decodedContent);
        
        // Step 1: Original decode
        ASN1Node originalRoot = ASN1BerDecoder.decode(originalBytes);
        ASN1Document originalDoc = new ASN1Document(originalRoot);
        ASN1Node firstDef = originalDoc.definitions().get(0);
        
        System.out.println("=== Original tree - first UTF8String ===");
        ASN1Node utf8_1 = findUtf8String(firstDef, "SM-Test-PKI-DE");
        if (utf8_1 != null) {
            System.out.println("offset=" + utf8_1.offset() + " len=" + utf8_1.length());
            System.out.println("hex: " + HexUtils.toCompactHex(Arrays.copyOfRange(originalBytes, utf8_1.offset(), utf8_1.offset()+utf8_1.length())));
        }
        
        // Step 2: Re-encode
        byte[] encoded = ASN1BerEncoder.encode(firstDef);
        System.out.println("\nOriginal length: " + originalBytes.length);
        System.out.println("Encoded length: " + encoded.length);
        
        // Step 3: Re-decode the encoded bytes
        ASN1Node encodedRoot = ASN1BerDecoder.decode(encoded);
        ASN1Document encodedDoc = new ASN1Document(encodedRoot);
        ASN1Node encodedDef = encodedDoc.definitions().get(0);
        
        System.out.println("\n=== Re-encoded tree - first UTF8String ===");
        ASN1Node utf8_2 = findUtf8String(encodedDef, "SM-Test-PKI-DE");
        if (utf8_2 != null) {
            System.out.println("offset=" + utf8_2.offset() + " len=" + utf8_2.length());
            System.out.println("hex: " + HexUtils.toCompactHex(Arrays.copyOfRange(encoded, utf8_2.offset(), utf8_2.offset()+utf8_2.length())));
        }
        
        // Step 4: The user clicks a node with offset X in the tree.
        // highlightBytes(X, L) is called with currentHexBytes = encoded
        // The hex highlight range is set to (X, X+L)
        // What should be highlighted at position 73-88 in encoded?
        System.out.println("\n=== What gets highlighted when user clicks first UTF8String? ===");
        System.out.println("Node offset: " + (utf8_2 != null ? utf8_2.offset() : "null"));
        System.out.println("Node length: " + (utf8_2 != null ? utf8_2.length() : "null"));
        if (utf8_2 != null) {
            byte[] highlighted = Arrays.copyOfRange(encoded, utf8_2.offset(), utf8_2.offset()+utf8_2.length());
            System.out.println("Highlighted bytes: " + HexUtils.toHexString(highlighted));
            System.out.println("Expected: 0C 0E 53 4D 2D 54 65 73 74 2D 50 4B 49 2D 44");
            
            // But wait — the user said they click the FIRST UTF8String in the syntax tree
            // Let's check if there's a different UTF8String earlier
            System.out.println("\n=== All UTF8Strings in re-encoded tree ===");
            findAllUtf8Strings(encodedDef, encoded, 0);
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
    
    static void findAllUtf8Strings(ASN1Node node, byte[] data, int depth) {
        if (node.name().equals("UTF8String") && node.value() != null) {
            String indent = "  ".repeat(depth);
            String hex = node.offset() >= 0 && node.length() > 0 ?
                HexUtils.toCompactHex(Arrays.copyOfRange(data, node.offset(), node.offset()+Math.min(node.length(), data.length-node.offset()))) : "N/A";
            System.out.println(indent + "UTF8String '" + node.value() + "' offset=" + node.offset() + " len=" + node.length() + " hex=" + hex.substring(0, Math.min(30, hex.length())));
        }
        for (ASN1Node child : node.children()) {
            findAllUtf8Strings(child, data, depth+1);
        }
    }
}
