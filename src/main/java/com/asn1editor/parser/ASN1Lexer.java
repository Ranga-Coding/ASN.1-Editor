package com.asn1editor.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Der Lexer zerlegt den Eingabetext in eine Liste von Token.
 * Er hält die Position (Zeile/Spalte) fest, um präzise Fehlermeldungen zu ermöglichen.
 */
public class ASN1Lexer {

    private final String input;
    private int pos = 0;
    private int line = 1;
    private int column = 1;

    public ASN1Lexer(String input) {
        this.input = input;
    }

    /**
     * Erstellt eine Liste von Token aus dem Input.
     */
    public List<Token> tokenize() throws ASN1ParseException {
        List<Token> tokens = new ArrayList<>();
        while (pos < input.length()) {
            char c = input.charAt(pos);

            if (Character.isWhitespace(c)) {
                consume(c);
                continue;
            }

            if (c == ':') {
                // Check for ::= (ASN.1 definition operator)
                if (peek() == ':') {
                    if (peek(2) == '=') {
                        tokens.add(new Token(Token.TokenType.DOUBLE_EQUALS, "::=", line, column));
                        consume(':');
                        consume(':');
                        consume('=');
                    } else {
                        // "::" without "=" → just first colon
                        tokens.add(new Token(Token.TokenType.COLON, ":", line, column));
                        consume(':');
                    }
                } else if (peek() == '=') {
                    // Legacy := operator
                    tokens.add(new Token(Token.TokenType.EQUALS, ":=", line, column));
                    consume(':');
                    consume('=');
                } else {
                    tokens.add(new Token(Token.TokenType.COLON, ":", line, column));
                    consume(':');
                }
                continue;
            }

            if (c == '{') {
                tokens.add(new Token(Token.TokenType.LBRACE, "{", line, column));
                consume('{');
                continue;
            }

            if (c == '}') {
                tokens.add(new Token(Token.TokenType.RBRACE, "}", line, column));
                consume('}');
                continue;
            }

            if (c == ';') {
                tokens.add(new Token(Token.TokenType.SEMICOLON, ";", line, column));
                consume(';');
                continue;
            }

            if (c == '|') {
                tokens.add(new Token(Token.TokenType.PIPE, "|", line, column));
                consume('|');
                continue;
            }

            if (c == ',') {
                tokens.add(new Token(Token.TokenType.COMMA, ",", line, column));
                consume(',');
                continue;
            }

            if (c == '"') {
                tokens.add(readStringLiteral());
                continue;
            }

            if (Character.isLetterOrDigit(c) || c == '_') {
                tokens.add(readIdentifier());
                continue;
            }

            // Falls kein bekanntes Zeichen gefunden wurde
            throw new ASN1ParseException("Unerwartetes Zeichen: '" + c + "'", line, column);
        }

        tokens.add(new Token(Token.TokenType.EOF, "", line, column));
        return tokens;
    }

    private Token readIdentifier() {
        StringBuilder sb = new StringBuilder();
        int startCol = column;
        while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
            sb.append(input.charAt(pos));
            consume(input.charAt(pos));
        }
        return new Token(Token.TokenType.IDENTIFIER, sb.toString(), line, startCol);
    }

    private Token readStringLiteral() {
        StringBuilder sb = new StringBuilder();
        int startCol = column;
        consume('"'); // consume opening quote
        while (pos < input.length() && input.charAt(pos) != '"') {
            sb.append(input.charAt(pos));
            consume(input.charAt(pos));
        }
        if (pos < input.length()) {
            consume('"'); // consume closing quote
        }
        return new Token(Token.TokenType.STRING_LITERAL, sb.toString(), line, startCol);
    }

    private char peek() {
        return pos + 1 < input.length() ? input.charAt(pos + 1) : '\0';
    }

    private char peek(int offset) {
        int idx = pos + offset;
        return idx < input.length() ? input.charAt(idx) : '\0';
    }

    /**
     * Konsumiert das angegebene Zeichen und aktualisiert Position/Zeile/Spalte.
     */
    private void consume(char c) {
        pos++;
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
    }
}
