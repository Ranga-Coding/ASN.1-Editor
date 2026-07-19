package com.asn1editor.ui;

import com.asn1editor.model.ASN1Node;

/**
 * Wrapper f�r TreeItem-Werte, der Anzeigetext und den zugrunde liegenden ASN1Node speichert.
 *
 * <p>JavaFX TreeItem hat kein UserData-Feld, daher wird dieser Wrapper verwendet,
 * um Metadaten mit dem angezeigten Text zu verbinden.
 *
 * @param displayText Der im TreeView angezeigte Text.
 * @param node Der zugrunde liegende ASN1Node (kann null sein).
 */
public record TreeItemContent(String displayText, ASN1Node node) {

    @Override
    public String toString() {
        return displayText;
    }
}
