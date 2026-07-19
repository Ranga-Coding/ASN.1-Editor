import java.nio.file.*;
import java.util.Base64;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugReenc8 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] originalBytes = service.fromHexString(decodedContent);
        
        ASN1Node originalRoot = ASN1BerDecoder.decode(originalBytes);
        ASN1Document originalDoc = new ASN1Document(originalRoot);
        ASN1Node firstDef = originalDoc.definitions().get(0);
        
        System.out.println("firstDef: " + firstDef.name());
        System.out.println("firstDef children: " + firstDef.children().size());
        
        // Encode just the first definition
        byte[] encoded = ASN1BerEncoder.encode(firstDef);
        System.out.println("encoded length: " + encoded.length);
        System.out.println("encoded hex: " + HexUtils.toCompactHex(encoded));
        
        // Re-decode
        ASN1Node encodedRoot = ASN1BerDecoder.decode(encoded);
        System.out.println("encodedRoot: " + encodedRoot.name());
        System.out.println("encodedRoot children: " + encodedRoot.children().size());
        
        // The key: when we do new ASN1Document(encodedRoot), the document's
        // definitions() returns [encodedRoot], not [encodedRoot.children...]
        ASN1Document encodedDoc = new ASN1Document(encodedRoot);
        ASN1Node def = encodedDoc.definitions().get(0);
        System.out.println("def from doc: " + def.name());
        System.out.println("def children: " + def.children().size());
        
        // Now find the first UTF8String
        findUtf8Strings(def, encoded, 0);
    }
    
    static void findUtf8Strings(ASN1Node node, byte[] data, int depth) {
        if (node.name().equals("UTF8String") && node.value() != null) {
            System.out.println("  ".repeat(depth) + "UTF8String: " + node.value());
            if (node.offset() >= 0 && node.length() > 0) {
                System.out.println("  ".repeat(depth) + "  offset=" + node.offset() + " len=" + node.length() +
                    " hex=" + HexUtils.toCompactHex(Arrays.copyOfRange(data, node.offset(), node.offset()+Math.min(node.length(), data.length-node.offset()))));
            }
        }
        for (ASN1Node child : node.children()) {
            findUtf8Strings(child, data, depth+1);
        }
    }
}
