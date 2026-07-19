package com.asn1editor.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ASN1LexerTest {

    @Test
    void testTokenizeIdentifier() throws ASN1ParseException {
        List<Token> tokens = new ASN1Lexer("MyType").tokenize();
        assertEquals(2, tokens.size()); // IDENTIFIER + EOF
        assertEquals(Token.TokenType.IDENTIFIER, tokens.get(0).type());
        assertEquals("MyType", tokens.get(0).value());
        assertEquals(Token.TokenType.EOF, tokens.get(1).type());
    }

    @Test
    void testTokenizeStringLiteral() throws ASN1ParseException {
        List<Token> tokens = new ASN1Lexer("\"Hello World\"").tokenize();
        assertEquals(2, tokens.size());
        assertEquals(Token.TokenType.STRING_LITERAL, tokens.get(0).type());
        assertEquals("Hello World", tokens.get(0).value());
    }

    @Test
    void testTokenizeEquals() throws ASN1ParseException {
        List<Token> tokens = new ASN1Lexer("MyType := SEQUENCE {").tokenize();
        List<Token.TokenType> types = List.of(
                Token.TokenType.IDENTIFIER,
                Token.TokenType.EQUALS,
                Token.TokenType.IDENTIFIER,
                Token.TokenType.LBRACE
        );
        for (int i = 0; i < types.size(); i++) {
            assertEquals(types.get(i), tokens.get(i).type());
        }
    }

    @Test
    void testTokenizePipeAndComma() throws ASN1ParseException {
        List<Token> tokens = new ASN1Lexer("a | b , c").tokenize();
        assertEquals(6, tokens.size()); // id, pipe, id, comma, id, eof
        assertEquals(Token.TokenType.PIPE, tokens.get(1).type());
        assertEquals(Token.TokenType.COMMA, tokens.get(3).type());
    }

    @Test
    void testTokenizeNewlines() throws ASN1ParseException {
        List<Token> tokens = new ASN1Lexer("a\nb").tokenize();
        assertEquals(3, tokens.size());
        assertEquals(1, tokens.get(0).line());
        assertEquals(2, tokens.get(1).line());
    }

    @Test
    void testTokenizeUnknownCharThrows() {
        assertThrows(ASN1ParseException.class, () -> {
            new ASN1Lexer("@#$").tokenize();
        });
    }

    @Test
    void testTokenizeEmptyInput() throws ASN1ParseException {
        List<Token> tokens = new ASN1Lexer("").tokenize();
        assertEquals(1, tokens.size());
        assertEquals(Token.TokenType.EOF, tokens.get(0).type());
    }
}
