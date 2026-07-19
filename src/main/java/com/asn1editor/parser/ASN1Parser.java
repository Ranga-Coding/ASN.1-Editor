package com.asn1editor.parser;

import com.asn1editor.model.ASN1Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Rekursiver Abstiegs-Parser (Recursive Descent) für ASN.1-Dokumente.
 *
 * <p>Der Parser verwendet die Tokenliste des {@link ASN1Lexer} und erzeugt
 * einen {@link ASN1Node}-Baum, der ein gesamtes ASN.1-Dokument repräsentiert.
 *
 * <p>Grammatik (vereinfacht, aber praxistauglich):
 * <pre>
 *   document       ::= definition*
 *   definition     ::= name ::= typeContent
 *                    | name ::= valueContent
 *
 *   typeContent    ::= "CHOICE" "{" choiceItem ( "|" choiceItem )* "}"
 *                    | "SEQUENCE" "{" fieldDef ( "," fieldDef )* "}"
 *                    | "SET" "{" fieldDef ( "," fieldDef )* "}"
 *                    | "SEQUENCE OF" type
 *                    | "SET OF" type
 *                    | typeRef
 *                    | "stringLiteral"
 *
 *   choiceItem     ::= name (typeContent | name)
 *
 *   fieldDef       ::= name typeContent
 *
 *   valueContent   ::= "stringLiteral"
 *                    | "{" valueEntry+ "}"
 *
 *   valueEntry     ::= name valueContent
 *
 *   subContent     ::= "stringLiteral"
 *                    | "{" subEntry+ "}"
 *
 *   subEntry       ::= name subContent
 * </pre>
 *
 * <p>Design-Entscheidungen:
 * <ul>
 *   <li>Knoten erhalten einen {@code kind}-Tag, der den ASN.1-Kontext beschreibt
 *       (TYPE_DEF, VALUE_DEF, CHOICE, SEQUENCE, SET, FIELD, CHOICE_ITEM, DATA, BLOCK)</li>
 *   <li>Typ- und Wert-Definitionen werden einheitlich als Baumstrukturen modelliert,
 *       damit die GUI beide gleich behandeln kann.</li>
 *   <li>Falsche Tokens lösen eine {@link ASN1ParseException} aus.</li>
 * </ul>
 */
public class ASN1Parser {

    /**
     * Parse-Modi: Unterscheidung zwischen Typ- und Wert-Kontext.
     * In Typ-Kontext ist ein Bezeichner ein Typ-Ref oder ein Schlüsselwort.
     * In Wert-Kontext ist ein Bezeichner ein Feldname.
     */
    public enum ParseMode { TYPE, VALUE }

    private final List<Token> tokens;
    private int pos = 0;

    private static final List<String> TYPE_KEYWORDS = List.of("CHOICE", "SEQUENCE", "SET");

    /**
     * Erstellt einen neuen Parser mit der gegebenen Tokenliste.
     *
     * @param tokens geparste Tokenliste vom Lexer
     */
    public ASN1Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // ─── Öffentliche API ────────────────────────────────────────────

    /**
     * Parsed die gesamte Tokenliste und liefert den Dokumenten-Wurzelknoten.
     *
     * @return ASN1Node mit allen Definitionen als Kinder
     * @throws ASN1ParseException wenn ein Syntaxfehler vorliegt
     */
    public ASN1Node parse() throws ASN1ParseException {
        List<ASN1Node> definitions = new ArrayList<>();

        while (!atEnd()) {
            definitions.add(parseDefinition());
        }

        if (definitions.isEmpty()) {
            throw new ASN1ParseException(
                    "Dokument enthält keine Definitionen.", 1, 1);
        }

        // Wenn genau ein Root-Knoten existiert, direkt zurückgeben.
        // Ansonsten wird ein Container-Knoten erstellt.
        if (definitions.size() == 1) {
            return definitions.get(0);
        }

        return ASN1Node.internal("ASN.1 Document", definitions);
    }

