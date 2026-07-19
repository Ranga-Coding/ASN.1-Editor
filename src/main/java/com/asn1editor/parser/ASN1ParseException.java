package com.asn1editor.parser;

/**
 * Eine spezialisierte Exception f�r Syntaxfehler im ASN.1 Kontext.
 * Speichert die Position (Zeile/Spalte) um pr�zise Fehlermeldungen zu erm�glichen.
 */
public class ASN1ParseException extends Exception {
    private final int line;
    private final int column;

    public ASN1ParseException(String message, int line, int column) {
        super(String.format("Syntaxfehler in Zeile %d, Spalte %d: %s", line, column, message));
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
