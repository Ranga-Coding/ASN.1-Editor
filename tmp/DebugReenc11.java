import java.nio.file.*;
import java.util.Base64;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugReenc11 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] originalBytes = service.fromHexString(decodedContent);
        
        ASN1Node originalRoot = ASN1BerDecoder.decode(originalBytes);
        ASN1Document originalDoc = new ASN1Document(originalRoot);
        ASN1Node firstDef = originalDoc.definitions().get(0);
        
        byte[] encoded = ASN1BerEncoder.encode(firstDef);
        ASN1Node root = ASN1BerDecoder.decode(encoded);
        ASN1Document encodedDoc = new ASN1Document(root);
        
        // Deep walk for UTF8Strings
        System.out.println("=== Finding UTF8Strings in re-encoded tree ===");
        findUtf8Strings(encodedDoc.definitions().get(0), encoded, 0);
        
        // Also check: does the re-encoded root have the same structure as original?
        System.out.println("\n=== Structure comparison ===");
        compareStructure(firstDef, 0, "ORIGINAL");
        
        ASN1Node reencodedRoot = ASN1BerDecoder.decode(encoded);
        ASN1Document reencodedDoc = new ASN1Document(reencodedRoot);
        compareStructure(reencodedDoc.definitions().get(0), 0, "RE-ENCODED");
    }
    
    static void findUtf8Strings(ASN1Node node, byte[] data, int depth) {
        if (node.name().equals("UTF8String") && node.value() != null) {
            System.out.println("  ".repeat(depth) + "UTF8String: '" + node.value() + "' offset=" + node.offset() + " len=" + node.length());
        }
        for (ASN1Node child : node.children()) {
            findUtf8Strings(child, data, depth+1);
        }
    }
    
    static void compareStructure(ASN1Node node, int depth, String label) {
        String indent = "  ".repeat(depth);
        if (node.children().size() == 0) {
            System.out.println(indent + label + " " + node.name() + " (leaf)");
        } else {
            System.out.println(indent + label + " " + node.name() + " children=" + node.children().size());
            for (ASN1Node child : node.children()) {
                compareStructure(child, depth+1, label);
            }
        }
    }
}
