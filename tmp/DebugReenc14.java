import java.nio.file.*;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugReenc14 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] originalBytes = service.fromHexString(decodedContent);
        
        ASN1Node originalRoot = ASN1BerDecoder.decode(originalBytes);
        ASN1Document originalDoc = new ASN1Document(originalRoot);
        
        // Original UTF8String
        ASN1Node origUtf8 = findFirstUtf8String(originalDoc.definitions().get(0));
        if (origUtf8 != null) {
            System.out.println("Original UTF8String:");
            System.out.println("  value=" + origUtf8.value());
            System.out.println("  offset=" + origUtf8.offset());
            System.out.println("  length=" + origUtf8.length());
            for (ASN1Node child : origUtf8.children()) {
                System.out.println("  child: " + child.name() + " value=" + child.value());
            }
        }
        
        // Re-encoded UTF8String
        byte[] encoded = ASN1BerEncoder.encode(originalDoc.definitions().get(0));
        ASN1Node newRoot = ASN1BerDecoder.decode(encoded);
        ASN1Document newDoc = new ASN1Document(newRoot);
        
        ASN1Node newUtf8 = findFirstUtf8String(newDoc.definitions().get(0));
        if (newUtf8 != null) {
            System.out.println("\nNew UTF8String:");
            System.out.println("  value=" + newUtf8.value());
            System.out.println("  offset=" + newUtf8.offset());
            System.out.println("  length=" + newUtf8.length());
            for (ASN1Node child : newUtf8.children()) {
                System.out.println("  child: " + child.name() + " value=" + child.value());
            }
        } else {
            System.out.println("\nNew UTF8String: NOT FOUND (value may be null)");
            // Try to find any UTF8String even without value
            ASN1Node anyUtf8 = findAnyUtf8String(newDoc.definitions().get(0));
            if (anyUtf8 != null) {
                System.out.println("Found ANY UTF8String:");
                System.out.println("  value=" + anyUtf8.value());
                System.out.println("  offset=" + anyUtf8.offset());
                System.out.println("  length=" + anyUtf8.length());
            }
        }
    }
    
    static ASN1Node findFirstUtf8String(ASN1Node node) {
        if (node.name().equals("UTF8String") && node.value() != null) {
            return node;
        }
        for (ASN1Node child : node.children()) {
            ASN1Node found = findFirstUtf8String(child);
            if (found != null) return found;
        }
        return null;
    }
    
    static ASN1Node findAnyUtf8String(ASN1Node node) {
        if (node.name().equals("UTF8String")) {
            return node;
        }
        for (ASN1Node child : node.children()) {
            ASN1Node found = findAnyUtf8String(child);
            if (found != null) return found;
        }
        return null;
    }
}
