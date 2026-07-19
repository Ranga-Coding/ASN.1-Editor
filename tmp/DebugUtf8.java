import java.nio.file.*;
import java.util.Base64;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugUtf8 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        String b64 = raw.replaceAll(".*BEGIN.*\\n", "").replaceAll("\\n.*END.*", "").trim();
        byte[] decoded = Base64.getDecoder().decode(b64);
        System.out.println("Decoded length: " + decoded.length);
        
        ASN1Node root = ASN1BerDecoder.decode(decoded);
        ASN1Node utf8Node = findUtf8String(root, "SM-Test-PKI-DE");
        if (utf8Node != null) {
            System.out.println("\n=== UTF8String Node ===");
            System.out.println("offset: " + utf8Node.offset());
            System.out.println("length: " + utf8Node.length());
            System.out.println("name: " + utf8Node.name());
            System.out.println("value: " + utf8Node.value());
            if (utf8Node.offset() >= 0 && utf8Node.length() > 0) {
                System.out.println("actual hex: " + HexUtils.toCompactHex(Arrays.copyOfRange(decoded, utf8Node.offset(), Math.min(decoded.length, utf8Node.offset()+utf8Node.length()))));
            }
        } else {
            System.out.println("UTF8String 'SM-Test-PKI-DE' not found, dumping tree...");
            dumpTree(root, decoded, 0);
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
            System.out.println(indent + node.name() + " offset=" + node.offset() + " len=" + node.length() + " hex=" + hex.substring(0, Math.min(60, hex.length())));
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
