import java.nio.file.*;
import java.util.Base64;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugReenc10 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] originalBytes = service.fromHexString(decodedContent);
        
        ASN1Node originalRoot = ASN1BerDecoder.decode(originalBytes);
        ASN1Document originalDoc = new ASN1Document(originalRoot);
        ASN1Node firstDef = originalDoc.definitions().get(0);
        
        byte[] encoded = ASN1BerEncoder.encode(firstDef);
        System.out.println("encoded first 20 bytes: " + HexUtils.toCompactHex(Arrays.copyOf(encoded, Math.min(20, encoded.length))));
        
        ASN1Node root = ASN1BerDecoder.decode(encoded);
        System.out.println("root: " + root.name());
        System.out.println("root children: " + root.children().size());
        for (ASN1Node child : root.children()) {
            System.out.println("  child: " + child.name() + " offset=" + child.offset());
            System.out.println("  child children: " + child.children().size());
        }
        
        // Now check the firstDef from the ORIGINAL doc
        System.out.println("\n=== Original firstDef ===");
        System.out.println("originalRoot: " + originalRoot.name());
        System.out.println("originalRoot children: " + originalRoot.children().size());
        for (ASN1Node child : originalRoot.children()) {
            System.out.println("  child: " + child.name() + " offset=" + child.offset() + " len=" + child.length());
            System.out.println("  child children: " + child.children().size());
        }
    }
}