    // ─── Hilfsmethoden ──────────────────────────────────────────────

    private boolean atEnd() {
        return peek().type() == Token.TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token current() {
        return tokens.get(pos);
    }

    /**
     * Liest das aktuelle Token und bewegt den Zeiger um eine Stelle.
     */
    private Token advance() {
        Token t = tokens.get(pos);
        pos++;
        return t;
    }

    private Token expect(Token.TokenType type) throws ASN1ParseException {
        Token t = current();
        if (t.type() != type) {
            throw new ASN1ParseException(
                    "Erwartet '" + type + "', gefunden '" + t.type() + "'.",
                    t.line(), t.column());
        }
        Token ret = t;
        pos++;
        return ret;
    }

    private boolean expectOptional(Token.TokenType type) {
        if (current().type() == type) {
            pos++;
            return true;
        }
        return false;
    }

    private boolean isTypeKeyword(Token t) {
        return TYPE_KEYWORDS.contains(t.value().toUpperCase());
    }

    // ─── Definition ─────────────────────────────────────────────────

    /**
     * definition ::= name ::= typeContent
     *              | name ::= valueContent
     *
     * Ein Block "{" markiert einen Wert, andernfalls wird der Rest
     * als Typ gedeutet (Schlüsselwort oder Typ-Ref + Optionen).
     */
    private ASN1Node parseDefinition() throws ASN1ParseException {
        Token name = expect(Token.TokenType.IDENTIFIER);
        Token eq = current();
        if (eq.type() != Token.TokenType.DOUBLE_EQUALS && eq.type() != Token.TokenType.EQUALS) {
            throw new ASN1ParseException(
                    "Erwartet '::=' oder ':=', gefunden '" + eq.type() + "'.",
                    eq.line(), eq.column());
        }
        advance();

        // Wenn der nächste Token ein Block ist → Wert-Definition
        if (current().type() == Token.TokenType.LBRACE) {
            return parseValueDefinition(name.value());
        }

        // Ansonsten Typ-Definition
        return parseTypeDefinition(name.value());
    }

    // ─── Typ-Definition ─────────────────────────────────────────────

    private ASN1Node parseTypeDefinition(String typeName) throws ASN1ParseException {
        Token typeKeyword = current();

        if (isTypeKeyword(typeKeyword)) {
            // Schlüsselwort: CHOICE, SEQUENCE, SET
            String keyword = advance().value().toUpperCase();
            ASN1Node typeNode = ASN1Node.leaf("kind", keyword);

            // CHOICE, SEQUENCE und SET erwarten einen Block
            expect(Token.TokenType.LBRACE);
            ASN1Node body = parseTypeBody(keyword);
            expect(Token.TokenType.RBRACE);

            return ASN1Node.internal("type:" + typeName, List.of(typeNode, body));

        } else if (current().type() == Token.TokenType.STRING_LITERAL) {
            // Typ ist ein einfacher String: MyField ::= "default"
            Token strVal = advance();
            ASN1Node typeNode = ASN1Node.leaf("kind", "STRING_LITERAL");
            ASN1Node valueNode = ASN1Node.leaf("value", strVal.value());
            return ASN1Node.internal("type:" + typeName, List.of(typeNode, valueNode));

        } else if (current().type() == Token.TokenType.IDENTIFIER) {
            // Typ-Ref: MyField ::= OtherType
            // Prüfen auf "OF" → SEQUENCE OF / SET OF
            Token typeRef = current();
            advance(); // typeRef konsumieren

            if (peek().type() == Token.TokenType.IDENTIFIER
                    && "OF".equalsIgnoreCase(peek().value())) {
                // SEQUENCE OF / SET OF type
                advance(); // "OF" konsumieren
                Token elemType = current();
                if (elemType.type() == Token.TokenType.IDENTIFIER) {
                    advance();
                    return ASN1Node.internal(
                            "type:" + typeName,
                            List.of(
                                    ASN1Node.leaf("kind", typeRef.value() + " OF"),
                                    ASN1Node.leaf("elementType", elemType.value())));
                } else if (elemType.type() == Token.TokenType.LBRACE) {
                    ASN1Node elemBlock = parseTypeBody("BLOCK");
                    expect(Token.TokenType.RBRACE);
                    return ASN1Node.internal(
                            "type:" + typeName,
                            List.of(
                                    ASN1Node.leaf("kind", typeRef.value() + " OF"),
                                    elemBlock));
                } else {
                    throw new ASN1ParseException(
                            "Erwartet Element-Typ nach 'OF'.",
                            elemType.line(), elemType.column());
                }
            } else {
                // Einfacher Typ-Verweis: ::= TypeName
                return ASN1Node.internal(
                        "type:" + typeName,
                        List.of(
                                ASN1Node.leaf("kind", "TYPE_REF"),
                                ASN1Node.leaf("typeRef", typeRef.value())));
            }
        } else {
            throw new ASN1ParseException(
                    "Unerwartetes Token '" + current().type() + "' an Stelle von Typ.",
                    current().line(), current().column());
        }
    }

    /**
     * Parst den Block-Inhalt eines Typs.
     */
    private ASN1Node parseTypeBody(String keyword) throws ASN1ParseException {
        List<ASN1Node> children;

        switch (keyword) {
            case "CHOICE" -> children = parseChoiceBody();
            case "SEQUENCE", "SET" -> children = parseSequenceBody();
            default -> children = parseBlockContent();
        }

        return ASN1Node.internal(keyword.toLowerCase(), children);
    }

    /**
     * choiceItem ::= name (typeContent | name)
     *                ( "|" name (typeContent | name) )*
     */
    private List<ASN1Node> parseChoiceBody() throws ASN1ParseException {
        List<ASN1Node> items = new ArrayList<>();
        items.add(parseChoiceItem());

        while (expectOptional(Token.TokenType.PIPE)) {
            items.add(parseChoiceItem());
        }

        return items;
    }

    private ASN1Node parseChoiceItem() throws ASN1ParseException {
        Token itemName = expect(Token.TokenType.IDENTIFIER);

        // Nach dem Item-Namen kann ein Typ folgen
        if (isTypeKeyword(current())) {
            ASN1Node typeNode = parseTypeDefinition(itemName.value());
            return ASN1Node.internal("choice:" + itemName.value(), List.of(typeNode));

        } else if (current().type() == Token.TokenType.IDENTIFIER) {
            // Verweis auf einen anderen Typ
            Token typeRef = advance();
            return ASN1Node.internal(
                    "choice:" + itemName.value(),
                    List.of(ASN1Node.leaf("typeRef", typeRef.value())));

        } else if (current().type() == Token.TokenType.STRING_LITERAL) {
            Token strVal = advance();
            ASN1Node typeNode = ASN1Node.leaf("kind", "STRING_LITERAL");
            ASN1Node valueNode = ASN1Node.leaf("value", strVal.value());
            return ASN1Node.internal("choice:" + itemName.value(), List.of(typeNode, valueNode));

        } else {
            // Kein Typ angegeben → einfaches Choice-Item
            return ASN1Node.internal("choice:" + itemName.value(), List.of());
        }
    }

    /**
     * fieldDef ::= name typeContent
     *              ( "," name typeContent )*
     *
     * <p>Akzeptiert auch Newline/Trennzeichen als Feldtrenner,
     * da ASN.1 oft Whitespace statt Kommas nutzt.
     */
    private List<ASN1Node> parseSequenceBody() throws ASN1ParseException {
        List<ASN1Node> fields = new ArrayList<>();
        fields.add(parseFieldDef());

        // Komma oder neues Feld (bei implizitem Separator)
        while (current().type() == Token.TokenType.COMMA || isFieldStart()) {
            if (current().type() == Token.TokenType.COMMA) {
                advance(); // Komma konsumieren
            }
            fields.add(parseFieldDef());
        }

        return fields;
    }

    /**
     * Prüft, ob ein neuer Feldname folgt (nicht RBRACE, kein Operator, kein Schlüsselwort).
     */
    private boolean isFieldStart() {
        return !atEnd()
                && current().type() == Token.TokenType.IDENTIFIER
                && !isTypeKeyword(current());
    }

    private ASN1Node parseFieldDef() throws ASN1ParseException {
        Token fieldName = expect(Token.TokenType.IDENTIFIER);

        if (current().type() == Token.TokenType.STRING_LITERAL) {
            // Feld hat einen String-Wert als Default
            Token strVal = advance();
            ASN1Node typeNode = ASN1Node.leaf("kind", "STRING_LITERAL");
            ASN1Node valueNode = ASN1Node.leaf("value", strVal.value());
            return ASN1Node.internal("field:" + fieldName.value(), List.of(typeNode, valueNode));

        } else if (current().type() == Token.TokenType.IDENTIFIER
                && isTypeKeyword(current())) {
            // Feld mit Typ-Schlüsselwort: field SEQUENCE { ... }
            ASN1Node typeNode = parseInlineTypeKeyword();
            return ASN1Node.internal("field:" + fieldName.value(), List.of(typeNode));

        } else if (current().type() == Token.TokenType.IDENTIFIER) {
            // Einfacher Typ-Ref: field INTEGER
            ASN1Node typeNode = parseInlineType();
            return ASN1Node.internal("field:" + fieldName.value(), List.of(typeNode));

        } else if (isTypeKeyword(current())) {
            // Typ-Schlüsselwort gefolgt von Block: field SEQUENCE { ... }
            ASN1Node typeNode = parseInlineTypeKeyword();
            return ASN1Node.internal("field:" + fieldName.value(), List.of(typeNode));

        } else {
            throw new ASN1ParseException(
                    "Erwartet Typ nach Feldname '" + fieldName.value() + "'.",
                    fieldName.line(), fieldName.column());
        }
    }

    /**
     * Parst einen inline Typ-Schlüsselwort (SEQUENCE, SET, CHOICE) mit optionaler Block-Inhalt.
     */
    private ASN1Node parseInlineTypeKeyword() throws ASN1ParseException {
        Token keyword = advance();
        String kw = keyword.value().toUpperCase();

        if (current().type() == Token.TokenType.LBRACE) {
            expect(Token.TokenType.LBRACE);
            ASN1Node body = parseTypeBody(kw);
            expect(Token.TokenType.RBRACE);
            return ASN1Node.internal(kw.toLowerCase(), List.of(ASN1Node.leaf("kind", kw), body));
        }

        return ASN1Node.leaf("kind", kw);
    }

    /**
     * Parst einen inline-Typ: TypRef oder "SEQUENCE OF" / "SET OF" TypRef.
     */
    private ASN1Node parseInlineType() throws ASN1ParseException {
        Token typeRef = current();

        if (typeRef.type() == Token.TokenType.IDENTIFIER
                && peek().type() == Token.TokenType.IDENTIFIER
                && "OF".equalsIgnoreCase(peek().value())) {
            advance(); // typeRef
            advance(); // OF
            Token elemType = advance();
            return ASN1Node.internal(
                    typeRef.value() + " OF",
                    List.of(ASN1Node.leaf("elementType", elemType.value())));
        }

        advance();
        return ASN1Node.leaf("typeRef", typeRef.value());
    }

    /**
     * Parst einen allgemeinen Block-Inhalt (für BLOCK-Typen).
     */
    private List<ASN1Node> parseBlockContent() throws ASN1ParseException {
        List<ASN1Node> entries = new ArrayList<>();

        while (!atEnd() && current().type() != Token.TokenType.RBRACE) {
            entries.add(parseBlockEntry());
        }

        return entries;
    }

    private ASN1Node parseBlockEntry() throws ASN1ParseException {
        Token name = expect(Token.TokenType.IDENTIFIER);

        ASN1Node content = parseBlockValue();

        return ASN1Node.internal("field:" + name.value(), List.of(content));
    }

    private ASN1Node parseBlockValue() throws ASN1ParseException {
        if (current().type() == Token.TokenType.STRING_LITERAL) {
            Token str = advance();
            return ASN1Node.leaf("data", str.value());
        }

        if (current().type() == Token.TokenType.LBRACE) {
            List<ASN1Node> entries = parseBlockContent();
            expect(Token.TokenType.RBRACE);
            return ASN1Node.internal("block", entries);
        }

        // Wenn es ein Identifikator ist → Typ-Verweis
        if (current().type() == Token.TokenType.IDENTIFIER) {
            Token ref = advance();
            return ASN1Node.leaf("ref", ref.value());
        }

        throw new ASN1ParseException(
                "Unerwartetes Token '" + current().type() + "' in Block-Wert.",
                current().line(), current().column());
    }

    // ─── Wert-Definition ────────────────────────────────────────────

    /**
     * value ::= name "{" valueEntry+ "}"
     */
    private ASN1Node parseValueDefinition(String name) throws ASN1ParseException {
        expect(Token.TokenType.LBRACE);

        List<ASN1Node> entries = parseValueEntries();

        expect(Token.TokenType.RBRACE);

        return ASN1Node.internal("value:" + name, entries);
    }

    /**
     * valueEntry ::= name valueContent
     *                ( name valueContent )*
     */
    private List<ASN1Node> parseValueEntries() throws ASN1ParseException {
        List<ASN1Node> entries = new ArrayList<>();

        while (!atEnd() && current().type() != Token.TokenType.RBRACE) {
            entries.add(parseValueEntry());
        }

        return entries;
    }

    private ASN1Node parseValueEntry() throws ASN1ParseException {
        Token name = expect(Token.TokenType.IDENTIFIER);
        ASN1Node content = parseValueContent();
        return ASN1Node.internal("field:" + name.value(), List.of(content));
    }

    /**
     * valueContent ::= "stringLiteral"
     *                | "{" valueEntry+ "}"
     */
    private ASN1Node parseValueContent() throws ASN1ParseException {
        if (current().type() == Token.TokenType.STRING_LITERAL) {
            Token str = advance();
            return ASN1Node.leaf("data", str.value());
        }

        if (current().type() == Token.TokenType.LBRACE) {
            advance(); // consume {
            List<ASN1Node> entries = parseNestedBlockEntries();
            expect(Token.TokenType.RBRACE);
            return ASN1Node.internal("block", entries);
        }

        throw new ASN1ParseException(
                "Unerwartetes Token '" + current().type() + "' in Wert-Kontext.",
                current().line(), current().column());
    }

    /**
     * Parst verschachtelte Block-Einträge (subEntry).
     */
    private List<ASN1Node> parseNestedBlockEntries() throws ASN1ParseException {
        List<ASN1Node> entries = new ArrayList<>();

        while (!atEnd() && current().type() != Token.TokenType.RBRACE) {
            entries.add(parseNestedEntry());
        }

        return entries;
    }

    private ASN1Node parseNestedEntry() throws ASN1ParseException {
        Token name = expect(Token.TokenType.IDENTIFIER);
        ASN1Node content = parseNestedContent();
        return ASN1Node.internal("field:" + name.value(), List.of(content));
    }

    /**
     * subContent ::= "stringLiteral" | "{" subEntry+ "}"
     */
    private ASN1Node parseNestedContent() throws ASN1ParseException {
        if (current().type() == Token.TokenType.STRING_LITERAL) {
            Token str = advance();
            return ASN1Node.leaf("data", str.value());
        }

        if (current().type() == Token.TokenType.LBRACE) {
            List<ASN1Node> entries = parseNestedBlockEntries();
            expect(Token.TokenType.RBRACE);
            return ASN1Node.internal("block", entries);
        }

        // Unbekanntes Token → Error
        throw new ASN1ParseException(
                "Unerwartetes Token '" + current().type() + "' in verschachteltem Inhalt.",
                current().line(), current().column());
    }
}
