package com.asn1editor.parser;

/**
 * Beschreibt ein ASN.1-Tag aus einem TLV-Element.
 *
 * <p>Wird vom {@link ASN1BerDecoder} und {@link ASN1BerEncoder} gemeinsam genutzt,
 * um Duplikate zu vermeiden.
 *
 * @param tagClass     0=UNIVERSAL, 1=APPLICATION, 2=CONTEXT-SPECIFIC, 3=PRIVATE
 * @param tagNumber    die Tag-Number
 * @param constructed  true wenn konstruktiv (P-Flag gesetzt)
 * @param name         der ASN.1-Typname (z.B. "SEQUENCE", "INTEGER")
 */
public record TagInfo(int tagClass, int tagNumber, boolean constructed, String name) {

    /**
     * Gibt den Anzeigenamen des Tags zurück.
     * Für konstruktive Typen wird "[CONSTRUCTED]" angehängt.
     */
    public String getDisplayName() {
        return name + (constructed ? " [CONSTRUCTED]" : "");
    }
}
