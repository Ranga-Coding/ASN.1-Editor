package com.asn1editor.parser;

import com.asn1editor.model.ASN1Node;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ASN1BerDecoderTest {

    @Test
    void decode_constructedIndefiniteLengthSequence_untilEoc() throws Exception {
        byte[] ber = new byte[] {
                0x30, (byte) 0x80,
                0x06, 0x03, 0x2A, 0x03, 0x04,
                0x05, 0x00,
                0x00, 0x00
        };

        ASN1Node root = ASN1BerDecoder.decode(ber);

        assertEquals("SEQUENCE [CONSTRUCTED]", root.name());
        assertEquals(2, root.children().size());
        assertEquals("OBJECT IDENTIFIER", root.children().get(0).name());
        assertEquals("NULL", root.children().get(1).name());
    }

    @Test
    void decode_nestedConstructedIndefiniteLengthSequence_untilNestedEoc() throws Exception {
        byte[] ber = new byte[] {
                0x30, (byte) 0x80,
                (byte) 0xA0, (byte) 0x80,
                0x02, 0x01, 0x05,
                0x00, 0x00,
                0x00, 0x00
        };

        ASN1Node root = ASN1BerDecoder.decode(ber);

        assertEquals("SEQUENCE [CONSTRUCTED]", root.name());
        assertEquals(1, root.children().size());
        ASN1Node context = root.children().getFirst();
        assertEquals("CONTEXT[0] [CONSTRUCTED]", context.name());
        assertEquals(1, context.children().size());
        assertEquals("INTEGER", context.children().getFirst().name());
    }

    @Test
    void decode_rfc5280KnownOid_displaysFriendlyName() throws Exception {
        byte[] oidSha256WithRsa = new byte[] {
                0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7,
                0x0D, 0x01, 0x01, 0x0B
        };

        ASN1Node root = ASN1BerDecoder.decode(oidSha256WithRsa);

        assertEquals("OBJECT IDENTIFIER", root.name());
        assertTrue(root.children().stream()
                .anyMatch(child -> "value".equals(child.name())
                        && "1.2.840.113549.1.1.11 (sha256WithRSAEncryption)".equals(child.value())));
        assertTrue(root.children().stream()
                .anyMatch(child -> "oid".equals(child.name())
                        && "1.2.840.113549.1.1.11".equals(child.value())));
    }

    @Test
    void decode_primitiveIndefiniteLength_throws() {
        byte[] ber = new byte[] { 0x04, (byte) 0x80, 0x00, 0x00 };

        BERDecodeException ex = assertThrows(BERDecodeException.class, () -> ASN1BerDecoder.decode(ber));

        assertTrue(ex.getMessage().contains("nur für konstruktive"));
        assertEquals(2, ex.getByteOffset());
    }
}
