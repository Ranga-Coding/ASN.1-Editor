package com.asn1editor.parser;

import com.asn1editor.model.ASN1Node;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ASN1BerEncoderTest {

    @Test
    void encode_integer_roundtrip() throws Exception {
        byte[] original = new byte[]{0x02, 0x01, 0x2A}; // INTEGER 42
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }

    @Test
    void encode_boolean_roundtrip() throws Exception {
        byte[] original = new byte[]{0x01, 0x01, 0x01}; // TRUE
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }

    @Test
    void encode_null_roundtrip() throws Exception {
        byte[] original = new byte[]{0x05, 0x00}; // NULL
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }

    @Test
    void encode_oid_roundtrip() throws Exception {
        byte[] original = new byte[]{
                0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7,
                0x0D, 0x01, 0x01, 0x0B}; // sha256WithRSAEncryption
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }

    @Test
    void encode_printableString_roundtrip() throws Exception {
        byte[] original = new byte[]{0x13, 0x04, 'T', 'e', 's', 't'};
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }

    @Test
    void encode_utf8String_roundtrip() throws Exception {
        byte[] original = new byte[]{0x0C, 0x05, 'H', 'a', 'l', 'l', 'o'};
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }

    @Test
    void encode_sequence_roundtrip() throws Exception {
        byte[] original = new byte[]{
                0x30, 0x09, 0x02, 0x01, 0x2A, 0x13, 0x04, 'T', 'e', 's', 't'};
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }

    @Test
    void encode_nested_sequence_roundtrip() throws Exception {
        byte[] original = new byte[]{
                0x30, 0x08, 0x30, 0x03, 0x02, 0x01, 0x01, 0x02, 0x01, 0x02};
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }

    @Test
    void encode_set_roundtrip() throws Exception {
        byte[] original = new byte[]{0x31, 0x03, 0x02, 0x01, 0x2A};
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }

    @Test
    void encode_octetString_roundtrip() throws Exception {
        byte[] original = new byte[]{0x04, 0x03, 0x01, 0x02, 0x03};
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }

    @Test
    void encode_bitString_roundtrip() throws Exception {
        byte[] original = new byte[]{0x03, 0x03, 0x00, (byte) 0xAB, (byte) 0xCD};
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }

    @Test
    void encode_longLength_roundtrip() throws Exception {
        // SEQUENCE { OCTET STRING (200 bytes) } — long form length (DER-konform)
        // OCTET STRING: 0x04, 0x81, 0xC8 (length 200 = 1 byte folgt)
        byte[] value = new byte[200];
        for (int i = 0; i < value.length; i++) value[i] = (byte) (i & 0xFF);
        byte[] octetString = new byte[3 + value.length]; // tag + 1-byte length + value
        octetString[0] = 0x04; octetString[1] = (byte) 0x81; octetString[2] = (byte) 0xC8;
        System.arraycopy(value, 0, octetString, 3, value.length);
        // SEQUENCE: 0x30, 0x81, 0xCB (length 203 = 1 byte folgt)
        byte[] original = new byte[3 + octetString.length];
        original[0] = 0x30; original[1] = (byte) 0x81; original[2] = (byte) 0xCB;
        System.arraycopy(octetString, 0, original, 3, octetString.length);
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }

    @Test
    void encode_applicationTag_roundtrip() throws Exception {
        // APPLICATION[5] primitive = 0x45
        byte[] original = new byte[]{0x45, 0x05, 'h', 'e', 'l', 'l', 'o'};
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }

    @Test
    void encode_contextTag_roundtrip() throws Exception {
        // CONTEXT[0] CONSTRUCTED = 0xA0, length 5
        byte[] original = new byte[]{(byte) 0xA0, 0x05, 0x30, 0x03, 0x02, 0x01, 0x01};
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }

    @Test
    void encode_nullNode_throws() {
        assertThrows(ASN1BerEncoder.ASN1EncodeException.class, () -> ASN1BerEncoder.encode(null));
    }

    @Test
    void encode_rfc5280LikeStructure_roundtrip() throws Exception {
        // Inner SEQUENCE: OID(11) = 11 (0x0B). Outer SEQUENCE: 3+13+6 = 22 (0x16)
        byte[] original = new byte[]{
                0x30, 0x16, 0x02, 0x01, 0x01, 0x30, 0x0B,
                0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7,
                0x0D, 0x01, 0x01, 0x0B, 0x13, 0x04, 'T', 'e', 's', 't'};
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }

    @Test
    void encode_emptySequence_roundtrip() throws Exception {
        byte[] original = new byte[]{0x30, 0x00};
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }

    @Test
    void encode_emptyOid_roundtrip() throws Exception {
        byte[] original = new byte[]{0x06, 0x00};
        ASN1Node decoded = ASN1BerDecoder.decode(original);
        byte[] encoded = ASN1BerEncoder.encode(decoded);
        assertArrayEquals(original, encoded);
    }
}
