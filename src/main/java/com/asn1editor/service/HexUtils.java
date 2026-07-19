package com.asn1editor.service;

/**
 * Utility-Klasse für Hex-String-Konvertierungen.
 *
 * <p>Bündelt alle Byte-Array ↔ Hex-String Operationen an einem Ort,
 * um Duplikate im Code zu vermeiden.
 */
public final class HexUtils {

    private HexUtils() {
        // Nicht instanziiert
    }

    /**
     * Konvertiert ein Byte-Array in einen hexadezimalen String mit Leerzeichen.
     *
     * <p>Beispiel: {@code [0x30, 0x80, 0x06]} → {@code "30 80 06"}
     *
     * @param bytes das zu konvertierende Byte-Array
     * @return hexadezimaler String mit Leerzeichen getrennt
     */
    public static String toHexString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Konvertiert ein Byte-Array in einen kompakten hexadezimalen String.
     *
     * <p>Beispiel: {@code [0x30, 0x80, 0x06]} → {@code "308006"}
     *
     * @param bytes das zu konvertierende Byte-Array
     * @return kompakter hexadezimaler String
     */
    public static String toCompactHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
}
