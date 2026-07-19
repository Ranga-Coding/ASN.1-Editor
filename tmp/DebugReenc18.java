import java.nio.file.*;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugReenc18 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] originalBytes = service.fromHexString(decodedContent);
        
        ASN1Node originalRoot = ASN1BerDecoder.decode(originalBytes);
        ASN1Document originalDoc = new ASN1Document(originalRoot);
        
        // Original UTF8String offset
        ASN1Node origUtf8 = findAnyUtf8String(originalDoc.definitions().get(0));
        System.out.println("Original UTF8String:");
        System.out.println("  offset=" + origUtf8.offset() + " length=" + origUtf8.length());
        
        // Re-encoded UTF8String offset
        byte[] encoded = ASN1BerEncoder.encode(originalDoc.definitions().get(0));
        ASN1Node newRoot = ASN1BerDecoder.decode(encoded);
        ASN1Document newDoc = new ASN1Document(newRoot);
        ASN1Node newUtf8 = findAnyUtf8String(newDoc.definitions().get(0));
        System.out.println("New UTF8String:");
        System.out.println("  offset=" + newUtf8.offset() + " length=" + newUtf8.length());
        
        // Was passiert wenn wir den alten Offset (69) auf die neuen Bytes anwenden?
        System.out.println("\n--- Was passiert mit dem alten Offset? ---");
        System.out.println("currentHexBytes (encoded) length=" + encoded.length);
        int oldOffset = origUtf8.offset();
        int oldLength = origUtf8.length();
        byte[] wrongHighlight = Arrays.copyOfRange(encoded, oldOffset, oldOffset + oldLength);
        System.out.println("highlightBytes(" + oldOffset + ", " + oldLength + ") → hex=" + HexUtils.toHexString(wrongHighlight));
        System.out.println("expected=0C 0E 53 4D 2D 54 65 73 74 2D 50 4B 49 2D 44 45");
        
        // Und mit dem neuen Offset?
        byte[] correctHighlight = Arrays.copyOfRange(encoded, newUtf8.offset(), newUtf8.offset() + newUtf8.length());
        System.out.println("\nhighlightBytes(" + newUtf8.offset() + ", " + newUtf8.length() + ") → hex=" + HexUtils.toHexString(correctHighlight));
        
        // Was ist an Position 69 im originalen Byte-Strom?
        byte[] origHighlight = Arrays.copyOfRange(originalBytes, origUtf8.offset(), origUtf8.offset() + origUtf8.length());
        System.out.println("\nOriginal highlightBytes(" + origUtf8.offset() + ", " + origUtf8.length() + ") → hex=" + HexUtils.toHexString(origHighlight));
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
