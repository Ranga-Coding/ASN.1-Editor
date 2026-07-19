import java.nio.file.*;
import java.util.Base64;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugReenc9 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] originalBytes = service.fromHexString(decodedContent);
        
        ASN1Node originalRoot = ASN1BerDecoder.decode(originalBytes);
        ASN1Document originalDoc = new ASN1Document(originalRoot);
        ASN1Node firstDef = originalDoc.definitions().get(0);
        
        byte[] encoded = ASN1BerEncoder.encode(firstDef);
        ASN1Node encodedRoot = ASN1BerDecoder.decode(encoded);
        ASN1Document encodedDoc = new ASN1Document(encodedRoot);
        
        // The syncHexFromTree path: it reads from TreeView nodes, not from encodedDoc
        // Let me simulate exactly what happens
        ASN1Node defFromDoc = encodedDoc.definitions().get(0);
        System.out.println("defFromDoc name: " + defFromDoc.name());
        System.out.println("defFromDoc children count: " + defFromDoc.children().size());
        
        // Now encode from this def
        byte[] encoded2 = ASN1BerEncoder.encode(defFromDoc);
        System.out.println("encoded2 length: " + encoded2.length);
        
        // Re-decode
        ASN1Node encodedRoot2 = ASN1BerDecoder.decode(encoded2);
        ASN1Document encodedDoc2 = new ASN1Document(encodedRoot2);
        ASN1Node def2 = encodedDoc2.definitions().get(0);
        
        System.out.println("def2 name: " + def2.name());
        System.out.println("def2 children: " + def2.children().size());
        
        // Now let's find the UTF8String
        findUtf8Strings(def2, encoded2, 0);
        
        // Now what highlightBytes does
        ASN1Node utf8Node = findFirstUtf8StringNode(def2);
        if (utf8Node != null) {
            System.out.println("\nFound first UTF8String:");
            System.out.println("  offset=" + utf8Node.offset() + " len=" + utf8Node.length() + " value=" + utf8Node.value());
            
            // highlightBytes(utf8Node.offset(), utf8Node.length())
            // This calls hexEditorArea.setHighlightRange(startByte, endByte)
            // where endByte = startByte + byteLength
            int start = utf8Node.offset();
            int end = start + utf8Node.length();
            System.out.println("  highlightRange [" + start + ", " + end + ")");
            
            byte[] highlighted = Arrays.copyOfRange(encoded2, start, end);
            System.out.println("  highlighted hex: " + HexUtils.toHexString(highlighted));
        }
    }
    
    static void findUtf8Strings(ASN1Node node, byte[] data, int depth) {
        if (node.name().equals("UTF8String") && node.value() != null) {
            System.out.println("  ".repeat(depth) + "UTF8String '" + node.value() + "' offset=" + node.offset() + " len=" + node.length());
        }
        for (ASN1Node child : node.children()) {
            findUtf8Strings(child, data, depth+1);
        }
    }
    
    static ASN1Node findFirstUtf8StringNode(ASN1Node node) {
        if (node.name().equals("UTF8String") && node.value() != null) {
            return node;
        }
        for (ASN1Node child : node.children()) {
            ASN1Node found = findFirstUtf8StringNode(child);
            if (found != null) return found;
        }
        return null;
    }
}
