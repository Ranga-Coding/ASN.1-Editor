import java.nio.file.*;
import java.util.Base64;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugLoad {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String fileFormat = service.detectFormat(raw).name();
        System.out.println("Detected format: " + fileFormat);
        
        String decodedContent = service.decodeBase64IfNeeded(raw);
        boolean isHex = service.isHexString(decodedContent);
        System.out.println("isHexString after decode: " + isHex);
        
        if (isHex) {
            byte[] decodedBytes = service.fromHexString(decodedContent);
            System.out.println("Decoded hex bytes length: " + decodedBytes.length);
            
            ASN1Node root = ASN1BerDecoder.decode(decodedBytes);
            ASN1Node utf8Node = findUtf8String(root, "SM-Test-PKI-DE");
            if (utf8Node != null && utf8Node.offset() >= 0) {
                System.out.println("UTF8String offset: " + utf8Node.offset());
                System.out.println("UTF8String length: " + utf8Node.length());
                
                byte[] expected = Arrays.copyOfRange(decodedBytes, utf8Node.offset(), utf8Node.offset() + utf8Node.length());
                System.out.println("Expected hex: " + HexUtils.toHexString(expected));
                System.out.println("Expected compact: " + HexUtils.toCompactHex(expected));
            }
        } else {
            System.out.println("Content is text, not hex. Decoded length: " + decodedContent.length());
            System.out.println("First 100 chars: " + decodedContent.substring(0, Math.min(100, decodedContent.length())));
            
            // This means it goes the text path
            byte[] currentHexBytes = decodedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("currentHexBytes length: " + currentHexBytes.length);
            
            ASN1Document doc = service.parse(decodedContent, Paths.get("TEST.crmf"));
            ASN1Node root = doc.definitions().get(0);
            ASN1Node utf8Node = findUtf8String(root, "SM-Test-PKI-DE");
            if (utf8Node != null && utf8Node.offset() >= 0) {
                System.out.println("UTF8String offset: " + utf8Node.offset());
                System.out.println("UTF8String length: " + utf8Node.length());
                
                byte[] nodeBytes = Arrays.copyOfRange(currentHexBytes, utf8Node.offset(), utf8Node.offset() + utf8Node.length());
                System.out.println("From currentHexBytes: " + HexUtils.toHexString(nodeBytes));
            } else {
                System.out.println("UTF8String not found in tree!");
                dumpTree(root, currentHexBytes, 0);
            }
        }
    }
    
    static ASN1Node findUtf8String(ASN1Node node, String expectedValue) {
        if (node.name().equals("UTF8String") && node.value() != null && node.value().equals(expectedValue)) {
            return node;
        }
        for (ASN1Node child : node.children()) {
            ASN1Node found = findUtf8String(child, expectedValue);
            if (found != null) return found;
        }
        return null;
    }
    
    static void dumpTree(ASN1Node node, byte[] data, int depth) {
        String indent = "  ".repeat(depth);
        if (node.offset() >= 0 && node.length() > 0) {
            String hex = HexUtils.toCompactHex(Arrays.copyOfRange(data, Math.max(0,node.offset()), Math.min(data.length, node.offset()+node.length())));
            System.out.println(indent + node.name() + " offset=" + node.offset() + " len=" + node.length() + " hex=" + hex.substring(0, Math.min(40, hex.length())));
            if (node.value() != null && !node.value().isEmpty() && node.value().length() < 50) {
                System.out.println(indent + "  value=" + node.value());
            }
        } else {
            System.out.println(indent + node.name() + " offset=" + node.offset() + " len=" + node.length());
        }
        for (ASN1Node child : node.children()) {
            dumpTree(child, data, depth+1);
        }
    }
}
