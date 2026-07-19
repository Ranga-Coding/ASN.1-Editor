package com.asn1editor.model;

import java.util.List;

/**
 * Repräsentation eines gesamten ASN.1-Dokuments.
 *
 * <p>Ein ASN.1-Dokument kann mehrere top-level Definitionen enthalten.
 * {@link #root()} gibt den ersten Root-Knoten zurück (Konvenience-Methode).
 *
 * @param roots Liste der Root-Knoten des Dokuments.
 */
public record ASN1Document(List<ASN1Node> roots) {

    public ASN1Document {
        if (roots == null || roots.isEmpty()) {
            throw new IllegalArgumentException("Roots darf nicht null oder leer sein.");
        }
        roots = List.copyOf(roots); // unveränderliche Kopie
    }

    /**
     * Erstellt ein Dokument mit einem einzelnen Root-Knoten.
     * Explizite Null-Prüfung, da List.of(null) NullPointerException werfen würde.
     */
    public ASN1Document(ASN1Node root) {
        this(validateRoot(root));
    }

    private static List<ASN1Node> validateRoot(ASN1Node root) {
        if (root == null) {
            throw new IllegalArgumentException("Root darf nicht null sein.");
        }
        return List.of(root);
    }

    /**
     * Gibt den ersten Root-Knoten zurück (Konvenience für Dokumente mit einer Definition).
     */
    public ASN1Node root() {
        return roots.get(0);
    }

    /**
     * Gibt alle Definitionen des Dokuments zurück.
     */
    public List<ASN1Node> definitions() {
        return roots;
    }
}
