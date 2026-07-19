import java.nio.file.*;
import java.util.Base64;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugReenc7 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] originalBytes = service.fromHexString(decodedContent);
        
        // Simulate the actual loadFile() flow
        ASN1Node originalRoot = ASN1BerDecoder.decode(originalBytes);
        ASN1Document originalDoc = new ASN1Document(originalRoot);
        
        // Step 1: buildTreeView is called with originalDoc
        // Then syncHexFromTree() is called which reads from the TreeView
        // The TreeView has the same nodes as originalDoc (same references)
        ASN1Document docFromTreeView = originalDoc;  // Same nodes!
        
        // Step 2: Encode from TreeView nodes
        byte[] encoded = ASN1BerEncoder.encode(docFromTreeView.definitions().getFirst());
        
        // Step 3: Re-decode
        ASN1Node encodedRoot = ASN1BerDecoder.decode(encoded);
        ASN1Document encodedDoc = new ASN1Document(encodedRoot);
        
        // Now the TreeView is rebuilt with encodedDoc nodes
        // The user clicks on a node with offset=73
        // highlightBytes(73, 16) is called
        // currentHexBytes = encoded (1632 bytes)
        
        // Find the first UTF8String in encodedDoc
        ASN1Node utf8Node = findFirstUtf8String(encodedDoc.definitions().get(0));
        if (utf8Node != null && utf8Node.offset() >= 0) {
            System.out.println("First UTF8String in re-encoded tree:");
            System.out.println("  offset=" + utf8Node.offset());
            System.out.println("  length=" + utf8Node.length());
            System.out.println("  value=" + utf8Node.value());
            
            // What highlightBytes would do
            int startByte = utf8Node.offset();
            int endByte = startByte + utf8Node.length();
            System.out.println("  highlightRange=[" + startByte + ", " + endByte + ")");
            
            // What bytes are at that position
            byte[] highlighted = Arrays.copyOfRange(encoded, startByte, endByte);
            System.out.println("  Highlighted hex: " + HexUtils.toHexString(highlighted));
            
            // Expected
            System.out.println("  Expected:      0C 0E 53 4D 2D 54 65 73 74 2D 50 4B 49 2D 44 45");
            
            // Try to decode what's at that position
            try {
                byte[] test = Arrays.copyOfRange(encoded, startByte, endByte);
                ASN1Node testNode = ASN1BerDecoder.decode(test);
                System.out.println("  Re-decoded at that position: " + testNode.name() + " value=" + testNode.value());
            } catch (Exception e) {
                System.out.println("  Re-decode failed: " + e.getMessage());
            }
        } else {
            System.out.println("No UTF8String found!");
            // Print the encoded tree
            printTree(encodedDoc.definitions().get(0), encoded, "");
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
    
    static void printTree(ASN1Node node, byte[] data, String indent) {
        if (node.name().equals("UTF8String") && node.value() != null) {
            String hex = "";
            if (node.offset() >= 0 && node.length() > 0) {
                hex = HexUtils.toCompactHex(Arrays.copyOfRange(data, node.offset(), node.offset() + Math.min(node.length(), data.length - node.offset())));
            }
            System.out.println(indent + "UTF8String '" + node.value() + "' offset=" + node.offset() + " len=" + node.length() + " hex=" + hex);
        }
        for (ASN1Node child : node.children()) {
            printTree(child, data, indent + "  ");
        }
    }
}
