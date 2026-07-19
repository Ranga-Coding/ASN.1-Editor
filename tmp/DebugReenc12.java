import java.nio.file.*;
import java.util.Base64;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugReenc12 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] originalBytes = service.fromHexString(decodedContent);
        
        ASN1Node originalRoot = ASN1BerDecoder.decode(originalBytes);
        ASN1Document originalDoc = new ASN1Document(originalRoot);
        
        // Schritt 1: buildTreeView mit originalDoc
        // Die TreeView-Knoten sind die gleichen Referenzen wie originalDoc.definitions()
        
        // Schritt 2: getTreeViewDocument() holt dieselben Knoten
        // syncHexFromTree codiert diese Knoten
        byte[] encoded = ASN1BerEncoder.encode(originalDoc.definitions().getFirst());
        System.out.println("encoded length: " + encoded.length);
        System.out.println("encoded first 20: " + HexUtils.toCompactHex(Arrays.copyOf(encoded, Math.min(20, encoded.length))));
        
        // Schritt 3: Rebuild mit neuem Root
        ASN1Node newRoot = ASN1BerDecoder.decode(encoded);
        ASN1Document newDoc = new ASN1Document(newRoot);
        
        // Die neuen Knoten haben andere Offset-Informationen
        ASN1Node newDef = newDoc.definitions().get(0);
        System.out.println("newDef children: " + newDef.children().size());
        
        // Find first UTF8String in newDoc
        ASN1Node utf8Node = findFirstUtf8String(newDef, encoded);
        if (utf8Node != null) {
            System.out.println("UTF8String in re-encoded tree:");
            System.out.println("  offset=" + utf8Node.offset() + " len=" + utf8Node.length());
            System.out.println("  value=" + utf8Node.value());
            byte[] highlighted = Arrays.copyOfRange(encoded, utf8Node.offset(), utf8Node.offset() + utf8Node.length());
            System.out.println("  hex=" + HexUtils.toHexString(highlighted));
        } else {
            System.out.println("No UTF8String found in re-encoded tree!");
            // Walk tree and print all node types
            printTree(newDef, encoded, "");
        }
    }
    
    static ASN1Node findFirstUtf8String(ASN1Node node, byte[] data) {
        if (node.name().equals("UTF8String") && node.value() != null) {
            return node;
        }
        for (ASN1Node child : node.children()) {
            ASN1Node found = findFirstUtf8String(child, data);
            if (found != null) return found;
        }
        return null;
    }
    
    static void printTree(ASN1Node node, byte[] data, String indent) {
        System.out.println(indent + node.name() + " offset=" + node.offset() + " len=" + node.length());
        for (ASN1Node child : node.children()) {
            printTree(child, data, indent + "  ");
        }
    }
}
