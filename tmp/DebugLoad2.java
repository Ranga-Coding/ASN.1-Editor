import java.nio.file.*;
import java.util.Base64;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugLoad2 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] decodedBytes = service.fromHexString(decodedContent);
        
        ASN1Node root = ASN1BerDecoder.decode(decodedBytes);
        
        // Find ALL UTF8Strings in the tree
        System.out.println("=== All UTF8Strings found ===");
        findUtf8Strings(root, decodedBytes, 0);
        
        // Now check what the ASN1Service.parse produces
        System.out.println("\n=== Parsing decoded text ===");
        ASN1Document doc = service.parse(decodedContent, Paths.get("TEST.crmf"));
        System.out.println("Definitions: " + doc.definitions().size());
        ASN1Node docRoot = doc.definitions().get(0);
        findUtf8Strings(docRoot, new byte[0], 0);
    }
    
    static void findUtf8Strings(ASN1Node node, byte[] data, int depth) {
        if (node.name().equals("UTF8String") && node.value() != null) {
            String indent = "  ".repeat(depth);
            String hex = "";
            if (node.offset() >= 0 && node.length() > 0) {
                hex = " offset=" + node.offset() + " len=" + node.length() + " hex=" + 
                    HexUtils.toCompactHex(Arrays.copyOfRange(data, Math.max(0,node.offset()), Math.min(data.length, node.offset()+node.length())));
            }
            System.out.println(indent + "UTF8String: " + node.value() + hex);
        }
        for (ASN1Node child : node.children()) {
            findUtf8Strings(child, data, depth+1);
        }
    }
}
