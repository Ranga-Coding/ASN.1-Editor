package com.asn1editor.parser;

/**
 * Repräsentiert ein lexemisches Element (Token).
 * Speichert den Typ, den Wert sowie die exakte Position im Quelltext für präzise Fehlerberichte.
 */
public record Token(TokenType type, String value, int line, int column) {

    public enum TokenType {
        IDENTIFIER,
        STRING_LITERAL,
        DOUBLE_EQUALS, // ::=
        EQUALS,        // := (legacy)
        LBRACE,        // {
        RBRACE,        // }
        COLON,         // :
        COMMA,         // ,
        PIPE,          // |
        SEMICOLON,     // ;
        EOF,           // Ende der Datei
        UNKNOWN
    }
}
