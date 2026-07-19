import java.nio.file.*;
import java.util.Base64;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugReenc6 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] originalBytes = service.fromHexString(decodedContent);
        
        ASN1Node originalRoot = ASN1BerDecoder.decode(originalBytes);
        ASN1Document originalDoc = new ASN1Document(originalRoot);
        ASN1Node firstDef = originalDoc.definitions().get(0);
        
        // Re-encode
        byte[] encoded = ASN1BerEncoder.encode(firstDef);
        
        // Re-decode
        ASN1Node encodedRoot = ASN1BerDecoder.decode(encoded);
        ASN1Document encodedDoc = new ASN1Document(encodedRoot);
        ASN1Node encodedDef = encodedDoc.definitions().get(0);
        
        System.out.println("encodedDef children: " + encodedDef.children().size());
        
        // Walk re-encoded tree to find first UTF8String
        walkTree(encodedDef, "", encoded);
    }
    
    static void walkTree(ASN1Node node, String indent, byte[] data) {
        System.out.println(indent + node.name() + " offset=" + node.offset() + " len=" + node.length());
        if (node.value() != null && !node.value().isEmpty() && node.value().length() < 50) {
            System.out.println(indent + "  value=[" + node.value() + "]");
        }
        for (ASN1Node child : node.children()) {
            walkTree(child, indent + "  ", data);
        }
    }
}
