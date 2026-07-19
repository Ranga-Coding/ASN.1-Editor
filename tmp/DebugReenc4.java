import java.nio.file.*;
import java.util.Base64;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugReenc4 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] originalBytes = service.fromHexString(decodedContent);
        
        ASN1Node originalRoot = ASN1BerDecoder.decode(originalBytes);
        ASN1Document originalDoc = new ASN1Document(originalRoot);
        ASN1Node firstDef = originalDoc.definitions().get(0);
        
        // List all leaf values
        listLeafValues(firstDef, 0);
    }
    
    static void listLeafValues(ASN1Node node, int depth) {
        String indent = "  ".repeat(depth);
        if (node.name().equals("UTF8String") && node.value() != null) {
            System.out.println(indent + "UTF8String value=[" + node.value() + "] len=" + node.value().length() +
                " offset=" + node.offset() + " len=" + node.length());
        }
        for (ASN1Node child : node.children()) {
            listLeafValues(child, depth+1);
        }
    }
}
