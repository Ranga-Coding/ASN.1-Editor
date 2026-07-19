package com.asn1editor.service;

/**
 * Exception für Fehler beim Laden oder Speichern von ASN.1-Dateien.
 *
 * <p>Kann sowohl Datei-I/O-Fehler als auch Parser-Fehler umfassen.
 * Bei binären Dateien (DER/BER) enthält sie auch den Hex-String zur Anzeige.
 */
public class ASN1IOException extends Exception {

    private final String filePath;
    private final String hexContent;

    /**
     * Erstellt eine neue ASN1IOException.
     *
     * @param message Die Fehlermeldung.
     * @param filePath Der Pfad der betroffenen Datei (kann null sein).
     * @param cause Der auslösende Exception (kann null sein).
     */
    public ASN1IOException(String message, String filePath, Throwable cause) {
        this(message, filePath, null, cause);
    }

    /**
     * Erstellt eine ASN1IOException mit Hex-Inhalt (für binäre Dateien).
     *
     * @param message Die Fehlermeldung.
     * @param filePath Der Pfad der betroffenen Datei (kann null sein).
     * @param hexContent Hex-Darstellung der Binärdaten (kann null sein).
     * @param cause Der auslösende Exception (kann null sein).
     */
    public ASN1IOException(String message, String filePath, String hexContent, Throwable cause) {
        super(message, cause);
        this.filePath = filePath;
        this.hexContent = hexContent;
    }

    /**
     * Erstellt eine ASN1IOException ohne Ursache.
     */
    public ASN1IOException(String message, String filePath) {
        this(message, filePath, null, null);
    }

    /**
     * Erstellt eine ASN1IOException ohne Ursache mit Hex-Inhalt.
     */
    public ASN1IOException(String message, String filePath, String hexContent) {
        this(message, filePath, hexContent, null);
    }

    /**
     * Gibt den Pfad der betroffenen Datei zurück.
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Gibt den Hex-Inhalt zurück (nur bei binären Dateien, sonst null).
     */
    public String getHexContent() {
        return hexContent;
    }

    /**
     * Prüft, ob diese Exception einen Hex-Inhalt enthält.
     */
    public boolean hasHexContent() {
        return hexContent != null && !hexContent.isEmpty();
    }
}
