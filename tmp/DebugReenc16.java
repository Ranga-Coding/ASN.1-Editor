import java.nio.file.*;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugReenc16 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] originalBytes = service.fromHexString(decodedContent);
        
        ASN1Node originalRoot = ASN1BerDecoder.decode(originalBytes);
        ASN1Document originalDoc = new ASN1Document(originalRoot);
        
        // Original UTF8String mit children
        ASN1Node origUtf8 = findAnyUtf8String(originalDoc.definitions().get(0));
        if (origUtf8 != null) {
            System.out.println("Original UTF8String:");
            System.out.println("  value=" + origUtf8.value());
            System.out.println("  children count=" + origUtf8.children().size());
            for (ASN1Node child : origUtf8.children()) {
                System.out.println("    child: " + child.name() + " = " + child.value());
            }
        }
        
        // Re-encoded UTF8String mit children
        byte[] encoded = ASN1BerEncoder.encode(originalDoc.definitions().get(0));
        ASN1Node newRoot = ASN1BerDecoder.decode(encoded);
        ASN1Document newDoc = new ASN1Document(newRoot);
        
        ASN1Node newUtf8 = findAnyUtf8String(newDoc.definitions().get(0));
        if (newUtf8 != null) {
            System.out.println("\nNew UTF8String:");
            System.out.println("  value=" + newUtf8.value());
            System.out.println("  children count=" + newUtf8.children().size());
            for (ASN1Node child : newUtf8.children()) {
                System.out.println("    child: " + child.name() + " = " + child.value());
            }
        }
        
        // Try re-decoding just the UTF8String bytes from the re-encoded data
        System.out.println("\n--- Direct re-decode of new UTF8String bytes ---");
        int utf8Offset = newUtf8.offset();
        int utf8Length = newUtf8.length();
        System.out.println("utf8 bytes at offset " + utf8Offset + ", length " + utf8Length);
        System.out.println("hex: " + HexUtils.toCompactHex(Arrays.copyOfRange(encoded, utf8Offset, utf8Offset + utf8Length)));
        
        byte[] utf8Bytes = Arrays.copyOfRange(encoded, utf8Offset, utf8Offset + utf8Length);
        ASN1Node redecoded = ASN1BerDecoder.decode(utf8Bytes);
        System.out.println("re-decoded name: " + redecoded.name());
        System.out.println("re-decoded value: " + redecoded.value());
        System.out.println("re-decoded children count: " + redecoded.children().size());
        for (ASN1Node child : redecoded.children()) {
            System.out.println("  child: " + child.name() + " = " + child.value());
        }
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
