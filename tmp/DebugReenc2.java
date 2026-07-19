import java.nio.file.*;
import java.util.Base64;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugReenc2 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] originalBytes = service.fromHexString(decodedContent);
        
        ASN1Node root = ASN1BerDecoder.decode(originalBytes);
        ASN1Document doc = new ASN1Document(root);
        
        byte[] encoded = ASN1BerEncoder.encode(doc.definitions().getFirst());
        ASN1Node encodedRoot = ASN1BerDecoder.decode(encoded);
        ASN1Document encodedDoc = new ASN1Document(encodedRoot);
        
        // Find ALL UTF8Strings in re-encoded tree
        System.out.println("=== UTF8Strings in re-encoded tree ===");
        findUtf8Strings(encodedDoc.definitions().get(0), encoded, 0);
        
        System.out.println("\n=== Tree structure (first level children) ===");
        ASN1Node def = encodedDoc.definitions().get(0);
        System.out.println("Root: " + def.name() + " offset=" + def.offset() + " len=" + def.length());
        for (ASN1Node child : def.children()) {
            dumpNode(child, encoded, 1);
        }
    }
    
    static void findUtf8Strings(ASN1Node node, byte[] data, int depth) {
        if (node.name().equals("UTF8String") && node.value() != null) {
            System.out.println("  ".repeat(depth) + "UTF8String: " + node.value() + 
                " offset=" + node.offset() + " len=" + node.length() +
                (node.offset() >= 0 ? " hex=" + HexUtils.toCompactHex(Arrays.copyOfRange(data, node.offset(), node.offset()+Math.min(node.length(), data.length-node.offset()))) : ""));
        }
        for (ASN1Node child : node.children()) {
            findUtf8Strings(child, data, depth+1);
        }
    }
    
    static void dumpNode(ASN1Node node, byte[] data, int depth) {
        String indent = "  ".repeat(depth);
        String hex = node.offset() >= 0 && node.length() > 0 ?
            HexUtils.toCompactHex(Arrays.copyOfRange(data, node.offset(), Math.min(data.length, node.offset()+Math.min(20, node.length())))) : "N/A";
        System.out.println(indent + node.name() + " offset=" + node.offset() + " len=" + node.length() + " hex=" + hex.substring(0, Math.min(40, hex.length())));
        for (ASN1Node child : node.children()) {
            dumpNode(child, data, depth+1);
        }
    }
}
