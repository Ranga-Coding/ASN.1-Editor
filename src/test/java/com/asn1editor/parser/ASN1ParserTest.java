package com.asn1editor.parser;

import com.asn1editor.model.ASN1Document;
import com.asn1editor.model.ASN1Node;
import com.asn1editor.service.ASN1IOException;
import com.asn1editor.service.ASN1Service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ASN1ParserTest {

    private final ASN1Service service = new ASN1Service();

    // ─── Lexer-Tests ────────────────────────────────────────────────

    @Test
    void testLexerSimpleIdentifier() throws ASN1ParseException {
        List<Token> tokens = new ASN1Lexer("Foo").tokenize();
        assertEquals(Token.TokenType.IDENTIFIER, tokens.get(0).type());
        assertEquals("Foo", tokens.get(0).value());
        assertEquals(Token.TokenType.EOF, tokens.get(1).type());
    }

    @Test
    void testLexerStringLiteral() throws ASN1ParseException {
        List<Token> tokens = new ASN1Lexer("\"test\"").tokenize();
        assertEquals(Token.TokenType.STRING_LITERAL, tokens.get(0).type());
        assertEquals("test", tokens.get(0).value());
    }

    // ─── Parser-Tests ───────────────────────────────────────────────

    @Test
    void testParseSequenceType() throws ASN1IOException {
        String source = """
                MyType ::= SEQUENCE {
                    field1 INTEGER
                    field2 "default"
                }
                """;
        ASN1Document doc = service.parse(source);
        assertNotNull(doc);
        ASN1Node root = doc.root();
        assertEquals("type:MyType", root.name());
        assertFalse(root.isLeaf());
        assertEquals(2, root.children().size());
    }

    @Test
    void testParseChoiceType() throws ASN1IOException {
        String source = """
                MyChoice ::= CHOICE {
                    first INTEGER | second "value"
                }
                """;
        ASN1Document doc = service.parse(source);
        assertNotNull(doc);
        ASN1Node root = doc.root();
        assertEquals("type:MyChoice", root.name());
        assertFalse(root.isLeaf());
        assertEquals(2, root.children().size());
    }

    @Test
    void testParseValueBlock() throws ASN1IOException {
        // Simpler Test ohne verschachtelte Blöcke
        String source = """
                MyValue ::= {
                    field1 "hello"
                    field2 "world"
                }
                """;
        ASN1Document doc = service.parse(source);
        assertNotNull(doc);
        ASN1Node root = doc.root();
        assertEquals("value:MyValue", root.name());
        assertEquals(2, root.children().size());
    }

    @Test
    void testParseValueBlockNested() throws ASN1IOException, ASN1ParseException {
        // Direkter Lexer-Test zur Isolierung
        String source = "MyValue ::= {\n    field1 \"hello\"\n    field2 { nested \"data\" }\n}";
        ASN1Lexer lexer = new ASN1Lexer(source);
        lexer.tokenize();

        // Jetzt parsen
        ASN1Document doc = service.parse(source);
        assertNotNull(doc);
        ASN1Node root = doc.root();
        assertEquals("value:MyValue", root.name());
        assertEquals(2, root.children().size());
        ASN1Node field2 = root.children().get(1);
        assertEquals("field:field2", field2.name());
        ASN1Node block = field2.children().get(0);
        assertEquals("block", block.name());
        assertEquals(1, block.children().size());
    }

    @Test
    void testParseTypeRef() throws ASN1IOException {
        String source = """
                MyRef ::= OtherType
                """;
        ASN1Document doc = service.parse(source);
        assertNotNull(doc);
        ASN1Node root = doc.root();
        assertEquals("type:MyRef", root.name());
        // Should have kind=TYPE_REF and typeRef=OtherType
        assertEquals(2, root.children().size());
    }

    @Test
    void testParseMultipleDefinitions() throws ASN1IOException {
        String source = """
                FirstDef ::= INTEGER
                SecondDef ::= "literal"
                """;
        ASN1Document doc = service.parse(source);
        assertNotNull(doc);
        // Multiple defs → container with "ASN.1 Document" name
        ASN1Node root = doc.root();
        assertFalse(root.isLeaf());
        assertEquals(2, doc.definitions().size());
    }

    @Test
    void testParseEmptyThrows() {
        assertThrows(ASN1IOException.class, () -> {
            service.parse("");
        });
    }

    @Test
    void testParseInvalidSyntaxThrows() {
        assertThrows(ASN1IOException.class, () -> {
            service.parse("This has no := assignment");
        });
    }

    @Test
    void testValidateValid() {
        assertTrue(service.validate("Foo ::= INTEGER"));
    }

    @Test
    void testValidateInvalid() {
        assertFalse(service.validate("no equals sign here"));
    }
}
