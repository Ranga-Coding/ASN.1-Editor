import com.asn1editor.parser.*;
import com.asn1editor.model.*;
import com.asn1editor.service.*;

public class DebugReenc20 {
    public static void main(String[] args) throws Exception {
        // SEQUENCE = 30 [length]
        //   INTEGER = 02 01 00 (1 byte value: 0)
        //   UTF8String = 0C [length] SM-Test-PKI-D (13 bytes)
        //   PrintableString = 13 [length] Hel (3 bytes)
        //
        // SEQUENCE content = 3 + 15 + 5 = 23 bytes
        // SEQUENCE total = 2 + 23 = 25 bytes
        
        // UTF8String: 0C 0D 534D2D546573742D504B492D44 (13 bytes text)
        byte[] data = new byte[] {
            (byte)0x30, (byte)0x17,  // SEQUENCE len=23
            (byte)0x02, (byte)0x01, (byte)0x00,  // INTEGER 0 (3 bytes)
            (byte)0x0C, (byte)0x0D, (byte)0x53, (byte)0x4D, (byte)0x2D, (byte)0x54,
            (byte)0x65, (byte)0x73, (byte)0x74, (byte)0x2D, (byte)0x50, (byte)0x4B,
            (byte)0x49, (byte)0x2D, (byte)0x44,  // UTF8String "SM-Test-PKI-D" (15 bytes)
            (byte)0x13, (byte)0x03, (byte)0x48, (byte)0x65, (byte)0x6C,  // PrintableString "Hel" (5 bytes)
        };
        
        System.out.println("Data length: " + data.length);
        System.out.println("Data hex: " + HexUtils.toCompactHex(data));
        
        // Verify: 2 (SEQUENCE header) + 3 (INTEGER) + 15 (UTF8String) + 5 (PrintableString) = 25
        // SEQUENCE content = 3 + 15 + 5 = 23 = 0x17
        // Total = 2 + 23 = 25
        
        ASN1Node root = ASN1BerDecoder.decode(data);
        System.out.println("Root: " + root.name() + " offset=" + root.offset() + " length=" + root.length());
        System.out.println("Root children: " + root.children().size());
        
        for (ASN1Node child : root.children()) {
            System.out.println("  " + child.name() + " offset=" + child.offset() + " length=" + child.length());
            if (child.name().equals("UTF8String")) {
                byte[] bytes = new byte[child.length()];
                System.arraycopy(data, child.offset(), bytes, 0, child.length());
                System.out.println("    hex=" + HexUtils.toCompactHex(bytes));
            }
        }
        
        // Now encode and re-decode
        byte[] encoded = ASN1BerEncoder.encode(root);
        System.out.println("\nEncoded length: " + encoded.length);
        System.out.println("Encoded hex: " + HexUtils.toCompactHex(encoded));
        
        ASN1Node newRoot = ASN1BerDecoder.decode(encoded);
        System.out.println("New root: " + newRoot.name() + " offset=" + newRoot.offset() + " length=" + newRoot.length());
        System.out.println("New root children: " + newRoot.children().size());
        
        for (ASN1Node child : newRoot.children()) {
            System.out.println("  " + child.name() + " offset=" + child.offset() + " length=" + child.length());
            if (child.name().equals("UTF8String")) {
                byte[] bytes = new byte[child.length()];
                System.arraycopy(encoded, child.offset(), bytes, 0, child.length());
                System.out.println("    hex=" + HexUtils.toCompactHex(bytes));
            }
        }
    }
}
