import java.nio.file.*;
import java.util.Arrays;
import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugReenc15 {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get("TEST.crmf")), java.nio.charset.StandardCharsets.ISO_8859_1);
        
        ASN1Service service = new ASN1Service();
        String decodedContent = service.decodeBase64IfNeeded(raw);
        byte[] originalBytes = service.fromHexString(decodedContent);
        
        ASN1Node originalRoot = ASN1BerDecoder.decode(originalBytes);
        ASN1Document originalDoc = new ASN1Document(originalRoot);
        
        // Check if original has UTF8String with value
        ASN1Node origUtf8 = findAnyUtf8String(originalDoc.definitions().get(0));
        if (origUtf8 != null) {
            System.out.println("Original UTF8String:");
            System.out.println("  value=" + origUtf8.value());
            System.out.println("  offset=" + origUtf8.offset());
            System.out.println("  length=" + origUtf8.length());
        }
        
        // Check all children of original
        ASN1Node origDef = originalDoc.definitions().get(0);
        ASN1Node origCtx0 = findFirstContext0(origDef);
        if (origCtx0 != null) {
            System.out.println("\nOriginal CONTEXT[0]:");
            System.out.println("  offset=" + origCtx0.offset());
            System.out.println("  children=" + origCtx0.children().size());
            if (!origCtx0.children().isEmpty()) {
                ASN1Node innerSeq = origCtx0.children().get(0);
                System.out.println("  inner seq children=" + innerSeq.children().size());
                if (!innerSeq.children().isEmpty()) {
                    ASN1Node innerSeq2 = innerSeq.children().get(0);
                    System.out.println("  inner seq2 children=" + innerSeq2.children().size());
                    if (!innerSeq2.children().isEmpty()) {
                        ASN1Node innerSeq3 = innerSeq2.children().get(0);
                        System.out.println("  inner seq3 children=" + innerSeq3.children().size());
                        for (ASN1Node child : innerSeq3.children()) {
                            System.out.println("    child: " + child.name() + " offset=" + child.offset() + " len=" + child.length());
                            if (child.name().contains("SEQUENCE") || child.name().contains("SET")) {
                                for (ASN1Node gc : child.children()) {
                                    System.out.println("      gc: " + gc.name() + " offset=" + gc.offset() + " len=" + gc.length());
                                    if (gc.name().contains("SEQUENCE") || gc.name().contains("SET")) {
                                        for (ASN1Node ggc : gc.children()) {
                                            System.out.println("        ggc: " + ggc.name() + " value=" + ggc.value() + " offset=" + ggc.offset() + " len=" + ggc.length());
                                            if (ggc.name().equals("UTF8String")) {
                                                System.out.println("        ggc CHILDREN:");
                                                for (ASN1Node gggc : ggc.children()) {
                                                    System.out.println("          " + gggc.name() + " = " + gggc.value());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
    
    static ASN1Node findFirstContext0(ASN1Node node) {
        if (node.name().startsWith("CONTEXT[0]")) return node;
        for (ASN1Node child : node.children()) {
            ASN1Node found = findFirstContext0(child);
            if (found != null) return found;
        }
        return null;
    }
}
