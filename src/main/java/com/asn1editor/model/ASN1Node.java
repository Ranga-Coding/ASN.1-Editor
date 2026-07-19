package com.asn1editor.model;

import java.util.List;

/**
 * Repräsentation eines Elements im ASN.1 Baum.
 *
 * <p>Für BER/DER-dekodierte Knoten enthalten {@code offset} und {@code length}
 * die Position im Binärstrom (TLV-Byte-Range). Für Text-ASN.1 bleiben beide -1 bzw. 0.
 *
 * @param name     Der Name des Knotens.
 * @param value    Der Wert des Knotens (falls vorhanden).
 * @param children Mögliche Unterknoten.
 * @param isLeaf   True, wenn es sich um ein Blatt handelt.
 * @param offset   Start-Byte im Binärstrom (-1 für Text-ASN.1).
 * @param length   Byte-Länge im Binärstrom (0 für Text-ASN.1).
 */
public record ASN1Node(String name, String value, List<ASN1Node> children, boolean isLeaf,
                       int offset, int length) {

    public ASN1Node {
        if (name == null) {
            throw new IllegalArgumentException("Name darf nicht null sein.");
        }
    }

    /**
     * Erstellt einen neuen Blattknoten.
     */
    public static ASN1Node leaf(String name, String value) {
        return new ASN1Node(name, value, List.of(), true, -1, 0);
    }

    /**
     * Erstellt einen neuen internen Knoten.
     */
    public static ASN1Node internal(String name, List<ASN1Node> children) {
        return new ASN1Node(name, null, List.copyOf(children), false, -1, 0);
    }

    /**
     * Erstellt einen neuen internen Knoten mit Byte-Offset und -Länge (für BER/DER).
     *
     * @param name     Der Name des Knotens.
     * @param children Die Unterknoten.
     * @param offset   Start-Byte im Binärstrom.
     * @param length   Byte-Länge des TLV-Elements.
     */
    public static ASN1Node internal(String name, List<ASN1Node> children, int offset, int length) {
        return new ASN1Node(name, null, List.copyOf(children), false, offset, length);
    }
}
